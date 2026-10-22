
package gameplatform.recommendation.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gameplatform.recommendation.entity.AiRecommendationRefreshStatus;
import gameplatform.recommendation.repository.AiRecommendationRefreshLockRepository;
import gameplatform.recommendation.repository.AiRecommendationRefreshStatusRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AiRecommendationRefreshService {

    private final AiRecommendationRefreshStatusRepository refreshStatusRepository;

    private final AiRecommendationRefreshLockRepository refreshLockRepository;

    private final JdbcTemplate jdbcTemplate;

    // AI 推薦有效期限：7 天
    private static final int CACHE_DAYS = 7;

    // AI 更新失敗後，至少等待 1 小時
    private static final int RETRY_HOURS = 1;

    // AI 執行超過 10 分鐘，視為處理逾時
    private static final int PROCESSING_TIMEOUT_MINUTES = 10;

    /**
     * 判斷會員是否需要重新產生 AI 推薦
     */
    public boolean needsRefresh(Integer memberId) {

        AiRecommendationRefreshStatus status = refreshStatusRepository.findById(memberId)
                .orElse(null);

        // 新會員：尚未有更新狀態
        if (status == null) {
            return true;
        }

        LocalDateTime now = LocalDateTime.now();

        // AI 正在處理
        if ("PROCESSING".equals(status.getRefreshStatus())) {

            LocalDateTime lastAttempt = status.getLastAttemptAt();

            // 尚未超過 10 分鐘，不允許重複更新
            if (lastAttempt != null &&
                    now.isBefore(lastAttempt.plusMinutes(
                            PROCESSING_TIMEOUT_MINUTES))) {
                return false;
            }

            // 超過 10 分鐘，允許重新嘗試
            return true;
        }

        // 尚未到達下次允許更新時間
        LocalDateTime nextRefresh = status.getNextRefreshAt();

        if (nextRefresh != null && now.isBefore(nextRefresh)) {
            return false;
        }

        // 更新成功，但尚未有 nextRefreshAt
        if ("SUCCESS".equals(status.getRefreshStatus())
                && status.getLastSuccessAt() != null) {

            LocalDateTime expiresAt = status.getLastSuccessAt().plusDays(CACHE_DAYS);

            return !now.isBefore(expiresAt);
        }

        // 更新失敗，但尚未有 nextRefreshAt
        if ("FAILED".equals(status.getRefreshStatus())
                && status.getLastAttemptAt() != null) {

            LocalDateTime retryAt = status.getLastAttemptAt().plusHours(RETRY_HOURS);

            return !now.isBefore(retryAt);
        }

        // PENDING 或沒有更新紀錄
        return true;
    }

    /**
     * AI 開始更新推薦
     *
     * 注意：這個方法只負責記錄狀態，
     * 尚未處理多請求同時執行的競爭問題。
     */
    @Transactional
    public void markProcessing(Integer memberId) {

        AiRecommendationRefreshStatus status = refreshStatusRepository.findById(memberId)
                .orElseGet(() -> {
                    AiRecommendationRefreshStatus newStatus = new AiRecommendationRefreshStatus();
                    newStatus.setMemberId(memberId);
                    return newStatus;
                });

        status.setRefreshStatus("PROCESSING");
        status.setLastAttemptAt(LocalDateTime.now());
        status.setLastError(null);

        refreshStatusRepository.save(status);
    }

    /**
     * AI 推薦成功
     * 設定下一次更新時間為 7 天後
     */
    @Transactional
    public void markSuccess(Integer memberId) {

        AiRecommendationRefreshStatus status = refreshStatusRepository.findById(memberId)
                .orElseThrow(() -> new IllegalStateException(
                        "找不到會員 AI 更新狀態"));

        LocalDateTime now = LocalDateTime.now();

        status.setRefreshStatus("SUCCESS");
        status.setLastSuccessAt(now);
        status.setNextRefreshAt(now.plusDays(CACHE_DAYS));
        status.setLastError(null);

        refreshStatusRepository.save(status);
    }

    /**
     * AI 推薦失敗
     * 設定 1 小時後才能再次嘗試
     */
    @Transactional
    public void markFailed(Integer memberId, String errorMessage) {

        AiRecommendationRefreshStatus status = refreshStatusRepository.findById(memberId)
                .orElseThrow(() -> new IllegalStateException(
                        "找不到會員 AI 更新狀態"));

        LocalDateTime now = LocalDateTime.now();

        status.setRefreshStatus("FAILED");
        status.setNextRefreshAt(now.plusHours(RETRY_HOURS));

        // 避免錯誤訊息超過資料庫欄位長度
        if (errorMessage != null && errorMessage.length() > 500) {
            errorMessage = errorMessage.substring(0, 500);
        }

        status.setLastError(errorMessage);

        refreshStatusRepository.save(status);
    }

    /**
     * 嘗試取得 AI 推薦更新權限
     *
     * 成功：回傳本次更新任務的 UUID
     * 失敗：回傳 null
     */
    public UUID tryStartRefresh(Integer memberId) {

        if (memberId == null || memberId <= 0) {
            throw new IllegalArgumentException("會員 ID 不正確");
        }

        return refreshLockRepository.tryAcquire(memberId);
    }

    /**
     * AI 推薦更新成功。
     * 只有目前持有更新權限的任務才能修改狀態。
     */
    @Transactional
    public void markSuccessWithToken(
            Integer memberId,
            UUID token) {

        if (memberId == null || token == null) {
            throw new IllegalArgumentException(
                    "會員 ID 或任務識別碼不可為空");
        }

        String sql = """
                UPDATE dbo.ai_recommendation_refresh_status
                SET refresh_status = 'SUCCESS',
                    last_success_at = SYSDATETIME(),
                    next_refresh_at = DATEADD(DAY, 7, SYSDATETIME()),
                    last_error = NULL,
                    refresh_token = NULL
                WHERE member_id = ?
                  AND refresh_token = ?
                  AND refresh_status = 'PROCESSING'
                """;

        int updated = jdbcTemplate.update(
                sql,
                memberId,
                token.toString());

        if (updated != 1) {
            throw new IllegalStateException(
                    "AI 更新任務已失效，無法標記成功");
        }
    }

    /**
     * AI 推薦更新失敗。
     * 只有目前持有更新權限的任務才能記錄失敗。
     */
    @Transactional
    public void markFailedWithToken(
            Integer memberId,
            UUID token,
            String errorMessage) {

        if (memberId == null || token == null) {
            throw new IllegalArgumentException(
                    "會員 ID 或任務識別碼不可為空");
        }

        // 避免超過資料庫 NVARCHAR(500) 長度
        if (errorMessage != null && errorMessage.length() > 500) {
            errorMessage = errorMessage.substring(0, 500);
        }

        String sql = """
                UPDATE dbo.ai_recommendation_refresh_status
                SET refresh_status = 'FAILED',
                    next_refresh_at = DATEADD(HOUR, 1, SYSDATETIME()),
                    last_error = ?,
                    refresh_token = NULL
                WHERE member_id = ?
                  AND refresh_token = ?
                  AND refresh_status = 'PROCESSING'
                """;

        int updated = jdbcTemplate.update(
                sql,
                errorMessage,
                memberId,
                token.toString());

        if (updated == 0) {
            // 任務已過期，不能修改新任務的狀態
            return;
        }

        if (updated != 1) {
            throw new IllegalStateException(
                    "AI 更新失敗狀態寫入異常");
        }
    }

}
