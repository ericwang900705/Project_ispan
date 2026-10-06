package gameplatform.game.service;

import java.util.List;
import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import gameplatform.game.dto.GameRequest;
import gameplatform.game.entity.Game;
import gameplatform.game.repository.GameRepository;
import jakarta.transaction.Transactional;

@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRepository gameRepository;

    public List<Game> findAll() {

        List<String> statuses = List.of("COMING_SOON", "PUBLISHED");

        return gameRepository.findByStatusIn(statuses);
    }

    // 模糊查詢
    public List<Game> findByGameName(String keyword) {

        List<String> statuses = List.of("COMING_SOON", "PUBLISHED");

        return gameRepository
                .findByGameNameContainingAndStatusIn(
                        keyword,
                        statuses);
    }

    public Optional<Game> findById(Integer gameId) {

        List<String> statuses = List.of("COMING_SOON", "PUBLISHED");

        return gameRepository.findByGameIdAndStatusIn(
                gameId,
                statuses);
    }

    public Game insert(GameRequest request) {

        Game game = new Game();

        game.setMemberId(request.getMemberId());
        game.setGameName(request.getGameName());
        game.setCoverUrl(request.getCoverUrl());
        game.setReleaseDate(request.getReleaseDate());
        game.setPrice(request.getPrice());
        game.setDescription(request.getDescription());

        return gameRepository.save(game);
    }

    public Game update(Integer gameId, GameRequest request) {

        Optional<Game> result = gameRepository.findById(gameId);

        if (result.isPresent()) {

            Game oldGame = result.get();

            oldGame.setGameName(request.getGameName());
            oldGame.setCoverUrl(request.getCoverUrl());
            oldGame.setReleaseDate(request.getReleaseDate());
            oldGame.setPrice(request.getPrice());
            oldGame.setDescription(request.getDescription());

            return gameRepository.save(oldGame);

        } else {
            return null;
        }
    }

    // 上架遊戲
    public Game publish(Integer gameId) {

        Optional<Game> result = gameRepository.findById(gameId);

        if (result.isPresent()) {

            Game game = result.get();
            game.setStatus("PUBLISHED");

            return gameRepository.save(game);

        } else {
            return null;
        }
    }

    // 下架遊戲
    public Game offShelf(Integer gameId) {

        Optional<Game> result = gameRepository.findById(gameId);

        if (result.isPresent()) {

            Game game = result.get();
            game.setStatus("OFF_SHELF");

            return gameRepository.save(game);

        } else {
            return null;
        }
    }

    public Game comingSoon(Integer gameId) {

        Optional<Game> result = gameRepository.findById(gameId);

        if (result.isPresent()) {

            Game game = result.get();

            game.setStatus("COMING_SOON");

            return gameRepository.save(game);

        } else {
            return null;
        }
    }

    // 自動上架遊戲(不使用for迴圈)
    @Transactional
    @Scheduled(fixedRate = 60000)
    public void autoPublishGames() {

        int updated = gameRepository.autoPublishGames();

        if (updated > 0) {
            System.out.println(
                    "自動發售完成，本次上架 " + updated + " 款遊戲");
        }
    }

}