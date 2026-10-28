
package gameplatform.recommendation.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 首頁推薦 API 回傳資料
 */
public record HomeRecommendationDto(
                // 推薦來源：AI 或 POPULAR
                String recommendationType,
                // 首頁顯示的遊戲清單
                List<HomeRecommendedGameDto> games) {
        /**
         * 首頁遊戲卡片資料
         */
        public record HomeRecommendedGameDto(

                        Integer gameId,
                        String gameName,
                        String coverUrl,
                        BigDecimal price,
                        // AI 推薦分數，熱門遊戲為 null
                        BigDecimal recommendationScore,
                        // AI 推薦理由，熱門遊戲為 null
                        String reason

        ) {
        }
}
