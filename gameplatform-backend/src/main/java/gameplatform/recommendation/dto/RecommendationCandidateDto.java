package gameplatform.recommendation.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RecommendationCandidateDto(
        Integer gameId,
        String gameName,
        String description,
        BigDecimal price,
        LocalDateTime releaseDate) {
}