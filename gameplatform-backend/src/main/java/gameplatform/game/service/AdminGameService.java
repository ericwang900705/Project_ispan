package gameplatform.game.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import gameplatform.game.entity.Game;
import gameplatform.game.repository.GameRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminGameService {

    private final GameRepository gameRepository;

    // 管理員查詢所有遊戲（包含分頁）
    public Page<Game> findAll(Pageable pageable) {

        return gameRepository.findAll(pageable);
    }

    // 管理員搜尋遊戲（名稱、狀態、分頁）
    public Page<Game> searchGames(
            String keyword,
            String status,
            Pageable pageable) {

        // 空字串轉成 null，代表不限制搜尋條件
        if (keyword != null && keyword.isBlank()) {
            keyword = null;
        }

        if (status != null && status.isBlank()) {
            status = null;
        }

        // 去除前後空白
        if (keyword != null) {
            keyword = keyword.trim();
        }

        if (status != null) {
            status = status.trim();
        }

        return gameRepository.searchAdminGames(
                keyword,
                status,
                pageable);
    }
}