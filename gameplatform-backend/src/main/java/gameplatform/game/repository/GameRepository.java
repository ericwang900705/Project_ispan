package gameplatform.game.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import gameplatform.game.entity.Game;

public interface GameRepository extends JpaRepository<Game, Integer> {

    Optional<Game> findByGameIdAndStatusIn(
            Integer gameId,
            List<String> statuses);

    List<Game> findByGameNameContainingAndStatusIn(
            String gameName,
            List<String> statuses);

    List<Game> findByStatus(String status);

    List<Game> findByStatusIn(List<String> statuses);

    @Modifying
    @Query("""
                UPDATE Game g
                SET g.status = 'PUBLISHED'
                WHERE g.status = 'COMING_SOON'
                AND g.releaseDate <= CURRENT_TIMESTAMP
            """)
    int autoPublishGames();
}