package gameplatform.recommendation.dto;

import java.math.BigDecimal;

public record GeminiRecommendationDto(
        Integer gameId,
        BigDecimal score,
        String reason) {
}