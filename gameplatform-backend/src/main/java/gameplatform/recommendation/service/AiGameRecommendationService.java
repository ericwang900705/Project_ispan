package gameplatform.recommendation.service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import gameplatform.game.entity.Game;
import gameplatform.game.repository.GameRepository;
import gameplatform.recommendation.entity.AiGameRecommendation;
import gameplatform.recommendation.repository.AiGameRecommendationRepository;
import gameplatform.recommendation.repository.MemberGameViewStatRepository;
import gameplatform.recommendation.repository.MemberPlayPreferenceRepository;
import gameplatform.recommendation.repository.MemberReviewPreferenceRepository;
import gameplatform.recommendation.repository.MemberTagSearchStatRepository;
import gameplatform.recommendation.dto.MemberPlayPreferenceDto;
import gameplatform.recommendation.dto.MemberPreferenceProfileDto;
import gameplatform.recommendation.dto.MemberReviewPreferenceDto;
import gameplatform.recommendation.dto.MemberTagPreferenceDto;
import gameplatform.recommendation.dto.PurchasedGameDto;
import gameplatform.recommendation.dto.RecommendationCandidateDto;
import gameplatform.recommendation.dto.TopRecommendedGameDto;
import gameplatform.recommendation.repository.PurchasedGameRepository;

@Service
@RequiredArgsConstructor
public class AiGameRecommendationService {

        private final AiGameRecommendationRepository recommendationRepository;

        private final GameRepository gameRepository;

        private final PurchasedGameRepository purchasedGameRepository;

        private final MemberReviewPreferenceRepository memberReviewPreferenceRepository;
        private final MemberPlayPreferenceRepository memberPlayPreferenceRepository;
        // 會員遊戲瀏覽統計
        private final MemberGameViewStatRepository memberGameViewStatRepository;

        // 會員 Tag 搜尋統計
        private final MemberTagSearchStatRepository memberTagSearchStatRepository;

        // 1. 查詢會員的 AI 推薦紀錄
        public List<AiGameRecommendation> findByMemberId(Integer memberId) {

                return recommendationRepository
                                .findByMemberIdOrderByRecommendationScoreDesc(memberId);
        }

        // 2. 新增或更新 AI 推薦結果
        @Transactional
        public AiGameRecommendation saveRecommendation(
                        Integer memberId,
                        Integer gameId,
                        BigDecimal score,
                        String reason,
                        String modelVersion) {

                // 檢查必要參數
                if (memberId == null || gameId == null || score == null) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "會員 ID、遊戲 ID、推薦分數不可為空");
                }

                // 檢查推薦分數
                if (score.compareTo(BigDecimal.ZERO) < 0
                                || score.compareTo(BigDecimal.ONE) > 0) {

                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "推薦分數必須介於 0 到 1");
                }

                // 確認遊戲存在
                if (!gameRepository.existsById(gameId)) {
                        throw new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "找不到此遊戲");
                }

                // 查詢是否已有推薦紀錄
                AiGameRecommendation recommendation = recommendationRepository
                                .findByMemberIdAndGameId(memberId, gameId)
                                .orElseGet(AiGameRecommendation::new);

                recommendation.setMemberId(memberId);
                recommendation.setGameId(gameId);
                recommendation.setRecommendationScore(score);
                recommendation.setReason(reason);
                recommendation.setModelVersion(modelVersion);

                return recommendationRepository.save(recommendation);
        }

        // 3. 刪除指定會員的全部推薦紀錄
        @Transactional
        public long deleteByMemberId(Integer memberId) {

                if (memberId == null) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "會員 ID 不可為空");
                }

                return recommendationRepository.deleteByMemberId(memberId);
        }

        // 查詢會員已購買的遊戲
        @Transactional(readOnly = true)
        public List<PurchasedGameDto> findPurchasedGames(
                        Integer memberId) {

                if (memberId == null || memberId <= 0) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "會員 ID 不正確");
                }

                return purchasedGameRepository
                                .findPurchasedGames(memberId)
                                .stream()
                                .map(game -> new PurchasedGameDto(
                                                game.getGameId(),
                                                game.getGameName(),
                                                game.getPrice()))
                                .toList();
        }

        // 查詢會員偏好的遊戲類型
        @Transactional(readOnly = true)
        public List<MemberTagPreferenceDto> findMemberTagPreferences(
                        Integer memberId) {

                if (memberId == null || memberId <= 0) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "會員 ID 不正確");
                }

                return purchasedGameRepository
                                .findMemberTagPreferences(memberId)
                                .stream()
                                .map(tag -> new MemberTagPreferenceDto(
                                                tag.getTagId(),
                                                tag.getTagName(),
                                                tag.getPurchaseCount()))
                                .toList();
        }

        // 查詢會員的遊玩偏好
        @Transactional(readOnly = true)
        public List<MemberPlayPreferenceDto> findMemberPlayPreferences(
                        Integer memberId) {

                if (memberId == null || memberId <= 0) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "會員 ID 不正確");
                }

                return memberPlayPreferenceRepository
                                .findMemberPlayPreferences(memberId)
                                .stream()
                                .map(play -> new MemberPlayPreferenceDto(
                                                play.getGameId(),
                                                play.getGameName(),
                                                play.getTotalPlaySeconds()))
                                .toList();
        }

        // 查詢會員的遊戲評價偏好
        @Transactional(readOnly = true)
        public List<MemberReviewPreferenceDto> findMemberReviewPreferences(
                        Integer memberId) {

                if (memberId == null || memberId <= 0) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "會員 ID 不正確");
                }

                return memberReviewPreferenceRepository
                                .findMemberReviewPreferences(memberId)
                                .stream()
                                .map(review -> new MemberReviewPreferenceDto(
                                                review.getGameId(),
                                                review.getGameName(),
                                                review.getRating(),
                                                review.getIsRecommended(),
                                                review.getContent()))
                                .toList();
        }

        // 整合會員的所有遊戲偏好資料
        // 整合會員的所有遊戲偏好資料
        @Transactional(readOnly = true)
        public MemberPreferenceProfileDto getMemberPreferenceProfile(
                        Integer memberId) {

                // 驗證會員 ID
                if (memberId == null || memberId <= 0) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "會員 ID 不正確");
                }

                // 1. 查詢會員已購買的遊戲
                var purchasedGames = findPurchasedGames(memberId);

                // 2. 查詢購買遊戲的標籤偏好
                var tagPreferences = findMemberTagPreferences(memberId);

                // 3. 查詢遊玩時數偏好
                var playPreferences = findMemberPlayPreferences(memberId);

                // 4. 查詢遊戲評論偏好
                var reviewPreferences = findMemberReviewPreferences(memberId);

                // 5. 查詢遊戲瀏覽偏好（最多 10 筆）
                var viewPreferences = memberGameViewStatRepository.findTopViewPreferences(
                                memberId,
                                PageRequest.of(0, 10));

                // 6. 查詢 Tag 搜尋偏好（最多 10 筆）
                var searchPreferences = memberTagSearchStatRepository.findTopSearchPreferences(
                                memberId,
                                PageRequest.of(0, 10));

                // 7. 整合所有會員偏好
                return new MemberPreferenceProfileDto(
                                memberId,
                                purchasedGames,
                                tagPreferences,
                                playPreferences,
                                reviewPreferences,
                                viewPreferences,
                                searchPreferences);
        }

        @Transactional(readOnly = true)
        public Page<RecommendationCandidateDto> findCandidates(
                        Integer memberId,
                        Pageable pageable) {

                if (memberId == null || memberId <= 0) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "會員 ID 不正確");
                }

                // 查詢會員已擁有的遊戲
                List<Integer> ownedGameIds = purchasedGameRepository.findOwnedGameIds(memberId);

                // AI 推薦只選擇已正式上架的遊戲
                // 不包含 COMING_SOON、PENDING_OFF_SHELF
                List<String> statuses = List.of("PUBLISHED");

                Page<Game> games;

                if (ownedGameIds.isEmpty()) {
                        games = gameRepository.findByStatusIn(
                                        statuses,
                                        pageable);
                } else {
                        games = gameRepository.findByStatusInAndGameIdNotIn(
                                        statuses,
                                        ownedGameIds,
                                        pageable);
                }

                return games.map(game -> new RecommendationCandidateDto(
                                game.getGameId(),
                                game.getGameName(),
                                game.getDescription(),
                                game.getPrice(),
                                game.getReleaseDate()));
        }

        @Transactional(readOnly = true)
        public List<TopRecommendedGameDto> findTop5RecommendedGames(
                        Integer memberId) {

                if (memberId == null || memberId <= 0) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "會員 ID 不正確");
                }

                // 會員已擁有的遊戲
                Set<Integer> ownedGameIds = new HashSet<>(
                                purchasedGameRepository.findOwnedGameIds(memberId));

                // 查詢已儲存的推薦結果（分數由高到低）
                List<AiGameRecommendation> recommendations = recommendationRepository
                                .findByMemberIdOrderByRecommendationScoreDesc(
                                                memberId);

                // 排除已擁有、不公開或不存在的遊戲
                return recommendations.stream()
                                .filter(r -> !ownedGameIds.contains(r.getGameId()))
                                .map(r -> gameRepository.findById(r.getGameId())
                                                // 即使推薦紀錄仍在 7 天快取內，
                                                // 只要遊戲不再是 PUBLISHED，就不顯示
                                                .filter(game -> "PUBLISHED".equals(game.getStatus()))
                                                .map(game -> new TopRecommendedGameDto(
                                                                game.getGameId(),
                                                                game.getGameName(),
                                                                game.getCoverUrl(),
                                                                game.getPrice(),
                                                                r.getRecommendationScore(),
                                                                r.getReason()))
                                                .orElse(null))
                                .filter(dto -> dto != null)
                                .limit(5)
                                .toList();
        }

}
