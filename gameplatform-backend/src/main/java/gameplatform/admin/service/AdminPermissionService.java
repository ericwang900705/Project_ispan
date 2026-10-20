package gameplatform.admin.service;

import gameplatform.admin.entity.Admin;
import gameplatform.admin.entity.AdminPermission;
import gameplatform.admin.entity.AdminUserPermission;
import gameplatform.admin.repository.AdminPermissionRepository;
import gameplatform.admin.repository.AdminRepository;
import gameplatform.admin.repository.AdminUserPermissionRepository;
import gameplatform.support.model.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AdminPermissionService {

    // STAFF 可被分配的 13 項業務權限。
    public static final Set<String> BUSINESS_CODES = Set.of(
            "COMMENT_REVIEW", "COUPON_MANAGE", "DASHBOARD_VIEW", "GAME_REVIEW",
            "LOGIN_LOG_VIEW", "MEMBER_SUSPEND", "OPERATION_LOG_VIEW", "ORDER_VIEW",
            "PROMOTION_MANAGE", "PUBLISHER_REVIEW", "REFUND_MANAGE", "TAG_MANAGE",
            "TICKET_HANDLE"
    );

    // 只能由 ADMIN 操作，禁止分配給 STAFF。
    public static final Set<String> RESERVED_CODES = Set.of(
            "PERMISSION_MANAGE", "STAFF_MANAGE"
    );

    // 舊版 REVIEWER / SUPPORT 固定角色權限，對齊 SecurityConfig 現有規則。
    public static final Set<String> REVIEWER_CODES = Set.of(
            "MEMBER_SUSPEND", "PUBLISHER_REVIEW", "GAME_REVIEW", "COMMENT_REVIEW"
    );

    public static final Set<String> SUPPORT_CODES = Set.of(
            "TICKET_HANDLE"
    );

    public static final Set<String> ALL_CODES = Stream.concat(
            BUSINESS_CODES.stream(), RESERVED_CODES.stream()
    ).collect(Collectors.toUnmodifiableSet());

    private final JdbcTemplate db;
    private final AdminRepository adminRepository;
    private final AdminPermissionRepository permissionRepository;
    private final AdminUserPermissionRepository userPermissionRepository;

    /** 只列出 STAFF 可以被分配的權限，不顯示 ADMIN 專用項目。 */
    @Transactional(readOnly = true)
    public List<AdminPermission> listAvailablePermissions() {
        return permissionRepository.findAll().stream()
                .filter(permission -> BUSINESS_CODES.contains(permission.getPermissionCode()))
                .sorted(Comparator.comparing(AdminPermission::getPermissionCode))
                .toList();
    }

    /** 查詢啟用中 STAFF 的有效業務權限。 */
    @Transactional(readOnly = true)
    public Set<String> getStaffPermissionCodes(Integer staffAdminId) {
        requireStaff(staffAdminId);
        Map<Integer, String> codeById = permissionRepository.findAll().stream()
                .collect(Collectors.toMap(
                        AdminPermission::getPermissionId,
                        AdminPermission::getPermissionCode));

        return userPermissionRepository.findByAdminId(staffAdminId).stream()
                .map(row -> codeById.get(row.getPermissionId()))
                .filter(BUSINESS_CODES::contains)
                .collect(Collectors.toSet());
    }

    /**
     * ADMIN 覆蓋 STAFF 的權限勾選結果。
     * 保留未變動的授權紀錄，只刪除取消勾選的權限並新增新勾選的權限。
     */
    @Transactional
    public void replaceStaffPermissions(Integer operatorAdminId,
            Integer staffAdminId, Set<String> requestedCodes) {
        // 與角色切換共用交易鎖，避免同時修改。
        lockStaffChanges();
        requireSuperAdmin(operatorAdminId);
        requireStaff(staffAdminId);

        if (requestedCodes == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "權限清單不可為 null；撤銷全部權限請傳空集合");
        }
        if (requestedCodes.stream().anyMatch(code -> code == null || code.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "權限代碼不可為空白");
        }

        Set<String> forbidden = new HashSet<>(requestedCodes);
        forbidden.removeAll(BUSINESS_CODES);
        if (!forbidden.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "包含不支援或僅限 ADMIN 的權限代碼：" + forbidden);
        }

        Map<String, AdminPermission> available = permissionRepository.findAll().stream()
                .collect(Collectors.toMap(AdminPermission::getPermissionCode, Function.identity()));
        Set<String> notInitialized = new HashSet<>(requestedCodes);
        notInitialized.removeAll(available.keySet());
        if (!notInitialized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "權限代碼尚未初始化於資料庫：" + notInitialized);
        }

        List<AdminUserPermission> currentRows =
                userPermissionRepository.findByAdminId(staffAdminId);
        Set<Integer> desiredIds = requestedCodes.stream()
                .map(code -> available.get(code).getPermissionId())
                .collect(Collectors.toSet());
        Set<Integer> currentIds = currentRows.stream()
                .map(AdminUserPermission::getPermissionId)
                .collect(Collectors.toSet());

        List<AdminUserPermission> toDelete = currentRows.stream()
                .filter(row -> !desiredIds.contains(row.getPermissionId()))
                .toList();
        userPermissionRepository.deleteAll(toDelete);
        userPermissionRepository.flush();

        List<AdminUserPermission> toAdd = requestedCodes.stream()
                .filter(code -> !currentIds.contains(available.get(code).getPermissionId()))
                .map(code -> {
                    AdminUserPermission row = new AdminUserPermission();
                    row.setAdminId(staffAdminId);
                    row.setPermissionId(available.get(code).getPermissionId());
                    row.setGrantedByAdminId(operatorAdminId);
                    return row;
                }).toList();
        userPermissionRepository.saveAll(toAdd);
        userPermissionRepository.flush();
    }

    /** 取得目前登入管理員的有效權限，供 BackofficeService 驗證。 */
    @Transactional(readOnly = true)
    public Set<String> forActor(Actor actor) {
        if (actor == null || actor.isMember()) {
            return Set.of();
        }
        Admin admin = adminRepository.findById(actor.id()).orElse(null);
        if (admin == null || !"ACTIVE".equals(admin.getStatus())
                || admin.getAdminRole() == null) {
            return Set.of();
        }
        String dbRole = admin.getAdminRole().getRoleCode();
        // Actor 的角色必須與資料庫一致，避免舊憑證或角色變更後仍可存取。
        if (!dbRole.equals(actor.role())) {
            return Set.of();
        }
        if ("ADMIN".equals(dbRole)) {
            return ALL_CODES;
        }
        if ("STAFF".equals(dbRole)) {
            return getStaffPermissionCodes(actor.id());
        }
        if ("REVIEWER".equals(dbRole)) {
            return REVIEWER_CODES;
        }
        if ("SUPPORT".equals(dbRole)) {
            return SUPPORT_CODES;
        }
        return Set.of();
    }

    // 與 StaffService 使用相同的 SQL Server 交易鎖。
    private void lockStaffChanges() {
        Integer result = db.queryForObject(
                "DECLARE @r INT; EXEC @r=sys.sp_getapplock " +
                "@Resource='HERO_STAFF_ADMIN',@LockMode='Exclusive'," +
                "@LockOwner='Transaction',@LockTimeout=10000; SELECT @r",
                Integer.class);
        if (result == null || result < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "人員管理忙碌，請稍後重試");
        }
    }

    private void requireSuperAdmin(Integer operatorAdminId) {
        Admin operator = findAdmin(operatorAdminId);
        if (!"ACTIVE".equals(operator.getStatus())
                || operator.getAdminRole() == null
                || !"ADMIN".equals(operator.getAdminRole().getRoleCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "只有啟用中的 ADMIN 可以分配權限");
        }
    }

    private void requireStaff(Integer staffAdminId) {
        Admin staff = findAdmin(staffAdminId);
        if (!"ACTIVE".equals(staff.getStatus())
                || staff.getAdminRole() == null
                || !"STAFF".equals(staff.getAdminRole().getRoleCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "只能設定啟用中的 STAFF 管理員權限");
        }
    }

    private Admin findAdmin(Integer adminId) {
        if (adminId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "管理員 ID 不可為空");
        }
        return adminRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "找不到管理員：" + adminId));
    }
}
