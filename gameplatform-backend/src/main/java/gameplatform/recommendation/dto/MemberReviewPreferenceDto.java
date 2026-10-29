package gameplatform.recommendation.dto;

public record MemberReviewPreferenceDto(
        Integer gameId,
        String gameName,
        Integer rating,
        Boolean isRecommended,
        String content) {
}