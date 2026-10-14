
package gameplatform.recommendation.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import gameplatform.recommendation.dto.PopularGameDto;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class PopularGameRepository {
    // 負責執行 SQL Server 查詢，不需要另外建立 JPA Entity
    private final JdbcTemplate jdbcTemplate;

    /**
     * 查詢全站熱門遊戲 TOP 5。
     *
     * 條件：
     * 1. 遊戲必須為 PUBLISHED
     * 2. 遊戲必須已發售
     * 3. 遊戲尚未到達預定下架時間
     *
     * 熱門程度依已付款訂單的購買數量排序。
     */
    public List<PopularGameDto> findTop5PopularGames() {

        String sql = """
                SELECT TOP (5)
                    g.game_id,
                    g.game_name,
                    g.cover_url,
                    g.price,
                    COALESCE(
                        SUM(
                            CASE
                                WHEN o.order_status = 'PAID'
                                THEN oi.quantity
                                ELSE 0
                            END
                        ),
                        0
                    ) AS purchase_count

                FROM dbo.games AS g

                LEFT JOIN dbo.order_items AS oi
                    ON g.game_id = oi.game_id

                LEFT JOIN dbo.orders AS o
                    ON oi.order_id = o.order_id

                -- 只顯示已公開上架的遊戲
                WHERE g.status = 'PUBLISHED'

                  -- 必須已經發售
                  AND (
                      g.release_date IS NULL
                      OR g.release_date <= SYSDATETIME()
                  )

                  -- 尚未到達預定下架時間
                  AND (
                      g.scheduled_off_shelf_at IS NULL
                      OR g.scheduled_off_shelf_at > SYSDATETIME()
                  )

                GROUP BY
                    g.game_id,
                    g.game_name,
                    g.cover_url,
                    g.price

                ORDER BY
                    purchase_count DESC,
                    g.game_id DESC
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new PopularGameDto(
                        rs.getInt("game_id"),
                        rs.getString("game_name"),
                        rs.getString("cover_url"),
                        rs.getBigDecimal("price"),
                        rs.getLong("purchase_count")));
    }

    /**
     * 查詢會員尚未擁有的熱門遊戲 TOP 5。
     *
     * 條件：
     * 1. 遊戲必須已公開上架
     * 2. 遊戲必須已發售
     * 3. 遊戲尚未到達預定下架時間
     * 4. 排除會員自己已購買的遊戲
     * 5. 排除其他會員贈送的遊戲
     *
     * 依照全站已付款購買數量排序。
     */
    public List<PopularGameDto> findTop5PopularGamesForMember(
            Integer memberId) {

        String sql = """
                SELECT TOP (5)
                    g.game_id,
                    g.game_name,
                    g.cover_url,
                    g.price,
                    COALESCE(
                        SUM(
                            CASE
                                WHEN o.order_status = 'PAID'
                                THEN oi.quantity
                                ELSE 0
                            END
                        ),
                        0
                    ) AS purchase_count

                FROM dbo.games AS g

                LEFT JOIN dbo.order_items AS oi
                    ON g.game_id = oi.game_id

                LEFT JOIN dbo.orders AS o
                    ON oi.order_id = o.order_id

                WHERE g.status = 'PUBLISHED'

                  -- 必須已經發售
                  AND (
                      g.release_date IS NULL
                      OR g.release_date <= SYSDATETIME()
                  )

                  -- 尚未到達預定下架時間
                  AND (
                      g.scheduled_off_shelf_at IS NULL
                      OR g.scheduled_off_shelf_at > SYSDATETIME()
                  )

                  -- 排除會員已經擁有的遊戲
                  AND NOT EXISTS (

                      SELECT 1
                      FROM dbo.orders AS owned_order

                      JOIN dbo.order_items AS owned_item
                          ON owned_order.order_id = owned_item.order_id

                      WHERE owned_item.game_id = g.game_id

                        -- 只計算已付款訂單
                        AND owned_order.order_status = 'PAID'

                        AND (
                            -- 會員自己購買
                            (
                                owned_order.member_id = ?
                                AND owned_item.is_gift = 0
                            )

                            OR

                            -- 其他會員贈送給他
                            (
                                owned_item.recipient_member_id = ?
                                AND owned_item.is_gift = 1
                            )
                        )
                  )

                GROUP BY
                    g.game_id,
                    g.game_name,
                    g.cover_url,
                    g.price

                ORDER BY
                    purchase_count DESC,
                    g.game_id DESC
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new PopularGameDto(
                        rs.getInt("game_id"),
                        rs.getString("game_name"),
                        rs.getString("cover_url"),
                        rs.getBigDecimal("price"),
                        rs.getLong("purchase_count")),
                memberId,
                memberId);
    }

}
