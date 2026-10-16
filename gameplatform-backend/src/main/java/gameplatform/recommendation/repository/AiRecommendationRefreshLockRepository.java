
package gameplatform.recommendation.repository;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AiRecommendationRefreshLockRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 嘗試取得 AI 推薦更新權限。
     *
     * 成功：回傳這次任務的 UUID。
     * 失敗：回傳 null。
     */
    @Transactional
    public UUID tryAcquire(Integer memberId) {

        String sql = """
                SET NOCOUNT ON;

                DECLARE @now DATETIME2 = SYSDATETIME();
                DECLARE @token UNIQUEIDENTIFIER = NEWID();
                DECLARE @acquired BIT = 0;

                -- 鎖定會員，避免同時建立更新任務
                IF EXISTS (
                    SELECT 1
                    FROM dbo.members WITH (UPDLOCK, HOLDLOCK)
                    WHERE member_id = ?
                )
                BEGIN

                    -- 第一次建立更新狀態
                    IF NOT EXISTS (
                        SELECT 1
                        FROM dbo.ai_recommendation_refresh_status
                        WHERE member_id = ?
                    )
                    BEGIN

                        INSERT INTO dbo.ai_recommendation_refresh_status (
                            member_id,
                            refresh_status,
                            last_attempt_at,
                            refresh_token
                        )
                        VALUES (?, 'PROCESSING', @now, @token);

                        SET @acquired = 1;
                    END
                    ELSE
                    BEGIN

                        -- 只有符合更新條件，才可以取得權限
                        UPDATE dbo.ai_recommendation_refresh_status
                        SET refresh_status = 'PROCESSING',
                            last_attempt_at = @now,
                            last_error = NULL,
                            refresh_token = @token
                        WHERE member_id = ?
                          AND (
                              (
                                  refresh_status = 'PROCESSING'
                                  AND (
                                      last_attempt_at IS NULL
                                      OR last_attempt_at <=
                                          DATEADD(MINUTE, -10, @now)
                                  )
                              )
                              OR
                              (
                                  refresh_status <> 'PROCESSING'
                                  AND (
                                      (
                                          next_refresh_at IS NOT NULL
                                          AND next_refresh_at <= @now
                                      )
                                      OR
                                      (
                                          next_refresh_at IS NULL
                                          AND refresh_status = 'SUCCESS'
                                          AND last_success_at <=
                                              DATEADD(DAY, -7, @now)
                                      )
                                      OR
                                      (
                                          next_refresh_at IS NULL
                                          AND refresh_status = 'FAILED'
                                          AND last_attempt_at <=
                                              DATEADD(HOUR, -1, @now)
                                      )
                                      OR
                                      (
                                          next_refresh_at IS NULL
                                          AND refresh_status = 'PENDING'
                                      )
                                  )
                              )
                          );

                        IF @@ROWCOUNT > 0
                            SET @acquired = 1;
                    END
                END

                -- 只有成功取得更新權限才回傳 Token
                SELECT CASE
                    WHEN @acquired = 1
                    THEN CONVERT(VARCHAR(36), @token)
                    ELSE NULL
                END;
                """;

        String token = jdbcTemplate.queryForObject(
                sql,
                String.class,
                memberId,
                memberId,
                memberId,
                memberId);

        return token == null ? null : UUID.fromString(token);
    }
}
