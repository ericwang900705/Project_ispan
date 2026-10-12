package gameplatform.recommendation.dto;

public record MemberTagPreferenceDto(
        Integer tagId,
        String tagName,
        Long purchaseCount) {
}