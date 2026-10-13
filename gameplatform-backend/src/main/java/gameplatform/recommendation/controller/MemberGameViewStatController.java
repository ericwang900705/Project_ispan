
package gameplatform.recommendation.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import gameplatform.recommendation.service.MemberGameViewStatService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class MemberGameViewStatController {

    private final MemberGameViewStatService viewStatService;

    @PostMapping("/{gameId}/views")
    public ResponseEntity<Map<String, Boolean>> recordGameView(
            @PathVariable Integer gameId) {

        // TODO: 正式整合 JWT 後改成取得登入會員 ID
        Integer memberId = 1;

        boolean counted = viewStatService.recordGameView(
                memberId,
                gameId);

        return ResponseEntity.ok(
                Map.of("counted", counted));
    }
}
