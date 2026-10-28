
package gameplatform.recommendation.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import gameplatform.recommendation.dto.GeminiRecommendationResponseDto;
import gameplatform.recommendation.dto.MemberPreferenceProfileDto;
import gameplatform.recommendation.dto.RecommendationCandidateDto;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

// 這支 Service 有什麼功能？
// 步驟	執行的方法
// 取得更新權限	refreshService.tryStartRefresh()
// 取得六種偏好	getMemberPreferenceProfile()
// 取得最多 50 款候選遊戲	findCandidates()
// 呼叫 Gemini	generateRecommendations()
// 解析 JSON	objectMapper.readValue()
// 儲存推薦	saveRecommendations()
// 設定七天快取	markSuccess()
// 更新失敗狀態	markFailed()
// 這樣就能重用你已經完成的所有 Service。

@Service
@RequiredArgsConstructor
public class AiRecommendationAutoRefreshService {

    private final AiRecommendationRefreshService refreshService;

    private final AiGameRecommendationService recommendationService;

    private final GeminiService geminiService;

    private final AiRecommendationCommitService commitService;

    private final ObjectMapper objectMapper;

    // 必須與 application.properties 的 gemini.model 一致
    @org.springframework.beans.factory.annotation.Value("${gemini.model}")
    private String modelVersion;

    /**
     * 檢查並執行 AI 推薦更新。
     *
     * true：成功完成更新
     * false：目前不需要更新，或其他任務正在更新
     */
    public boolean refreshIfNeeded(Integer memberId) {

        // 1. 嘗試取得更新權限
        UUID token = refreshService.tryStartRefresh(memberId);

        if (token == null) {
            return false;
        }

        try {

            // 2. 取得會員偏好資料
            MemberPreferenceProfileDto profile = recommendationService.getMemberPreferenceProfile(memberId);

            // 3. 取得最多 50 款候選遊戲
            List<RecommendationCandidateDto> candidates = recommendationService.findCandidates(
                    memberId,
                    PageRequest.of(0, 50)).getContent();

            if (candidates.isEmpty()) {
                throw new IllegalStateException(
                        "沒有可推薦的候選遊戲");
            }

            // 4. 呼叫 Gemini 產生推薦結果
            String json = geminiService.generateRecommendations(
                    profile,
                    candidates);

            // 5. 將 Gemini JSON 轉成 DTO
            GeminiRecommendationResponseDto response = objectMapper.readValue(
                    json,
                    GeminiRecommendationResponseDto.class);

            // 6. 在同一個交易中：
            // 驗證 Token、儲存推薦、更新 SUCCESS
            commitService.commitRecommendations(
                    memberId,
                    token,
                    response,
                    candidates,
                    modelVersion);

            return true;

        } catch (Exception e) {

            // 7. 更新失敗時，只有持有有效 Token
            // 的任務才能將狀態改成 FAILED
            try {
                refreshService.markFailedWithToken(
                        memberId,
                        token,
                        e.getMessage());
            } catch (Exception statusError) {
                e.addSuppressed(statusError);
            }

            throw new IllegalStateException(
                    "AI 推薦自動更新失敗，會員 ID：" + memberId,
                    e);
        }
    }

}
