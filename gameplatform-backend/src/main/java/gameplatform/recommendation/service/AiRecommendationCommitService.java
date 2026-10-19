
package gameplatform.recommendation.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gameplatform.recommendation.dto.GeminiRecommendationResponseDto;
import gameplatform.recommendation.dto.RecommendationCandidateDto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AiRecommendationCommitService {

    private final AiRecommendationSaveService saveService;

    private final AiRecommendationRefreshService refreshService;

    /**
     * 將 AI 推薦結果與更新成功狀態，
     * 放在同一個資料庫交易中完成。
     *
     * 任一操作失敗，整個交易都會回滾。
     */
    @Transactional(rollbackFor = Exception.class)
    public int commitRecommendations(
            Integer memberId,
            UUID token,
            GeminiRecommendationResponseDto response,
            List<RecommendationCandidateDto> candidates,
            String modelVersion) {

        // 1. 驗證 Token，儲存 AI 推薦結果
        int savedCount = saveService.saveRecommendationsWithToken(
                memberId,
                token,
                response,
                candidates,
                modelVersion);

        // 2. 將更新狀態改為 SUCCESS
        // 並設定七天後才能再次更新
        refreshService.markSuccessWithToken(
                memberId,
                token);

        // 3. 兩個動作都成功，交易才會提交
        return savedCount;
    }
}
