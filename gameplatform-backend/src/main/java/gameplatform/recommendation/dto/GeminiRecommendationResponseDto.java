package gameplatform.recommendation.dto;

import java.util.List;

public record GeminiRecommendationResponseDto(
                List<GeminiRecommendationDto> recommendations) {
}