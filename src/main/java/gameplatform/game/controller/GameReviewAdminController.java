package gameplatform.game.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Sort;

import gameplatform.game.dto.GameAuditRequest;
import gameplatform.game.entity.Game;
import gameplatform.game.entity.GameAuditLog;
import gameplatform.game.service.GameReviewAdminService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/game-reviews")
@RequiredArgsConstructor
public class GameReviewAdminController {

    private final GameReviewAdminService gameReviewAdminService;

    // 核准遊戲上架
    @PutMapping("/{gameId}/approve")
    public ResponseEntity<Game> approve(
            @PathVariable Integer gameId,
            @RequestBody GameAuditRequest request) {

        Game result = gameReviewAdminService.approve(
                gameId,
                request.getAdminId(),
                request.getComment());

        if (result != null) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // 拒絕遊戲上架
    @PutMapping("/{gameId}/reject")
    public ResponseEntity<Game> reject(@PathVariable Integer gameId, @RequestBody GameAuditRequest request) {

        Game result = gameReviewAdminService.reject(
                gameId,
                request.getAdminId(),
                request.getComment());

        if (result != null) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // 強制下架遊戲
    @PutMapping("/{gameId}/force-off-shelf")
    public ResponseEntity<Game> forceOffShelf(
            @PathVariable Integer gameId,
            @RequestBody GameAuditRequest request) {

        Game result = gameReviewAdminService.forceOffShelf(
                gameId,
                request.getAdminId(),
                request.getComment());

        if (result != null) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // 查詢待審核遊戲
    @GetMapping("/pending")
    public ResponseEntity<List<Game>> findPendingReviews() {

        List<Game> games = gameReviewAdminService.findPendingReviews();

        return ResponseEntity.ok(games);
    }

    // 管理員查詢遊戲審核歷史
    @GetMapping("/history")
    public Page<GameAuditLog> findAuditHistory(
            @PageableDefault(size = 10, sort = "reviewedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return gameReviewAdminService.findAuditHistory(pageable);
    }

}