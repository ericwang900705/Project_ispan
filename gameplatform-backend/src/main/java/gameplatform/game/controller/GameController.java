package gameplatform.game.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
    public Page<Game> findAll(
            @PageableDefault(size = 12, sort = "releaseDate", direction = Sort.Direction.DESC) Pageable pageable) {

        return gameService.findAll(pageable);
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

    // 根據關鍵字查詢遊戲
    @GetMapping("/search")
    public List<Game> search(@RequestParam String keyword) {
        return gameService.findByGameName(keyword);
    }

    // 根據標籤查詢遊戲
    @GetMapping("/search-by-tag")
    public Page<Game> findGamesByTag(
            @RequestParam Integer tagId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return gameService.findPublicGamesByTag(
                tagId,
                pageable);
    }

    // 根據多個標籤查詢遊戲
    @GetMapping("/search-by-tags")
    public Page<Game> findGamesByTags(
            @RequestParam List<Integer> tagIds,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return gameService.findPublicGamesByTags(
                tagIds,
                pageable);
    }

    // 根據價格範圍查詢遊戲
    @GetMapping("/advanced-search")
    public Page<Game> advancedSearch(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<Integer> tagIds,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String releaseStatus,
            @RequestParam(required = false) Boolean freeOnly,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate releaseDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate releaseDateTo,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        // 只允許價格與發售日期排序
        Set<String> allowedSortFields = Set.of(
                "price",
                "releaseDate");

        // 驗證排序欄位
        for (Sort.Order order : pageable.getSort()) {

            if (!allowedSortFields.contains(order.getProperty())) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "只允許依價格或發售日期排序");
            }
        }

        return gameService.advancedSearch(
                keyword,
                tagIds,
                minPrice,
                maxPrice,
                releaseStatus,
                freeOnly,
                releaseDateFrom,
                releaseDateTo,
                pageable);
    }

}