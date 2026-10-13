package com.example.cart.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cart.model.Game;
import com.example.cart.service.GameService;

// 遊戲 API（暫代版本，正式版本由隊友負責）
// 所有 API 的共同路徑前綴為 /api/games
@RestController
@RequestMapping("/api/games")
public class GameController {
    private final GameService gameService;

    // 建構子注入：Spring 會自動把 GameService 的實例傳進來
    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    // 查詢全部遊戲
    // GET /api/games
    @GetMapping
    public ResponseEntity<List<Game>> getAllGames() {
        return new ResponseEntity<>(gameService.getAllGames(), HttpStatus.OK);
    }

    // 每款遊戲的平均評分、評論數、賣出數量
    // GET /api/games/stats
    // 要放在 /{id} 前面宣告比較好讀；Spring 會優先比對固定路徑，所以 stats 不會被當成 id
    @GetMapping("/stats")
    public ResponseEntity<List<GameService.GameStat>> getGameStats() {
        return new ResponseEntity<>(gameService.getGameStats(), HttpStatus.OK);
    }

    // 依 ID 查詢單一遊戲
    // GET /api/games/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Game> getGameById(@PathVariable Integer id) throws Exception {
        return new ResponseEntity<>(gameService.getGameById(id), HttpStatus.OK);
    }

    // 新增遊戲
    // POST /api/games
    @PostMapping
    public ResponseEntity<Game> addGame(@RequestBody Game game) {
        return new ResponseEntity<>(gameService.addGame(game), HttpStatus.CREATED);
    }

    // 批次新增遊戲
    // POST /api/games/batch，Body 是 JSON 陣列
    @PostMapping("/batch")
    public ResponseEntity<List<Game>> addGames(@RequestBody Game[] games) {
        List<Game> createdGames = new ArrayList<>();
        for (Game game : games) {
            createdGames.add(gameService.addGame(game));
        }
        return new ResponseEntity<>(createdGames, HttpStatus.CREATED);
    }

    // 刪除遊戲
    // DELETE /api/games/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteGame(@PathVariable Integer id) {
        return new ResponseEntity<>(gameService.deleteGame(id), HttpStatus.OK);
    }
}
