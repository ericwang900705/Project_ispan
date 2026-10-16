
package gameplatform.recommendation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import gameplatform.game.entity.Game;

public interface MemberReviewPreferenceRepository
        extends JpaRepository<Game, Integer> {

    // 查詢會員的遊戲評價偏好
    @Query(value = """
            SELECT
                g.game_id AS gameId,
                g.game_name AS gameName,
                gr.rating AS rating,
                gr.is_recommended AS isRecommended,
                gr.content AS content
            FROM dbo.game_reviews gr
            JOIN dbo.games g
                ON gr.game_id = g.game_id
            WHERE gr.member_id = :memberId
              AND gr.status = 'NORMAL'
            ORDER BY
                gr.rating DESC,
                gr.created_at DESC
            """, nativeQuery = true)
    List<MemberReviewPreferenceProjection> findMemberReviewPreferences(
            @Param("memberId") Integer memberId);

    interface MemberReviewPreferenceProjection {

        Integer getGameId();

        String getGameName();

        Integer getRating();

        Boolean getIsRecommended();

        String getContent();
    }
}
