package gameplatform.game.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import gameplatform.game.entity.Game;
import gameplatform.game.service.GameService;

@RestController
@RequestMapping("/api/public/games")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;

    // 查詢全部遊戲
    @GetMapping
    public List<Game> findAll() {
        return gameService.findAll();
    }

    // 根據 gameId 查詢單一遊戲
    @GetMapping("/{gameId}")
    public ResponseEntity<Game> findById(@PathVariable Integer gameId) {

        Optional<Game> game = gameService.findById(gameId);

        if (game.isPresent()) {
            return ResponseEntity.ok(game.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/search")
    public List<Game> search(@RequestParam String keyword) {
        return gameService.findByGameName(keyword);
    }

}