
package gameplatform.recommendation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import gameplatform.recommendation.dto.HomeRecommendationDto;
import gameplatform.recommendation.service.HomeRecommendationService;
import gameplatform.support.model.Actor;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/recommendations/home")
@RequiredArgsConstructor
public class HomeRecommendationController {

    private final HomeRecommendationService homeRecommendationService;

    /**
     * 取得目前登入會員的首頁推薦。
     *
     * 有 AI 推薦：回傳 AI 推薦
     * 沒有 AI 推薦：回傳熱門 TOP 5
     *
     * AI 更新由背景執行。
     */
    @GetMapping
    public ResponseEntity<HomeRecommendationDto> getHomeRecommendations(
            @AuthenticationPrincipal Actor actor) {

        HomeRecommendationDto result = homeRecommendationService.getHomeRecommendations(actor.id());

        return ResponseEntity.ok(result);
    }
}
