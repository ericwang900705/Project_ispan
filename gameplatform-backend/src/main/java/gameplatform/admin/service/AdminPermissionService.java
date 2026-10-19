package gameplatform.admin.service;

import gameplatform.admin.entity.Admin;
import gameplatform.admin.entity.AdminPermission;
import gameplatform.admin.entity.AdminUserPermission;
import gameplatform.admin.repository.AdminPermissionRepository;
import gameplatform.admin.repository.AdminRepository;
import gameplatform.admin.repository.AdminUserPermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理員個別功能權限服務。
 *
 * <p>
 * 權限名稱由 admin_permissions 預先定義，Super Admin 只能勾選或取消既有權限。
 * 本類別不修改登入、JWT 或既有審核流程；Controller 與 Spring Security 將在後續階段串接。
 */
@Service
@RequiredArgsConstructor // Lombok 自動產生 final 欄位的建構子，讓 Spring 注入 Repository
public class AdminPermissionService {

    private final AdminRepository adminRepository;
    private final AdminPermissionRepository permissionRepository;
    private final AdminUserPermissionRepository userPermissionRepository;

    /** 查詢可勾選的全部功能權限，供 Vue 3 顯示核取方塊。 */
    @Transactional(readOnly = true)
    public List<AdminPermission> listAvailablePermissions() {
        return permissionRepository.findAll();
    }

    /**
     * 查詢某位 STAFF 已取得的權限代碼。
     * 例如：["GAME_REVIEW", "TICKET_HANDLE"]。
     */
    @Transactional(readOnly = true)
    public Set<String> getStaffPermissionCodes(Integer staffAdminId) {
        requireStaff(staffAdminId);
        Map<Integer, String> codeById = permissionRepository.findAll().stream()
                .collect(Collectors.toMap(AdminPermission::getPermissionId,
                        AdminPermission::getPermissionCode));

        return userPermissionRepository.findByAdminId(staffAdminId).stream()
                .map(row -> codeById.get(row.getPermissionId()))
                .filter(code -> code != null)
                .collect(Collectors.toSet());
    }

    /**
     * 儲存某位 STAFF 的完整勾選結果（覆蓋原有權限）。
     *
     * <p>
     * 例如傳入 ["GAME_REVIEW"]，代表只保留遊戲審核權限；
     * 傳入空集合則撤銷全部權限。此方法必須由已驗證的 Super Admin API 呼叫。
     *
     * @param operatorAdminId 從已驗證登入身分取得的操作者 ID，不可直接相信前端自行填入的 ID
     * @param staffAdminId    被分配權限的 STAFF 管理員 ID
     * @param requestedCodes  前端勾選的權限代碼集合
     */
    @Transactional
    public void replaceStaffPermissions(Integer operatorAdminId,
            Integer staffAdminId,
            Set<String> requestedCodes) {
        requireSuperAdmin(operatorAdminId);
        requireStaff(staffAdminId);
        if (requestedCodes == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "權限清單不可為 null；撤銷全部權限請傳空集合");
        }
        if (requestedCodes.stream().anyMatch(code -> code == null || code.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "權限代碼不可為空白");
        }

        // 只允許系統資料庫中已存在的權限，不允許前端自行創造權限名稱。
        Map<String, AdminPermission> available = permissionRepository.findAll().stream()
                .collect(Collectors.toMap(AdminPermission::getPermissionCode, Function.identity()));
        Set<String> unknown = new HashSet<>(requestedCodes);
        unknown.removeAll(available.keySet());
        if (!unknown.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知的權限代碼：" + unknown);
        }

        // 先刪除原本勾選，再新增這次的勾選；同一個交易內完成，失敗會回滾。
        // flush 確保 DELETE 先送往資料庫，避免與複合主鍵 INSERT 衝突。
        userPermissionRepository.deleteAll(userPermissionRepository.findByAdminId(staffAdminId));
        userPermissionRepository.flush();

        List<AdminUserPermission> newRows = requestedCodes.stream().map(code -> {
            AdminUserPermission row = new AdminUserPermission();
            row.setAdminId(staffAdminId);
            row.setPermissionId(available.get(code).getPermissionId());
            row.setGrantedByAdminId(operatorAdminId);
            // granted_at 由 SQL Server DEFAULT SYSDATETIME() 自動填入。
            return row;
        }).toList();
        userPermissionRepository.saveAll(newRows);
    }

    /** 確認操作者是啟用中的 ADMIN（Super Admin）。 */
    private void requireSuperAdmin(Integer operatorAdminId) {
        Admin operator = findAdmin(operatorAdminId);
        if (!"ACTIVE".equals(operator.getStatus())
                || !"ADMIN".equals(operator.getAdminRole().getRoleCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有 Super Admin 可以分配權限");
        }
    }

    /** 確認目標是啟用中的 STAFF，避免修改舊 REVIEWER / SUPPORT 或 ADMIN。 */
    private void requireStaff(Integer staffAdminId) {
        Admin staff = findAdmin(staffAdminId);
        if (!"ACTIVE".equals(staff.getStatus())
                || !"STAFF".equals(staff.getAdminRole().getRoleCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只能設定啟用中的 STAFF 管理員權限");
        }
    }

    /** 共用管理員查詢：不存在時回傳 404。 */
    private Admin findAdmin(Integer adminId) {
        if (adminId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "管理員 ID 不可為空");
        }
        return adminRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到管理員：" + adminId));
    }
}
