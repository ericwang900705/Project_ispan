
package gameplatform.recommendation.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import gameplatform.recommendation.dto.PopularGameDto;
import gameplatform.recommendation.service.PopularGameService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/recommendations/popular")
@RequiredArgsConstructor
public class PopularGameController {

    private final PopularGameService popularGameService;

    /**
     * 查詢熱門遊戲 TOP 5。
     *
     * 依照已付款訂單的購買數量排序。
     */
    @GetMapping("/top5")
    public List<PopularGameDto> getTop5PopularGames() {

        return popularGameService.getTop5PopularGames();
    }
}
