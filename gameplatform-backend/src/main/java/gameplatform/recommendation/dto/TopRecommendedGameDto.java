
package gameplatform.recommendation.dto;

import java.math.BigDecimal;

public record TopRecommendedGameDto(
        Integer gameId,
        String gameName,
        String coverUrl,
        BigDecimal price,
        BigDecimal recommendationScore,
        String reason) {
}
