package gameplatform.recommendation.dto;

import java.util.List;

public record MemberPreferenceProfileDto(

        Integer memberId,

        List<PurchasedGameDto> purchasedGames,

        List<MemberTagPreferenceDto> tagPreferences,

        List<MemberPlayPreferenceDto> playPreferences,

        List<MemberReviewPreferenceDto> reviewPreferences

) {
}