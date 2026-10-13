
package gameplatform.recommendation.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AiRecommendationBackgroundService {

    private final AiRecommendationAutoRefreshService autoRefreshService;

    /**
     * 在背景執行 AI 推薦更新。
     *
     * 呼叫這個方法後，不需要等待 Gemini 回應。
     */
    @Async("aiRecommendationExecutor")
    public void refreshInBackground(Integer memberId) {

        try {

            autoRefreshService.refreshIfNeeded(memberId);

        } catch (Exception e) {

            // AutoRefreshService 已負責記錄 FAILED 狀態
            // 這裡只記錄背景執行錯誤
            System.err.println(
                    "AI 推薦背景更新失敗，會員 ID："
                            + memberId
                            + "，原因："
                            + e.getMessage());
        }
    }
}
