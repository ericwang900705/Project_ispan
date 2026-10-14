package gameplatform.game.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import jakarta.transaction.Transactional;

import gameplatform.game.entity.Game;
import gameplatform.game.repository.GameRepository;
import lombok.RequiredArgsConstructor;

/** Member storefront read API. Publisher writes and reviews belong to HERO support services. */
@Service
@RequiredArgsConstructor
public class GameService {
    private static final List<String> VISIBLE_STATUSES = List.of("COMING_SOON", "PUBLISHED");

    private final GameRepository gameRepository;

    public Page<Game> findAll(Pageable pageable) {
        return gameRepository.findByStatusIn(VISIBLE_STATUSES, pageable);
    }

    public List<Game> findByGameName(String keyword) {
        return gameRepository.findByGameNameContainingAndStatusIn(keyword, VISIBLE_STATUSES);
    }

    public Optional<Game> findById(Integer gameId) {
        return gameRepository.findByGameIdAndStatusIn(gameId, VISIBLE_STATUSES);
    }
    /** Release an approved upcoming game at its configured local database time. */
    @Transactional
    @Scheduled(initialDelay = 5000, fixedDelay = 60000)
    public void publishDueGames() {
        gameRepository.publishDueGames();
    }
}
