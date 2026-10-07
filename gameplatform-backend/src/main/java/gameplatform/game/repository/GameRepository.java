package gameplatform.game.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

        Page<Game> findByStatusIn(
                        List<String> statuses,
                        Pageable pageable);

        // 時間到(reaseDate <= CURRENT_TIMESTAMP)自動將遊戲狀態改為PUBLISHED
        @Modifying
        @Query("""
                            UPDATE Game g
                            SET g.status = 'PUBLISHED'
                            WHERE g.status = 'COMING_SOON'
                            AND g.releaseDate <= CURRENT_TIMESTAMP
                        """)
        int autoPublishGames();

        // 自動下架遊戲
        @Modifying
        @Query("""
                            UPDATE Game g
                            SET g.status = 'OFF_SHELF',
                                g.scheduledOffShelfAt = null
                            WHERE g.status = 'PENDING_OFF_SHELF'
                            AND g.scheduledOffShelfAt <= CURRENT_TIMESTAMP
                        """)
        int autoOffShelfGames();
}