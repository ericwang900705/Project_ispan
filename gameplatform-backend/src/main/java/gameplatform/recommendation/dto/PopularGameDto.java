package gameplatform.recommendation.dto;

import java.math.BigDecimal;

public record PopularGameDto(
                Integer gameId,
                String gameName,
                String coverUrl,
                BigDecimal price,
                Long purchaseCount) {
}