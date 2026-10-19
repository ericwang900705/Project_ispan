
package gameplatform.game.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import gameplatform.game.dto.GameAuditRequest;
import gameplatform.game.entity.Game;
import gameplatform.game.entity.GameAuditLog;
import gameplatform.game.service.GameReviewAdminService;
import gameplatform.support.model.Actor;
import lombok.RequiredArgsConstructor;

/**
 * 遊戲審核管理員 Controller。
 *
 * 權限規則：
 * 1. ADMIN：保留完整遊戲審核權限。
 * 2. REVIEWER：保留原本的遊戲審核權限。
 * 3. STAFF：必須由 Super Admin 授予 GAME_REVIEW。
 * 4. SUPPORT、MEMBER、PUBLISHER：不可操作。
 *
 * /**
 * 注意：
 * 使用 Spring Security 的方法層級授權時，
 * 必須在安全設定中啟用 EnableMethodSecurity。
 */
@RestController
@RequestMapping("/api/admin/game-reviews")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_REVIEWER', 'GAME_REVIEW')")
public class GameReviewAdminController {

    private final GameReviewAdminService gameReviewAdminService;

    /**
     * 核准遊戲上架。
     *
     * 管理員 ID 由 JWT 身分取得，
     * 不信任前端自行傳入的 adminId。
     */
    @PutMapping("/{gameId}/approve")
    public ResponseEntity<Game> approve(
            @PathVariable Integer gameId,
            @RequestBody GameAuditRequest request,
            @AuthenticationPrincipal Actor operator) {

        Game result = gameReviewAdminService.approve(
                gameId,
                operator.id(),
                request.getComment());

        if (result != null) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 拒絕遊戲上架。
     */
    @PutMapping("/{gameId}/reject")
    public ResponseEntity<Game> reject(
            @PathVariable Integer gameId,
            @RequestBody GameAuditRequest request,
            @AuthenticationPrincipal Actor operator) {

        Game result = gameReviewAdminService.reject(
                gameId,
                operator.id(),
                request.getComment());

        if (result != null) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 強制下架遊戲。
     */
    @PutMapping("/{gameId}/force-off-shelf")
    public ResponseEntity<Game> forceOffShelf(
            @PathVariable Integer gameId,
            @RequestBody GameAuditRequest request,
            @AuthenticationPrincipal Actor operator) {

        Game result = gameReviewAdminService.forceOffShelf(
                gameId,
                operator.id(),
                request.getComment());

        if (result != null) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 查詢待審核遊戲。
     */
    @GetMapping("/pending")
    public ResponseEntity<List<Game>> findPendingReviews() {

        List<Game> games = gameReviewAdminService.findPendingReviews();

        return ResponseEntity.ok(games);
    }

    /**
     * 查詢遊戲審核歷史。
     *
     * 預設每頁 10 筆，
     * 依 reviewedAt 由新到舊排序。
     */
    @GetMapping("/history")
    public Page<GameAuditLog> findAuditHistory(
            @PageableDefault(size = 10, sort = "reviewedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return gameReviewAdminService.findAuditHistory(pageable);
    }
}
