package gameplatform.recommendation.dto;

import java.util.List;

public record MemberPreferenceProfileDto(

                Integer memberId,

                // 已購買的遊戲
                List<PurchasedGameDto> purchasedGames,

                // 從購買紀錄分析出的 Tag 偏好
                List<MemberTagPreferenceDto> tagPreferences,

                // 遊戲遊玩偏好
                List<MemberPlayPreferenceDto> playPreferences,

                // 遊戲評論偏好
                List<MemberReviewPreferenceDto> reviewPreferences,

                // 遊戲詳情頁瀏覽偏好（新增）
                List<GameViewPreferenceDto> viewPreferences,

                // Tag 搜尋偏好（新增）
                List<TagSearchPreferenceDto> searchPreferences

) {
}