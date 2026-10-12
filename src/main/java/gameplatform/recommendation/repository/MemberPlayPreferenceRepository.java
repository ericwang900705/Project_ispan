package gameplatform.recommendation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import gameplatform.game.entity.Game;

public interface MemberPlayPreferenceRepository
        extends JpaRepository<Game, Integer> {

    // 統計會員各款遊戲的累積遊玩秒數
    @Query(value = """
            SELECT
                g.game_id AS gameId,
                g.game_name AS gameName,
                SUM(CAST(pr.play_seconds AS BIGINT))
                    AS totalPlaySeconds
            FROM dbo.play_records pr
            JOIN dbo.games g
                ON pr.game_id = g.game_id
            WHERE pr.member_id = :memberId
              AND pr.play_seconds > 0
              AND pr.ended_at IS NOT NULL
            GROUP BY
                g.game_id,
                g.game_name
            ORDER BY
                totalPlaySeconds DESC,
                g.game_id ASC
            """, nativeQuery = true)
    List<MemberPlayPreferenceProjection> findMemberPlayPreferences(
            @Param("memberId") Integer memberId);

    interface MemberPlayPreferenceProjection {

        Integer getGameId();

        String getGameName();

        Long getTotalPlaySeconds();
    }
}