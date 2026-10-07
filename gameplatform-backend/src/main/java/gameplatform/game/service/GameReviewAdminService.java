package gameplatform.game.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

import gameplatform.game.entity.Game;
import gameplatform.game.entity.GameAuditLog;
import gameplatform.game.repository.GameAuditLogRepository;
import gameplatform.game.repository.GameBuildRepository;
import gameplatform.game.repository.GameRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GameReviewAdminService {

    private final GameRepository gameRepository;
    private final GameAuditLogRepository gameAuditLogRepository;
    private final GameBuildRepository gameBuildRepository;

    // 管理員核准遊戲上架
    @Transactional
    public Game approve(
            Integer gameId,
            Integer adminId,
            String comment) {

        Optional<Game> result = gameRepository.findById(gameId);

        // 找不到遊戲
        if (result.isEmpty()) {
            return null;
        }

        Game game = result.get();

        // 只有待審核的遊戲才能核准
        if (!"PENDING_REVIEW".equals(game.getStatus())) {
            throw new IllegalStateException(
                    "只有待審核的遊戲可以核准");
        }

        // 必須設定發售時間
        if (game.getReleaseDate() == null) {
            throw new IllegalStateException("遊戲尚未設定發售時間");
        }

        // 至少 24 小時後
        LocalDateTime minimumReleaseDate = LocalDateTime.now().plusHours(24);

        if (game.getReleaseDate().isBefore(minimumReleaseDate)) {
            throw new IllegalStateException("遊戲發售時間必須至少在審核通過的 24 小時後");
        }

        // 必須有可用的遊戲程式
        boolean hasActiveBuild = gameBuildRepository.existsByGameIdAndStatus(gameId, "ACTIVE");

        if (!hasActiveBuild) {
            throw new IllegalStateException("遊戲尚未上傳可使用的遊戲程式");
        }

        // 全部通過才進入 COMING_SOON
        game.setStatus("COMING_SOON");

        Game savedGame = gameRepository.save(game);

        // 建立審核紀錄
        GameAuditLog log = new GameAuditLog();

        log.setGameId(gameId);
        log.setAdminId(adminId);
        log.setReviewType("PUBLISH");
        log.setDecision("APPROVED");
        log.setComment(comment);

        gameAuditLogRepository.save(log);

        return savedGame;
    }

    @Transactional
    public Game reject(
            Integer gameId,
            Integer adminId,
            String comment) {

        Optional<Game> result = gameRepository.findById(gameId);

        // 找不到遊戲
        if (result.isEmpty()) {
            return null;
        }

        Game game = result.get();

        // 只有待審核狀態才能拒絕
        if (!"PENDING_REVIEW".equals(game.getStatus())) {
            throw new IllegalStateException(
                    "只有待審核的遊戲可以拒絕");
        }

        // 拒絕後回到草稿
        game.setStatus("DRAFT");

        Game savedGame = gameRepository.save(game);

        // 建立審核紀錄
        GameAuditLog log = new GameAuditLog();

        log.setGameId(gameId);
        log.setAdminId(adminId);
        log.setReviewType("PUBLISH");
        log.setDecision("REJECTED");
        log.setComment(comment);

        gameAuditLogRepository.save(log);

        return savedGame;
    }

    // 管理員核准遊戲下架
    @Transactional
    public Game approveOffShelf(
            Integer gameId,
            Integer adminId,
            String comment) {

        Optional<Game> result = gameRepository.findById(gameId);

        if (result.isEmpty()) {
            return null;
        }

        Game game = result.get();

        // 只有等待下架審核的遊戲才能核准
        if (!"PENDING_OFF_SHELF".equals(game.getStatus())) {
            throw new IllegalStateException(
                    "此遊戲目前不是等待下架審核狀態");
        }

        // 正式下架
        game.setStatus("OFF_SHELF");

        Game savedGame = gameRepository.save(game);

        // 寫入審核紀錄
        GameAuditLog log = new GameAuditLog();

        log.setGameId(gameId);
        log.setAdminId(adminId);
        log.setReviewType("OFF_SHELF");
        log.setDecision("APPROVED");
        log.setComment(comment);

        gameAuditLogRepository.save(log);

        return savedGame;
    }

}