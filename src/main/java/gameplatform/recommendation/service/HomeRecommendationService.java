
package gameplatform.recommendation.service;

import java.util.List;

import org.springframework.stereotype.Service;

import gameplatform.recommendation.dto.HomeRecommendationDto;
import gameplatform.recommendation.dto.HomeRecommendationDto.HomeRecommendedGameDto;
import gameplatform.recommendation.dto.MemberPreferenceProfileDto;
import gameplatform.recommendation.dto.PopularGameDto;
import gameplatform.recommendation.dto.TopRecommendedGameDto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HomeRecommendationService {

    private final AiGameRecommendationService recommendationService;

    private final PopularGameService popularGameService;

    // 判斷 AI 推薦是否需要更新
    private final AiRecommendationRefreshService refreshService;

    // 在背景執行 Gemini 推薦更新
    private final AiRecommendationBackgroundService backgroundService;

    /**
     * 取得會員首頁推薦遊戲。
     *
     * 有偏好且有 AI 推薦快取：顯示 AI 推薦。
     * 其他情況：顯示熱門遊戲。
     */
    public HomeRecommendationDto getHomeRecommendations(
            Integer memberId) {

        if (memberId == null || memberId <= 0) {
            throw new IllegalArgumentException("會員 ID 不正確");
        }

        // 1. 取得會員偏好
        MemberPreferenceProfileDto profile = recommendationService.getMemberPreferenceProfile(memberId);

        // 2. 判斷會員是否具有偏好資料
        boolean hasPreferences = !profile.purchasedGames().isEmpty()
                || !profile.tagPreferences().isEmpty()
                || !profile.playPreferences().isEmpty()
                || !profile.reviewPreferences().isEmpty()
                || !profile.viewPreferences().isEmpty()
                || !profile.searchPreferences().isEmpty();

        /*
         * 只有會員具有偏好資料，
         * 且 AI 推薦快取需要更新時，
         * 才提交背景更新任務。
         */
        if (hasPreferences && refreshService.needsRefresh(memberId)) {

            backgroundService.refreshInBackground(memberId);
        }

        // 3. 有偏好時，優先讀取既有 AI 推薦快取
        if (hasPreferences) {

            List<TopRecommendedGameDto> aiGames = recommendationService.findTop5RecommendedGames(memberId);

            if (!aiGames.isEmpty()) {

                List<HomeRecommendedGameDto> games = aiGames.stream()
                        .map(game -> new HomeRecommendedGameDto(
                                game.gameId(),
                                game.gameName(),
                                game.coverUrl(),
                                game.price(),
                                game.recommendationScore(),
                                game.reason()))
                        .toList();

                return new HomeRecommendationDto("AI", games);
            }
        }

        /*
         * 沒有偏好，或尚未產生 AI 推薦快取時，
         * 顯示會員尚未擁有的熱門遊戲 TOP 5。
         *
         * 排除：
         * 1. 會員自己已購買的遊戲
         * 2. 其他會員贈送給他的遊戲
         */
        List<PopularGameDto> popularGames = popularGameService.getTop5PopularGamesForMember(memberId);

        List<HomeRecommendedGameDto> games = popularGames.stream()
                .map(game -> new HomeRecommendedGameDto(
                        game.gameId(),
                        game.gameName(),
                        null,
                        game.price(),
                        null,
                        null))
                .toList();

        return new HomeRecommendationDto("POPULAR", games);
    }
}
