package gameplatform.game.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 遊戲審核請求 DTO。
 *
 * 用於：
 * 1. 核准遊戲上架
 * 2. 拒絕遊戲上架
 * 3. 強制下架遊戲
 *
 * 管理員 ID 由 JWT 取得，
 * 不需要前端自行提供 adminId。
 */
@Getter
@Setter
public class GameAuditRequest {

    /**
     * 管理員審核備註。
     */
    private String comment;
}