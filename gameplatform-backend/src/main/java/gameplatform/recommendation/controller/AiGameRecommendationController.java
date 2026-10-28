package gameplatform.recommendation.controller;

import java.util.List;
import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Sort;
import lombok.RequiredArgsConstructor;
import gameplatform.recommendation.dto.MemberPlayPreferenceDto;
import gameplatform.recommendation.dto.MemberPreferenceProfileDto;
import gameplatform.recommendation.dto.MemberReviewPreferenceDto;
import gameplatform.recommendation.dto.MemberTagPreferenceDto;
import gameplatform.recommendation.dto.PurchasedGameDto;
import gameplatform.recommendation.dto.RecommendationCandidateDto;
import gameplatform.recommendation.dto.TopRecommendedGameDto;
import gameplatform.recommendation.entity.AiGameRecommendation;
import gameplatform.recommendation.service.AiGameRecommendationService;
import gameplatform.recommendation.service.AiRecommendationRefreshService;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class AiGameRecommendationController {

    private final AiGameRecommendationService recommendationService;
    private final AiRecommendationRefreshService refreshService;

    private static void requireOwner(Actor actor, Integer memberId) {
        if (actor == null || !actor.isMember() || !Integer.valueOf(actor.id()).equals(memberId))
            throw new Problem(403, "只能查詢自己的推薦與偏好資料");
    }

    // 查詢指定會員的 AI 推薦遊戲
    @GetMapping("/members/{memberId}")
    public List<AiGameRecommendation> findByMemberId(
            Actor actor, @PathVariable Integer memberId) {
        requireOwner(actor, memberId);

        return recommendationService.findByMemberId(memberId);
    }

    // 查詢會員已購買的遊戲
    @GetMapping("/members/{memberId}/purchased-games")
    public List<PurchasedGameDto> findPurchasedGames(
            Actor actor, @PathVariable Integer memberId) {
        requireOwner(actor, memberId);

        return recommendationService.findPurchasedGames(memberId);
    }

    // 查詢會員偏好的遊戲類型
    @GetMapping("/members/{memberId}/tag-preferences")
    public List<MemberTagPreferenceDto> findMemberTagPreferences(
            Actor actor, @PathVariable Integer memberId) {
        requireOwner(actor, memberId);

        return recommendationService.findMemberTagPreferences(memberId);
    }

    // 查詢會員的遊玩偏好
    @GetMapping("/members/{memberId}/play-preferences")
    public List<MemberPlayPreferenceDto> findMemberPlayPreferences(
            Actor actor, @PathVariable Integer memberId) {
        requireOwner(actor, memberId);

        return recommendationService.findMemberPlayPreferences(memberId);
    }

    // 查詢會員的遊戲評價偏好
    @GetMapping("/members/{memberId}/review-preferences")
    public List<MemberReviewPreferenceDto> findMemberReviewPreferences(
            Actor actor, @PathVariable Integer memberId) {
        requireOwner(actor, memberId);

        return recommendationService.findMemberReviewPreferences(memberId);
    }

    // 查詢會員完整遊戲偏好資料
    @GetMapping("/members/{memberId}/profile")
    public MemberPreferenceProfileDto getMemberPreferenceProfile(
            Actor actor, @PathVariable Integer memberId) {
        requireOwner(actor, memberId);

        return recommendationService.getMemberPreferenceProfile(memberId);
    }

    // 查詢 AI 推薦候選遊戲
    @GetMapping("/members/{memberId}/candidates")
    public Page<RecommendationCandidateDto> findCandidates(
            Actor actor, @PathVariable Integer memberId,
            @PageableDefault(size = 10, sort = "releaseDate", direction = Sort.Direction.DESC) Pageable pageable) {
        requireOwner(actor, memberId);

        return recommendationService.findCandidates(
                memberId,
                pageable);
    }

    // 查詢會員的前 5 名推薦遊戲
    @GetMapping("/members/{memberId}/top5")
    public List<TopRecommendedGameDto> findTop5RecommendedGames(
            Actor actor, @PathVariable Integer memberId) {
        requireOwner(actor, memberId);

        return recommendationService
                .findTop5RecommendedGames(memberId);
    }

    // 查詢會員的 AI 推薦更新狀態
    @GetMapping("/members/{memberId}/refresh-status")
    public Map<String, Object> getRefreshStatus(
            Actor actor, @PathVariable Integer memberId) {
        requireOwner(actor, memberId);

        boolean needsRefresh = refreshService.needsRefresh(memberId);

        return Map.of(
                "memberId", memberId,
                "needsRefresh", needsRefresh,
                "message", needsRefresh
                        ? "AI 推薦需要更新"
                        : "AI 推薦目前不需要更新");
    }

}