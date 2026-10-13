package com.example.cart.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.cart.model.Game;
import com.example.cart.repository.GameRepository;

// 遊戲的業務邏輯層（暫代版本，正式版本由隊友負責）
@Service
public class GameService {
    private final GameRepository gameRepository;

    // 建構子注入：Spring 會自動把 GameRepository 的實例傳進來
    public GameService(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    // 新增遊戲：save() 寫入資料庫，回傳的物件會帶有資料庫產生的 ID
    public Game addGame(Game game) {
        return gameRepository.save(game);
    }

    // 依 ID 刪除遊戲
    public String deleteGame(Integer id) {
        gameRepository.deleteById(id);
        return "Game deleted successfully";
    }

    // 依 ID 查詢遊戲：有資料就回傳，沒有就丟出例外
    public Game getGameById(Integer id) throws Exception {
        Optional<Game> opt = gameRepository.findById(id);
        if (opt.isPresent()) {
            return opt.get();
        }
        throw new Exception("找不到遊戲，gameId = " + id);
    }

    // 取得所有遊戲
    public List<Game> getAllGames() {
        return gameRepository.findAll();
    }

    // 每款遊戲的統計：平均評分、評論數、賣出數量（首頁「評價排行」「暢銷商品」用）
    public record GameStat(Integer gameId, double avgRating, long reviewCount, long soldCount) {
    }

    // 把評論統計和銷售統計合併成每款遊戲一筆；沒評論 / 沒賣出的遊戲數字就是 0
    public List<GameStat> getGameStats() {
        Map<Integer, Object[]> ratings = new HashMap<>();
        for (Object[] row : gameRepository.findRatingStats()) {
            ratings.put((Integer) row[0], row);
        }
        Map<Integer, Long> sales = new HashMap<>();
        for (Object[] row : gameRepository.findSalesStats()) {
            sales.put((Integer) row[0], ((Number) row[1]).longValue());
        }

        List<GameStat> stats = new ArrayList<>();
        for (Game game : gameRepository.findAll()) {
            Object[] rating = ratings.get(game.getGameId());
            double avg = rating == null ? 0 : ((Number) rating[1]).doubleValue();
            long count = rating == null ? 0 : ((Number) rating[2]).longValue();
            stats.add(new GameStat(game.getGameId(), avg, count, sales.getOrDefault(game.getGameId(), 0L)));
        }
        return stats;
    }
}
