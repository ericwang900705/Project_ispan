package gameplatform.game.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import gameplatform.game.entity.Tag;
import gameplatform.game.service.GameTagService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameTagController {

    private final GameTagService gameTagService;

    // 查詢全部遊戲標籤
    @GetMapping("/tags")
    public List<Tag> findAllTags() {
        return gameTagService.findAllTags();
    }

    // 查詢指定遊戲的標籤
    @GetMapping("/{gameId}/tags")
    public List<Tag> findGameTags(
            @PathVariable Integer gameId) {

        return gameTagService.findGameTags(gameId);
    }
}