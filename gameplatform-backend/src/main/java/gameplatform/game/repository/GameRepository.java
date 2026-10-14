package gameplatform.game.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import gameplatform.game.entity.Game;

/** Storefront visibility is restricted to published and coming-soon games. */
public interface GameRepository extends JpaRepository<Game, Integer> {
    Optional<Game> findByGameIdAndStatusIn(Integer gameId, List<String> statuses);
    List<Game> findByGameNameContainingAndStatusIn(String keyword, List<String> statuses);
    Page<Game> findByStatusIn(List<String> statuses, Pageable pageable);

    // Based on Project_ispan's release scheduler. Only approved, playable games
    // may change from COMING_SOON to PUBLISHED; never bypass a pending review.
    @Modifying
    @Query(value = """
        UPDATE games SET status = 'PUBLISHED'
        WHERE status = 'COMING_SOON'
          AND release_date IS NOT NULL
          AND release_date <= CURRENT_TIMESTAMP
          AND pending_review_type IS NULL
          AND EXISTS (SELECT 1 FROM game_builds b
                      WHERE b.game_id = games.game_id AND b.status = 'ACTIVE')
        """, nativeQuery = true)
    int publishDueGames();
}
