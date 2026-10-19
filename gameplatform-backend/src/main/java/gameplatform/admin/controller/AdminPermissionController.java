package gameplatform.admin.controller;

import gameplatform.admin.dto.PermissionResponse;
import gameplatform.admin.dto.ReplacePermissionsRequest;
import gameplatform.admin.service.AdminPermissionService;
import gameplatform.support.model.Actor;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

/**
 * Super Admin 管理普通管理員權限的 Controller。
 *
 * API 前綴：/api/admin/permissions
 *
 * 主要功能：
 * 1. 查詢系統所有可分配權限
 * 2. 查詢 STAFF 已有權限
 * 3. 修改 STAFF 的功能權限
 *
 * 注意：
 * 此 Controller 只處理 HTTP 請求與回應。
 * 權限驗證與資料庫操作交給 Service。
 */
@RestController
@RequestMapping("/api/admin/permissions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPermissionController {

    // 使用 Lombok 自動建構子注入 Service
    private final AdminPermissionService permissionService;

    /**
     * 查詢所有可分配的功能權限。
     *
     * GET /api/admin/permissions
     *
     * 將 Entity 轉換為 DTO，
     * 避免直接將資料庫 Entity 回傳前端。
     */
    @GetMapping
    public List<PermissionResponse> listPermissions() {

        return permissionService.listAvailablePermissions()
                .stream()
                .map(PermissionResponse::from)
                .toList();
    }

    /**
     * 查詢指定 STAFF 已取得的權限。
     *
     * GET /api/admin/permissions/staff/{staffId}
     *
     * @param staffId 普通管理員 ID
     * @return 該管理員擁有的權限代碼
     */
    @GetMapping("/staff/{staffId}")
    public Set<String> getStaffPermissions(
            @PathVariable Integer staffId) {

        return permissionService.getStaffPermissionCodes(staffId);
    }

    /**
     * 修改 STAFF 的功能權限。
     *
     * PUT /api/admin/permissions/staff/{staffId}
     *
     * 使用完整覆蓋方式：
     * - 傳入權限代碼：保留指定權限
     * - 未傳入的舊權限：撤銷
     * - 傳入空集合：撤銷全部權限
     *
     * @param operator JWT 驗證後的管理員身分
     * @param staffId  要修改的普通管理員 ID
     * @param request  前端提交的權限資料
     * @return 修改後的權限集合
     */
    @PutMapping("/staff/{staffId}")
    public Set<String> replaceStaffPermissions(

            @AuthenticationPrincipal Actor operator,

            @PathVariable Integer staffId,

            @RequestBody ReplacePermissionsRequest request) {

        // 確認目前有通過驗證的登入身分
        if (operator == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "尚未登入管理員");
        }

        // 確認前端有提供權限清單
        if (request == null || request.permissionCodes() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "permissionCodes 必填；清空權限請傳 []");
        }

        // 由 Service 驗證操作者與 STAFF 身分，
        // 並將權限設定儲存至資料庫。
        permissionService.replaceStaffPermissions(
                operator.id(),
                staffId,
                request.permissionCodes());

        // 回傳更新後的權限清單
        return permissionService.getStaffPermissionCodes(staffId);
    }
}