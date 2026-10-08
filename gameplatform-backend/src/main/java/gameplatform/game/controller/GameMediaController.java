package gameplatform.game.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import gameplatform.game.entity.GameMedia;
import gameplatform.game.service.GameMediaService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameMediaController {

    private final GameMediaService gameMediaService;

    // 查詢指定遊戲的圖片與影片
    @GetMapping("/{gameId}/media")
    public List<GameMedia> findGameMedia(
            @PathVariable Integer gameId) {

        return gameMediaService.findGameMedia(gameId);
    }
}