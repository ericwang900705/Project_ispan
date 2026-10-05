package gameplatform.game.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import gameplatform.game.entity.Game;
import gameplatform.game.repository.GameRepository;

@Service
public class GameService {

    private final GameRepository gameRepository;

    public GameService(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    // 查詢全部遊戲
    public List<Game> findAll() {
        return gameRepository.findAll();
    }

    // 根據 ID 查詢一款遊戲
    public Optional<Game> findById(Integer gameId) {
        return gameRepository.findById(gameId);
    }
}