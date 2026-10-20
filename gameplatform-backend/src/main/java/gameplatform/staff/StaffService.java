package gameplatform.staff;

import gameplatform.admin.service.AdminPermissionService;
import gameplatform.support.model.Actor;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 管理員帳號管理：一般角色可互相切換，ADMIN 角色受保護。 */
@Service
public class StaffService {
    private static final Set<String> CHANGEABLE_ROLES = Set.of("STAFF", "REVIEWER", "SUPPORT");

    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final AdminPermissionService permissions;

    public StaffService(JdbcTemplate db, PasswordEncoder passwords, AdminPermissionService permissions) {
        this.db = db;
        this.passwords = passwords;
        this.permissions = permissions;
    }

    public record Register(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_]{4,50}") String username,
            @NotBlank @Size(max = 50) String name,
            @NotBlank @Email @Size(max = 100) String email,
            @NotBlank @Size(min = 12, max = 72) String password) {
    }

    // 使用交易鎖，避免同時修改角色及權限。
    private void lock() {
        Integer result = db.queryForObject(
                "DECLARE @r INT; EXEC @r=sys.sp_getapplock " +
                        "@Resource='HERO_STAFF_ADMIN',@LockMode='Exclusive'," +
                        "@LockOwner='Transaction',@LockTimeout=10000; SELECT @r",
                Integer.class);
        if (result == null || result < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "人員管理忙碌，請稍後重試");
        }
    }

    private void requireAdmin(Actor actor) {
        if (actor == null || !"ADMIN".equals(actor.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有最高管理員可以管理人員");
        }
        var matches = db.queryForList(
                "SELECT a.admin_account, a.status, r.role_code FROM dbo.admin a " +
                        "JOIN dbo.admin_roles r ON r.admin_role_id=a.admin_role_id WHERE a.admin_id=?",
                actor.id());
        if (matches.size() != 1 || !"ACTIVE".equals(matches.get(0).get("status"))
                || !"ADMIN".equals(matches.get(0).get("role_code"))
                || !actor.username().equals(matches.get(0).get("admin_account"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "管理員身分已失效，請重新登入");
        }
    }

    private int roleId(String code) {
        var ids = db.queryForList(
                "SELECT admin_role_id FROM dbo.admin_roles WHERE role_code=?", Integer.class, code);
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "資料庫尚未建立角色：" + code);
        }
        return ids.get(0);
    }

    private void insert(Register r) {
        if (r == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請填寫人員資料");
        }
        if (r.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密碼不可超過 72 bytes");
        }
        Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM dbo.admin WHERE admin_account=? OR email=?",
                Integer.class, r.username(), r.email());
        if (count != null && count > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "後台帳號或信箱已被使用");
        }
        db.update("INSERT INTO dbo.admin(admin_role_id,admin_account,admin_name,email,password_hash,status) " +
                "VALUES(?,?,?,?,?,'ACTIVE')",
                roleId("PENDING"), r.username(), r.name(), r.email(), passwords.encode(r.password()));
    }

    @Transactional
    public void create(Actor actor, Register r) {
        lock();
        requireAdmin(actor);
        insert(r);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Actor actor) {
        requireAdmin(actor);
        var rows = db.queryForList(
                "SELECT a.admin_id AS id,a.admin_account AS username,a.admin_name AS name," +
                        "a.email,a.status,r.role_code AS role FROM dbo.admin a " +
                        "JOIN dbo.admin_roles r ON r.admin_role_id=a.admin_role_id ORDER BY a.admin_id");
        for (var row : rows) {
            String role = (String) row.get("role");
            int id = ((Number) row.get("id")).intValue();
            Set<String> codes = switch (role) {
                case "ADMIN" -> AdminPermissionService.ALL_CODES;
                case "REVIEWER" -> AdminPermissionService.REVIEWER_CODES;
                case "SUPPORT" -> AdminPermissionService.SUPPORT_CODES;
                case "STAFF" -> "ACTIVE".equals(row.get("status"))
                        ? permissions.getStaffPermissionCodes(id)
                        : Set.of();
                default -> Set.of();
            };
            row.put("permissions", codes.stream().sorted().toList());
        }
        return rows;
    }

    /** 允許一般角色互換；首次授權 PENDING 時一併啟用帳號。 */
    @Transactional
    public void change(Actor actor, int id, String code) {
        lock();
        requireAdmin(actor);
        if (!CHANGEABLE_ROLES.contains(code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "只能切換 STAFF、REVIEWER、SUPPORT；ADMIN 與 PENDING 不在一般角色切換範圍");
        }
        var targets = db.queryForList(
                "SELECT r.role_code,a.status FROM dbo.admin a " +
                        "JOIN dbo.admin_roles r ON r.admin_role_id=a.admin_role_id WHERE a.admin_id=?",
                id);
        if (targets.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到後台人員");
        }
        String previous = (String) targets.get(0).get("role_code");
        if ("ADMIN".equals(previous)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "最高管理員帳號受保護，不可變更角色");
        }
        String status = (String) targets.get(0).get("status");
        if (!"ACTIVE".equals(status)
                && !("PENDING".equals(previous) && "PENDING".equals(status))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "停用中的管理員不可變更角色");
        }
        if (previous.equals(code))
            return;

        // 角色切換時清除舊權限；首次授權則啟用帳號。
        db.update("UPDATE dbo.admin SET admin_role_id=?,status='ACTIVE',updated_at=SYSDATETIME() WHERE admin_id=?",
                roleId(code), id);
        db.update("DELETE FROM dbo.admin_user_permissions WHERE admin_id=?", id);
        // STAFF 預設無權限，須由 ADMIN 另行授權。
    }

    @Transactional
    public void permissions(Actor actor, int id, List<String> requested) {
        lock();
        requireAdmin(actor);
        if (requested == null || requested.stream().anyMatch(c -> c == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "權限清單不可為 null，且不可包含 null");
        }
        permissions.replaceStaffPermissions(actor.id(), id, new HashSet<>(requested));
    }
}
