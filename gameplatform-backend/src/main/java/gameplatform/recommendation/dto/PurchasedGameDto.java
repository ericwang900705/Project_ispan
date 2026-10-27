package gameplatform.recommendation.dto;

import java.math.BigDecimal;

public record PurchasedGameDto(
                Integer gameId,
                String gameName,
                BigDecimal price) {
}