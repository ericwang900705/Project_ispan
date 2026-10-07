package gameplatform.game.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import gameplatform.game.dto.GameAuditRequest;
import gameplatform.game.entity.Game;
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

}