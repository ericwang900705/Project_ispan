package gameplatform.game.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import gameplatform.game.dto.GameBuildRequest;
import gameplatform.game.entity.GameBuild;
import gameplatform.game.service.GameBuildService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/publisher/games")
@RequiredArgsConstructor
public class PublisherGameBuildController {

    private final GameBuildService gameBuildService;

    // 新增遊戲 Build
    @PostMapping("/{gameId}/builds")
    public ResponseEntity<GameBuild> insert(
            @PathVariable Integer gameId,
            @RequestBody GameBuildRequest request) {

        GameBuild result = gameBuildService.insert(gameId, request);

        if (result != null) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

}