package gameplatform.game.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import gameplatform.game.entity.GameBuild;

public interface GameBuildRepository
        extends JpaRepository<GameBuild, Integer> {

    boolean existsByGameIdAndStatus(Integer gameId, String status);
}