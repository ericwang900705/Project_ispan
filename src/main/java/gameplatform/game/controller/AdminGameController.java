package gameplatform.game.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gameplatform.game.entity.Game;
import gameplatform.game.service.AdminGameService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/games")
@RequiredArgsConstructor
public class AdminGameController {

    private final AdminGameService adminGameService;

    @GetMapping
    public Page<Game> findAll(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return adminGameService.findAll(pageable);
    }

    // 管理員搜尋遊戲（名稱、狀態、分頁）
    @GetMapping("/search")
    public Page<Game> searchGames(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return adminGameService.searchGames(
                keyword,
                status,
                pageable);
    }
}