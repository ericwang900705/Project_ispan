package gameplatform.admin.dto;

import java.util.Set;

/**
 * 修改普通管理員權限的請求 DTO。
 *
 * 前端會傳入完整的權限代碼集合。
 *
 * 例如：
 * {
 * "permissionCodes": [
 * "GAME_REVIEW",
 * "TICKET_HANDLE"
 * ]
 * }
 *
 * 注意：
 * 這是「完整覆蓋」權限，不是單純新增權限。
 * 傳入空集合代表撤銷全部權限。
 */
public record ReplacePermissionsRequest(

        // 要分配給 STAFF 的功能權限代碼
        Set<String> permissionCodes

) {
}