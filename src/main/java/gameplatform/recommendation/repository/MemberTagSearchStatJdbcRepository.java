
package gameplatform.recommendation.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class MemberTagSearchStatJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 記錄會員主動搜尋或選擇 Tag 的行為。
     *
     * 第一次搜尋：新增紀錄
     * 30 分鐘後再次搜尋：搜尋次數 +1
     * 30 分鐘內重複搜尋：不計數
     *
     * @return true 代表成功計數
     *         false 代表 30 分鐘內已計數
     */
    @Transactional
    public boolean recordTagSearch(Integer memberId, Integer tagId) {

        String sql = """
                SET NOCOUNT ON;

                DECLARE @now DATETIME2 = SYSDATETIME();
                DECLARE @counted BIT = 0;

                IF NOT EXISTS (
                    SELECT 1
                    FROM dbo.member_tag_search_stats
                         WITH (UPDLOCK, HOLDLOCK)
                    WHERE member_id = ?
                      AND tag_id = ?
                )
                BEGIN
                    INSERT INTO dbo.member_tag_search_stats (
                        member_id,
                        tag_id,
                        search_count,
                        first_searched_at,
                        last_searched_at
                    )
                    VALUES (?, ?, 1, @now, @now);

                    SET @counted = 1;
                END
                ELSE
                BEGIN
                    UPDATE dbo.member_tag_search_stats
                    SET search_count = search_count + 1,
                        last_searched_at = @now
                    WHERE member_id = ?
                      AND tag_id = ?
                      AND last_searched_at <= DATEADD(
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
                memberId, tagId,
                memberId, tagId,
                memberId, tagId);

        return Integer.valueOf(1).equals(result);
    }
}
