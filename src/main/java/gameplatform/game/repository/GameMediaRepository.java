
package gameplatform.game.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import gameplatform.game.entity.GameMedia;

public interface GameMediaRepository
        extends JpaRepository<GameMedia, Integer> {

    // 依照展示順序由小到大查詢
    List<GameMedia> findByGameIdOrderByDisplayOrderAsc(
            Integer gameId);
}