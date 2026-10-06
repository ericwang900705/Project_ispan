package gameplatform.game.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import gameplatform.game.dto.GameRequest;
import gameplatform.game.entity.Game;
import gameplatform.game.service.GameService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/publisher/games")
@RequiredArgsConstructor
public class PublisherGameController {

    private final GameService gameService;

    // 新增遊戲
    @PostMapping
    public Game insert(@RequestBody GameRequest request) {
        return gameService.insert(request);
    }

    // 修改遊戲
    @PutMapping("/{gameId}")
    public ResponseEntity<Game> update(
            @PathVariable Integer gameId,
            @RequestBody GameRequest request) {

        Game result = gameService.update(gameId, request);

        if (result != null) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // 上架遊戲
    @PutMapping("/{gameId}/publish")
    public ResponseEntity<Game> publish(@PathVariable Integer gameId) {

        Game result = gameService.publish(gameId);

        if (result != null) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // 下架遊戲
    @PutMapping("/{gameId}/off-shelf")
    public ResponseEntity<Game> offShelf(@PathVariable Integer gameId) {

        Game result = gameService.offShelf(gameId);

        if (result != null) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // 設定為即將發售
    @PutMapping("/{gameId}/coming-soon")
    public ResponseEntity<Game> comingSoon(@PathVariable Integer gameId) {

        Game result = gameService.comingSoon(gameId);

        if (result != null) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}