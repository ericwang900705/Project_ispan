
package gameplatform.recommendation.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

// 這個版本的優點
// - 防止重複新增：利用 SQL Server 的鎖定機制，保護同一組 (member_id, game_id)。
// - 30 分鐘防重複：同一會員短時間重新整理遊戲詳情頁，不會增加瀏覽次數。
// - 交易一致性：@Transactional 確保鎖定與寫入在同一交易中完成。
// - 保留原本 JPA 架構：其他組員不需要跟著修改 Entity。
// - 容易整合：未來只要由 Service 呼叫 recordView()。
@Repository
@RequiredArgsConstructor
public class MemberGameViewStatJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public boolean recordView(Integer memberId, Integer gameId) {

        String sql = """
                SET NOCOUNT ON;

                DECLARE @now DATETIME2 = SYSDATETIME();
                DECLARE @counted BIT = 0;

                IF NOT EXISTS (
                    SELECT 1
                    FROM dbo.member_game_view_stats
                         WITH (UPDLOCK, HOLDLOCK)
                    WHERE member_id = ?
                      AND game_id = ?
                )
                BEGIN
                    INSERT INTO dbo.member_game_view_stats (
                        member_id,
                        game_id,
                        view_count,
                        first_viewed_at,
                        last_viewed_at
                    )
                    VALUES (?, ?, 1, @now, @now);

                    SET @counted = 1;
                END
                ELSE
                BEGIN
                    UPDATE dbo.member_game_view_stats
                    SET view_count = view_count + 1,
                        last_viewed_at = @now
                    WHERE member_id = ?
                      AND game_id = ?
                      AND last_viewed_at <= DATEADD(
                          MINUTE, -30, @now
                      );

                    IF @@ROWCOUNT > 0
                        SET @counted = 1;
                END

                SELECT CAST(@counted AS INT);
                """;

        Integer result = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                memberId, gameId,
                memberId, gameId,
                memberId, gameId);

        return Integer.valueOf(1).equals(result);
    }
}
