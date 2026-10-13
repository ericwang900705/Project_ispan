
package gameplatform.recommendation.dto;

import java.math.BigDecimal;

public record PopularGameDto(
        Integer gameId,
        String gameName,
        BigDecimal price,
        Long purchaseCount) {
}
