package gameplatform.game.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Sort;

import gameplatform.game.dto.GameMediaRequest;
import gameplatform.game.dto.GameRequest;
import gameplatform.game.dto.GameTagRequest;
import gameplatform.game.entity.Game;
import gameplatform.game.entity.GameAuditLog;
import gameplatform.game.entity.GameMedia;
import gameplatform.game.entity.Tag;
import gameplatform.game.service.GameMediaService;
import gameplatform.game.service.GameMediaUploadService;
import gameplatform.game.service.GameService;
import gameplatform.game.service.GameTagService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/publisher/games")
@RequiredArgsConstructor
public class PublisherGameController {

    private final GameService gameService;
    private final GameTagService gameTagService;
    private final GameMediaService gameMediaService;
    private final GameMediaUploadService gameMediaUploadService;

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

    // 送出遊戲審核
    @PutMapping("/{gameId}/submit-review")
    public ResponseEntity<Game> submitForReview(
            @PathVariable Integer gameId) {

        Game result = gameService.submitForReview(gameId);

        if (result != null) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // 發行商下架遊戲
    @PutMapping("/{gameId}/request-off-shelf")
    public ResponseEntity<Game> requestOffShelf(
            @PathVariable Integer gameId) {

        Game result = gameService.requestOffShelf(gameId);

        if (result != null) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // 發行商取消下架
    @PutMapping("/{gameId}/cancel-off-shelf")
    public ResponseEntity<Game> cancelOffShelf(
            @PathVariable Integer gameId) {

        Game result = gameService.cancelOffShelf(gameId);

        if (result != null) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // 發行商查詢自己遊戲
    @GetMapping("/my-games")
    public Page<Game> findMyGames(
            @RequestParam Integer memberId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return gameService.findMyGames(memberId, pageable);
    }

    // 發行商查詢自己遊戲的審核歷史
    @GetMapping("/{gameId}/audit-history")
    public ResponseEntity<List<GameAuditLog>> findMyGameAuditHistory(
            @PathVariable Integer gameId,
            @RequestParam Integer memberId) {

        List<GameAuditLog> logs = gameService.findMyGameAuditHistory(
                gameId,
                memberId);

        return ResponseEntity.ok(logs);
    }

    // 發行商設定自己遊戲的標籤
    @PutMapping("/{gameId}/tags")
    public ResponseEntity<List<Tag>> updateGameTags(
            @PathVariable Integer gameId,
            @RequestParam Integer memberId,
            @RequestBody GameTagRequest request) {

        List<Tag> tags = gameTagService.updateGameTags(
                gameId,
                memberId,
                request.getTagIds());

        return ResponseEntity.ok(tags);
    }

    // 發行商新增遊戲標籤
    @PostMapping("/{gameId}/tags")
    public ResponseEntity<List<Tag>> addGameTags(
            @PathVariable Integer gameId,
            @RequestParam Integer memberId,
            @RequestBody GameTagRequest request) {

        List<Tag> tags = gameTagService.addGameTags(
                gameId,
                memberId,
                request.getTagIds());

        return ResponseEntity.ok(tags);
    }

    // 發行商刪除指定遊戲標籤
    @DeleteMapping("/{gameId}/tags/{tagId}")
    public ResponseEntity<List<Tag>> deleteGameTag(
            @PathVariable Integer gameId,
            @PathVariable Integer tagId,
            @RequestParam Integer memberId) {

        List<Tag> tags = gameTagService.deleteGameTag(
                gameId,
                memberId,
                tagId);

        return ResponseEntity.ok(tags);
    }

    // 發行商新增遊戲圖片或影片
    @PostMapping("/{gameId}/media")
    public ResponseEntity<GameMedia> addGameMedia(
            @PathVariable Integer gameId,
            @RequestParam Integer memberId,
            @RequestBody GameMediaRequest request) {

        GameMedia media = gameMediaService.addGameMedia(
                gameId,
                memberId,
                request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(media);
    }

    // 發行商修改遊戲媒體
    @PutMapping("/{gameId}/media/{mediaId}")
    public ResponseEntity<GameMedia> updateGameMedia(
            @PathVariable Integer gameId,
            @PathVariable Integer mediaId,
            @RequestParam Integer memberId,
            @RequestBody GameMediaRequest request) {

        GameMedia media = gameMediaService.updateGameMedia(
                gameId,
                mediaId,
                memberId,
                request);

        return ResponseEntity.ok(media);
    }

    // 發行商刪除遊戲媒體
    @DeleteMapping("/{gameId}/media/{mediaId}")
    public ResponseEntity<Void> deleteGameMedia(
            @PathVariable Integer gameId,
            @PathVariable Integer mediaId,
            @RequestParam Integer memberId) {

        gameMediaService.deleteGameMedia(
                gameId,
                mediaId,
                memberId);

        return ResponseEntity.noContent().build();
    }

    // 發行商上傳遊戲圖片或影片
    @PostMapping(value = "/{gameId}/media/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GameMedia> uploadGameMedia(
            @PathVariable Integer gameId,
            @RequestParam Integer memberId,
            @RequestParam("file") MultipartFile file,
            @RequestParam Integer displayOrder) {

        GameMedia media = gameMediaUploadService.uploadGameMedia(
                gameId,
                memberId,
                file,
                displayOrder);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(media);
    }
}