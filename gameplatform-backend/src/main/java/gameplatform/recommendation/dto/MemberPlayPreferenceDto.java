package gameplatform.recommendation.dto;

public record MemberPlayPreferenceDto(
        Integer gameId,
        String gameName,
        Long totalPlaySeconds) {
}