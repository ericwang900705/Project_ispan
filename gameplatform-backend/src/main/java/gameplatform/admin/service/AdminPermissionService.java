
package gameplatform.admin.service;

import gameplatform.admin.entity.*;
import gameplatform.admin.repository.*;
import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Adapted from game/admin 60788f4: JPA permissions, ADMIN highest, STAFF
 * individual grants.
 */
@Service
@Transactional(readOnly = true)
public class AdminPermissionService {
    public static final Set<String> BUSINESS_CODES = Set.of("DASHBOARD_VIEW", "MEMBER_SUSPEND",
            "PUBLISHER_REVIEW", "ORDER_VIEW", "REFUND_MANAGE", "GAME_REVIEW", "TAG_MANAGE",
            "TICKET_HANDLE", "COMMENT_REVIEW", "PROMOTION_MANAGE", "COUPON_MANAGE",
            "OPERATION_LOG_VIEW", "LOGIN_LOG_VIEW");
    public static final Set<String> RESERVED_CODES = Set.of("STAFF_MANAGE", "PERMISSION_MANAGE");
    public static final Set<String> ALL_CODES = java.util.stream.Stream.concat(
            BUSINESS_CODES.stream(), RESERVED_CODES.stream()).collect(Collectors.toUnmodifiableSet());
    private final AdminRepository admins;
    private final AdminPermissionRepository permissions;
    private final AdminUserPermissionRepository grants;
    private final JdbcTemplate db;

    public AdminPermissionService(AdminRepository admins, AdminPermissionRepository permissions,
            AdminUserPermissionRepository grants, JdbcTemplate db) {
        this.admins = admins;
        this.permissions = permissions;
        this.grants = grants;
        this.db = db;
    }

    public static Set<String> fixedPermissions(String role) {
        return switch (role) {
            case "ADMIN" -> ALL_CODES;
            case "STAFF" -> Set.of(); // STAFF 權限由管理員個別授予
            case "REVIEWER" -> Set.of("GAME_REVIEW", "PUBLISHER_REVIEW", "MEMBER_SUSPEND", "COMMENT_REVIEW");
            case "SUPPORT" -> Set.of("TICKET_HANDLE");
            default -> Set.of();
        };
    }

    private Optional<Admin> current(Actor actor) {
        if (actor == null || !actor.isStaff())
            return Optional.empty();
        return admins.findById(actor.id()).filter(a -> "ACTIVE".equals(a.getStatus())
                && Objects.equals(actor.username(), a.getAdminAccount())
                && a.getAdminRole() != null && Objects.equals(actor.role(), a.getAdminRole().getRoleCode()));
    }

    public boolean isHighestAdmin(Actor actor) {
        return actor != null && actor.isHighestAdmin() && current(actor).isPresent();
    }

    public void requireHighestAdmin(Actor actor) {
        if (!isHighestAdmin(actor))
            throw new Problem(403, "只有啟用中的 ADMIN 可以管理人員與權限");
    }

    public List<String> forActor(Actor actor) {
        if (current(actor).isEmpty())
            return List.of();
        return effectivePermissions(actor.id(), actor.role()).stream().sorted().toList();
    }

    private Set<String> effectivePermissions(int id, String role) {
        if (!"STAFF".equals(role))
            return fixedPermissions(role);
        Map<Integer, String> codes = permissions.findAll().stream().collect(Collectors.toMap(
                AdminPermission::getPermissionId, AdminPermission::getPermissionCode));
        return grants.findByAdminId(id).stream().map(g -> codes.get(g.getPermissionId()))
                .filter(c -> c != null && BUSINESS_CODES.contains(c)).collect(Collectors.toSet());
    }

    public List<AdminPermission> listAvailablePermissions() {
        return permissions.findAll().stream().filter(p -> BUSINESS_CODES.contains(p.getPermissionCode()))
                .sorted(Comparator.comparing(AdminPermission::getPermissionCode)).toList();
    }

    public Set<String> getStaffPermissionCodes(int id) {
        Admin target = requireStaff(id);
        return effectivePermissions(id, target.getAdminRole().getRoleCode());
    }

    private Admin requireStaff(int id) {
        Admin target = admins.findById(id).orElseThrow(() -> new Problem(404, "找不到後台人員"));
        if (!"ACTIVE".equals(target.getStatus()) || !"STAFF".equals(target.getAdminRole().getRoleCode()))
            throw new Problem(400, "只能調整啟用中的 STAFF 個別權限");
        return target;
    }

    /**
     * Shared lock with StaffService: role changes and replacements cannot
     * interleave.
     */
    private void lock() {
        Integer result = db.queryForObject(
                "DECLARE @r INT; EXEC @r=sys.sp_getapplock @Resource='HERO_STAFF_ADMIN',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;SELECT @r",
                Integer.class);
        if (result == null || result < 0)
            throw new Problem(409, "人員管理忙碌，請稍後重試");
    }

    @Transactional
    public void resetAfterRoleChange(Actor operator, int id, String role) {
        lock();
        requireHighestAdmin(operator);
        grants.deleteAll(grants.findByAdminId(id));
        grants.flush();
        // 切換角色時清除舊授權，STAFF 不自動取得任何權限。
    }

    @Transactional
    public void replaceStaffPermissions(Actor operator, int staffId, Set<String> requested) {
        lock();
        requireHighestAdmin(operator);
        requireStaff(staffId);
        if (requested == null || requested.stream().anyMatch(c -> c == null || !BUSINESS_CODES.contains(c)))
            throw new Problem(400, "包含不支援或僅限 ADMIN 的權限代碼");
        Map<String, AdminPermission> available = permissions.findAll().stream().collect(Collectors.toMap(
                AdminPermission::getPermissionCode, p -> p));
        if (!available.keySet().containsAll(requested))
            throw new Problem(400, "權限代碼尚未初始化，請先執行 SQL");
        // Full replacement result, applied as a diff: retained grants keep their audit
        // provenance.
        var current = grants.findByAdminId(staffId);
        Set<Integer> desiredIds = requested.stream().map(c -> available.get(c).getPermissionId())
                .collect(Collectors.toSet());
        Set<Integer> currentIds = current.stream().map(AdminUserPermission::getPermissionId)
                .collect(Collectors.toSet());
        grants.deleteAll(current.stream().filter(g -> !desiredIds.contains(g.getPermissionId())).toList());
        grants.flush();
        grants.saveAll(
                requested.stream().filter(c -> !currentIds.contains(available.get(c).getPermissionId())).map(code -> {
                    AdminUserPermission g = new AdminUserPermission();
                    g.setAdminId(staffId);
                    g.setPermissionId(available.get(code).getPermissionId());
                    g.setGrantedByAdminId(operator.id());
                    return g;
                }).toList());
        grants.flush();
    }
}
