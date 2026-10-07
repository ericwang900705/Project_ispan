package gameplatform.game.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // 查詢所有遊戲(只查詢狀態為 COMING_SOON 或 PUBLISHED 的遊戲)
    public Page<Game> findAll(Pageable pageable) {

        List<String> statuses = List.of(
                "COMING_SOON",
                "PUBLISHED",
                "PENDING_OFF_SHELF");

        return gameRepository.findByStatusIn(
                statuses,
                pageable);
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

    // 新增遊戲資料邏輯
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

    // 修改,更新遊戲資料邏輯
    public Game update(Integer gameId, GameRequest request) {

        Optional<Game> result = gameRepository.findById(gameId);

        if (result.isPresent()) {

            Game oldGame = result.get();

            // COMING_SOON 的遊戲修改發售時間時
            // 新的發售時間必須至少在 24 小時後
            if ("COMING_SOON".equals(oldGame.getStatus())) {

                if (request.getReleaseDate() == null) {
                    throw new IllegalArgumentException(
                            "即將發售的遊戲不能取消發售時間");
                }

                LocalDateTime minimumReleaseDate = LocalDateTime.now().plusHours(24);

                if (request.getReleaseDate().isBefore(minimumReleaseDate)) {
                    throw new IllegalArgumentException(
                            "遊戲發售時間必須至少設定在 24 小時後");
                }
            }

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

    // 預發售邏輯
    public Game comingSoon(Integer gameId) {

        Optional<Game> result = gameRepository.findById(gameId);

        if (result.isPresent()) {

            Game game = result.get();

            // 必須有設定發售時間
            if (game.getReleaseDate() == null) {
                throw new IllegalArgumentException("請先設定遊戲發售時間");
            }

            // 最早可以發售的時間 = 現在 + 24 小時
            LocalDateTime minimumReleaseDate = LocalDateTime.now().plusHours(24);

            // 發售時間少於 24 小時
            if (game.getReleaseDate().isBefore(minimumReleaseDate)) {
                throw new IllegalArgumentException(
                        "遊戲發售時間必須至少設定在 24 小時後");
            }

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

    // 自動下架遊戲(不使用for迴圈)
    @Transactional
    @Scheduled(fixedRate = 60000)
    public void autoOffShelfGames() {

        int updated = gameRepository.autoOffShelfGames();

        if (updated > 0) {
            System.out.println(
                    "自動下架完成，本次下架 "
                            + updated
                            + " 款遊戲");
        }
    }

    // 發行商送出遊戲審核
    public Game submitForReview(Integer gameId) {

        Optional<Game> result = gameRepository.findById(gameId);

        // 找不到遊戲
        if (result.isEmpty()) {
            return null;
        }

        Game game = result.get();

        // 只有草稿可以送審
        if (!"DRAFT".equals(game.getStatus())) {
            throw new IllegalStateException(
                    "只有草稿狀態的遊戲可以送出審核");
        }

        // 必須設定發售時間
        if (game.getReleaseDate() == null) {
            throw new IllegalStateException(
                    "請先設定遊戲發售時間");
        }

        game.setStatus("PENDING_REVIEW");

        return gameRepository.save(game);
    }

    // 發行商下架邏輯
    public Game requestOffShelf(Integer gameId) {

        Optional<Game> result = gameRepository.findById(gameId);

        if (result.isEmpty()) {
            return null;
        }

        Game game = result.get();

        if (!"PUBLISHED".equals(game.getStatus())) {
            throw new IllegalStateException(
                    "只有已發售的遊戲可以下架");
        }

        // 進入 24 小時反悔期
        game.setStatus("PENDING_OFF_SHELF");

        // 現在時間 + 24 小時
        game.setScheduledOffShelfAt(
                LocalDateTime.now().plusHours(24));

        return gameRepository.save(game);
    }

    // 取消下架
    public Game cancelOffShelf(Integer gameId) {

        Optional<Game> result = gameRepository.findById(gameId);

        if (result.isEmpty()) {
            return null;
        }

        Game game = result.get();

        if (!"PENDING_OFF_SHELF".equals(game.getStatus())) {
            throw new IllegalStateException(
                    "此遊戲目前不在下架反悔期");
        }

        game.setStatus("PUBLISHED");
        game.setScheduledOffShelfAt(null);

        return gameRepository.save(game);
    }

}