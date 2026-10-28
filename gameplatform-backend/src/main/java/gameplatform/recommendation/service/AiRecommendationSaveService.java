package gameplatform.recommendation.service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gameplatform.recommendation.dto.GeminiRecommendationDto;
import gameplatform.recommendation.dto.GeminiRecommendationResponseDto;
import gameplatform.recommendation.dto.RecommendationCandidateDto;
import gameplatform.recommendation.entity.AiGameRecommendation;
import gameplatform.recommendation.repository.AiGameRecommendationRepository;

import lombok.RequiredArgsConstructor;

// 這個 Service 專門負責：
// 1. 驗證 Gemini 回傳的資料。
// 2. 確認遊戲在候選清單內。
// 3. 清除會員舊的推薦結果。
// 4. 寫入最新推薦結果。

@Service
@RequiredArgsConstructor
public class AiRecommendationSaveService {

        private final AiGameRecommendationRepository recommendationRepository;
        private final JdbcTemplate jdbcTemplate;

        @Transactional
        public int saveRecommendations(
                        Integer memberId,
                        GeminiRecommendationResponseDto response,
                        List<RecommendationCandidateDto> candidates,
                        String modelVersion) {

                if (response == null || response.recommendations() == null) {
                        throw new IllegalArgumentException(
                                        "AI 推薦結果不可為空");
                }

                List<GeminiRecommendationDto> recommendations = response.recommendations();

                if (recommendations.size() > 5) {
                        throw new IllegalArgumentException(
                                        "AI 推薦數量不可超過 5 款");
                }

                // 取得合法候選遊戲 ID
                Set<Integer> candidateIds = new HashSet<>();

                for (RecommendationCandidateDto candidate : candidates) {
                        candidateIds.add(candidate.gameId());
                }

                // 防止 Gemini 重複推薦同一款遊戲
                Set<Integer> recommendedIds = new HashSet<>();

                // 先驗證所有資料，通過後才修改資料庫
                for (GeminiRecommendationDto recommendation : recommendations) {

                        if (recommendation == null
                                        || recommendation.gameId() == null
                                        || !candidateIds.contains(recommendation.gameId())) {
                                throw new IllegalArgumentException(
                                                "AI 推薦了不在候選清單中的遊戲");
                        }

                        if (!recommendedIds.add(recommendation.gameId())) {
                                throw new IllegalArgumentException(
                                                "AI 推薦結果包含重複遊戲");
                        }

                        BigDecimal score = recommendation.score();

                        if (score == null
                                        || score.compareTo(BigDecimal.ZERO) < 0
                                        || score.compareTo(BigDecimal.ONE) > 0) {
                                throw new IllegalArgumentException(
                                                "AI 推薦分數必須介於 0 到 1");
                        }

                        if (recommendation.reason() == null
                                        || recommendation.reason().isBlank()
                                        || recommendation.reason().length() > 500) {
                                throw new IllegalArgumentException(
                                                "AI 推薦原因不可為空，且不得超過 500 字");
                        }
                }

                // 驗證通過，清除舊推薦
                recommendationRepository.deleteAllByMemberIdBatch(memberId);

                // 將新推薦寫入資料庫
                for (GeminiRecommendationDto recommendation : recommendations) {

                        AiGameRecommendation entity = new AiGameRecommendation();

                        entity.setMemberId(memberId);
                        entity.setGameId(recommendation.gameId());
                        entity.setRecommendationScore(
                                        recommendation.score());
                        entity.setReason(recommendation.reason());
                        entity.setModelVersion(modelVersion);

                        recommendationRepository.save(entity);
                }

                recommendationRepository.flush();

                return recommendations.size();
        }

        /**
         * 只有持有有效任務識別碼的更新任務，
         * 才能儲存新的 AI 推薦結果。
         */
        @Transactional
        public int saveRecommendationsWithToken(
                        Integer memberId,
                        UUID token,
                        GeminiRecommendationResponseDto response,
                        List<RecommendationCandidateDto> candidates,
                        String modelVersion) {

                if (memberId == null || memberId <= 0 || token == null) {
                        throw new IllegalArgumentException(
                                        "會員 ID 或任務識別碼不正確");
                }

                /*
                 * 使用 SQL Server 鎖定更新狀態。
                 *
                 * 在目前交易結束前，其他更新任務
                 * 無法同時修改這筆狀態資料。
                 */
                String sql = """
                                SELECT COUNT(*)
                                FROM dbo.ai_recommendation_refresh_status
                                     WITH (UPDLOCK, HOLDLOCK)
                                WHERE member_id = ?
                                  AND refresh_token = ?
                                  AND refresh_status = 'PROCESSING'
                                """;

                Integer count = jdbcTemplate.queryForObject(
                                sql,
                                Integer.class,
                                memberId,
                                token.toString());

                if (!Integer.valueOf(1).equals(count)) {
                        throw new IllegalStateException(
                                        "AI 更新任務已失效，禁止覆蓋推薦結果");
                }

                // 驗證通過後，使用原本的儲存邏輯
                return saveRecommendations(
                                memberId,
                                response,
                                candidates,
                                modelVersion);
        }

}