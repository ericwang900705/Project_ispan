package gameplatform.recommendation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import gameplatform.game.entity.Game;

public interface PurchasedGameRepository
        extends JpaRepository<Game, Integer> {

    @Query(value = """
            SELECT DISTINCT
                g.game_id AS gameId,
                g.game_name AS gameName,
                g.price AS price
            FROM dbo.orders o
            JOIN dbo.order_items oi
                ON o.order_id = oi.order_id
            JOIN dbo.games g
                ON oi.game_id = g.game_id
            WHERE o.member_id = :memberId
              AND o.order_status = 'PAID'
              AND oi.is_gift = 0
            ORDER BY g.game_id
            """, nativeQuery = true)
    List<PurchasedGameProjection> findPurchasedGames(
            @Param("memberId") Integer memberId);

    interface PurchasedGameProjection {
        Integer getGameId();

        String getGameName();

        java.math.BigDecimal getPrice();
    }

    // 統計會員購買過的遊戲類型
    @Query(value = """
            SELECT
                t.tag_id AS tagId,
                t.tag_name AS tagName,
                COUNT(DISTINCT oi.game_id) AS purchaseCount
            FROM dbo.orders o
            JOIN dbo.order_items oi
                ON o.order_id = oi.order_id
            JOIN dbo.game_tags gt
                ON oi.game_id = gt.game_id
            JOIN dbo.tags t
                ON gt.tag_id = t.tag_id
            WHERE o.member_id = :memberId
              AND o.order_status = 'PAID'
              AND oi.is_gift = 0
            GROUP BY
                t.tag_id,
                t.tag_name
            ORDER BY
                purchaseCount DESC,
                t.tag_id ASC
            """, nativeQuery = true)
    List<MemberTagPreferenceProjection> findMemberTagPreferences(
            @Param("memberId") Integer memberId);

    // 介面投影
    interface MemberTagPreferenceProjection {

        Integer getTagId();

        String getTagName();

        Long getPurchaseCount();
    }

    @Query(value = """
            SELECT DISTINCT oi.game_id
            FROM dbo.orders o
            JOIN dbo.order_items oi
                ON o.order_id = oi.order_id
            WHERE o.member_id = :memberId
              AND o.order_status = 'PAID'
              AND oi.is_gift = 0

            UNION

            SELECT DISTINCT oi.game_id
            FROM dbo.orders o
            JOIN dbo.order_items oi
                ON o.order_id = oi.order_id
            WHERE oi.recipient_member_id = :memberId
              AND o.order_status = 'PAID'
              AND oi.is_gift = 1
            """, nativeQuery = true)
    List<Integer> findOwnedGameIds(
            @Param("memberId") Integer memberId);
}