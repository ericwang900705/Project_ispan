package gameplatform.admin.dto;

import gameplatform.admin.entity.AdminPermission;

/**
 * 管理員功能權限回應 DTO
 *
 * 用途：
 * 將 admin_permissions 資料表中的權限資料
 * 轉換成前端需要的 JSON 格式。
 *
 * 使用 record 可以自動產生建構子與 getter 類似功能，
 * 因此不需要額外使用 Lombok。
 */
public record PermissionResponse(

        // 權限 ID
        Integer permissionId,

        // 權限代碼，例如 GAME_REVIEW
        String permissionCode,

        // 權限名稱，例如「遊戲審核」
        String permissionName,

        // 權限說明
        String description

) {

    /**
     * 將 Entity 轉換成 DTO。
     *
     * @param permission 從資料庫取得的權限 Entity
     * @return 給前端使用的 PermissionResponse
     */
    public static PermissionResponse from(AdminPermission permission) {

        return new PermissionResponse(
                permission.getPermissionId(),
                permission.getPermissionCode(),
                permission.getPermissionName(),
                permission.getDescription());
    }
}