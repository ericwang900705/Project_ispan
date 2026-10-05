package gameplatform.game.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import gameplatform.game.entity.Game;

public interface GameRepository extends JpaRepository<Game, Integer> {

}