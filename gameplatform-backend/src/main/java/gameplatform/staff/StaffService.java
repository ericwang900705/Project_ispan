package gameplatform.staff;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.constraints.*;
import gameplatform.support.model.*;
import gameplatform.support.config.StaffPermissionService;

@Service
public class StaffService {
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final StaffPermissionService access;
    private final gameplatform.admin.service.AdminPermissionService permissionService;
    public StaffService(JdbcTemplate db, PasswordEncoder passwords, StaffPermissionService access,
            gameplatform.admin.service.AdminPermissionService permissionService) {
        this.db=db;this.passwords=passwords;this.access=access;this.permissionService=permissionService;
    }
    public record Register(@NotBlank @Pattern(regexp="[A-Za-z0-9_]{4,50}") String username,
            @NotBlank @Size(max=50) String name, @NotBlank @Email @Size(max=100) String email,
            @NotBlank @Size(min=12,max=72) String password) {}

    private void lock() {
        Integer result = db.queryForObject("DECLARE @r INT; EXEC @r=sys.sp_getapplock @Resource='HERO_STAFF_ADMIN',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;SELECT @r", Integer.class);
        if (result == null || result < 0) throw new Problem(409,"人員管理忙碌，請稍後再試");
    }
    private boolean top(Actor actor) { return access.isHighestAdmin(actor); }
    private int role(String code) {
        Integer id = db.queryForObject("SELECT admin_role_id FROM dbo.admin_roles WHERE role_code=?",Integer.class,code);
        if (id == null) throw new Problem(500,"請先執行完整 SQL 的第 10 段");
        return id;
    }
    private void insert(Register r) {
        if (r.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) throw new Problem(400,"密碼不可超過 72 bytes");
        if (db.queryForObject("SELECT COUNT(*) FROM dbo.admin WHERE admin_account=? OR email=?",Integer.class,r.username(),r.email()) > 0)
            throw new Problem(409,"後台帳號或信箱已被使用");
        db.update("INSERT INTO dbo.admin(admin_role_id,admin_account,admin_name,email,password_hash,status) VALUES(?,?,?,?,?,'ACTIVE')",
                role("PENDING"),r.username(),r.name(),r.email(),passwords.encode(r.password()));
    }
    @Transactional public void register(Register r) { lock(); insert(r); }
    @Transactional public void create(Actor actor, Register r) {
        lock(); permissionService.requireHighestAdmin(actor); insert(r);
    }
    public List<Map<String,Object>> list(Actor actor) {
        permissionService.requireHighestAdmin(actor);
        var rows = db.queryForList("SELECT a.admin_id AS id,a.admin_account AS username,a.admin_name AS name,a.email,a.status,r.role_code AS role FROM dbo.admin a JOIN dbo.admin_roles r ON r.admin_role_id=a.admin_role_id ORDER BY a.admin_id");
        for (var row : rows) {
            String code = (String)row.get("role");
            var grants = code.equals("STAFF")
                ? ("ACTIVE".equals(row.get("status")) ? permissionService.getStaffPermissionCodes(((Number)row.get("id")).intValue()).stream().sorted().toList() : List.<String>of())
                : StaffPermissionService.options(code).stream().sorted().toList();
            row.put("permissions",grants);
        }
        return rows;
    }
    @Transactional public void change(Actor actor, int id, String code) {
        lock();
        if (!top(actor)) throw new Problem(403,"只有最高管理員可以指定人員角色與其他最高管理員");
        if (code == null || !Set.of("ADMIN","STAFF","REVIEWER","SUPPORT","PENDING").contains(code)) throw new Problem(400,"不支援的後台角色");
        var target = db.queryForList("SELECT r.role_code,a.status FROM dbo.admin a JOIN dbo.admin_roles r ON r.admin_role_id=a.admin_role_id WHERE a.admin_id=?",id);
        if (target.isEmpty()) throw new Problem(404,"找不到後台人員");
        String previous = (String)target.get(0).get("role_code");
        if (previous.equals(code)) return;
        // Existing highest administrators stay protected, including self.
        if ("ADMIN".equals(previous)) throw new Problem(409,"最高管理員帳號受保護，不能變更角色");
        if (Set.of("REVIEWER","SUPPORT").contains(previous)) throw new Problem(409,"審核與客服的角色及權限固定，不能變更角色或升為最高管理員");
        if ("ADMIN".equals(code) && !"STAFF".equals(previous)) throw new Problem(409,"只有 Staff 人員可以被指定為最高管理員");
        if ("ADMIN".equals(code) && !"ACTIVE".equals(target.get(0).get("status"))) throw new Problem(409,"只能指定啟用中的 Staff 人員為最高管理員");
        db.update("UPDATE dbo.admin SET admin_role_id=?,updated_at=SYSDATETIME() WHERE admin_id=?",role(code),id);
        // Preserve historical grants for fixed roles and highest administrators; they are ignored at runtime.
        // Returning to STAFF starts with one explicit safe default, never old hidden grants.
        if ("STAFF".equals(code) || "PENDING".equals(code)) permissionService.resetAfterRoleChange(actor,id,code);
    }
    /** Compatibility endpoint delegates to the single JPA permission service. */
    @Transactional public void permissions(Actor actor,int id,List<String> requested) {
        if(requested==null) throw new Problem(400,"權限清單不可為 null");
        permissionService.replaceStaffPermissions(actor,id,new HashSet<>(requested));
    }
}
