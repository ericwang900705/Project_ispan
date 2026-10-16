
package gameplatform.recommendation.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import gameplatform.recommendation.service.MemberGameViewStatService;
import gameplatform.support.model.Actor;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class MemberGameViewStatController {

        private final MemberGameViewStatService viewStatService;

        /**
         * 記錄目前登入會員的遊戲瀏覽行為。
         * 會員 ID 從 JWT 取得。
         */
        @PostMapping("/{gameId}/views")
        public ResponseEntity<Map<String, Boolean>> recordGameView(
                        @PathVariable Integer gameId,
                        @AuthenticationPrincipal Actor actor) {

                boolean counted = viewStatService.recordGameView(
                                actor.id(),
                                gameId);

                return ResponseEntity.ok(
                                Map.of("counted", counted));
        }
}
