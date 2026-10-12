package gameplatform.game.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import gameplatform.game.dto.GameMediaRequest;
import gameplatform.game.entity.Game;
import gameplatform.game.entity.GameMedia;
import gameplatform.game.repository.GameMediaRepository;
import gameplatform.game.repository.GameRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GameMediaService {

        private final GameMediaRepository gameMediaRepository;
        private final GameRepository gameRepository;

        //
        @Value("${app.upload.game-dir}")
        private String uploadDir;

        private static final Pattern UPLOADED_FILENAME = Pattern.compile(
                        "^[0-9a-fA-F-]{36}\\.(jpg|png|gif|mp4)$");

        // 查詢指定遊戲的圖片與影片
        public List<GameMedia> findGameMedia(Integer gameId) {

                // 1. 確認遊戲存在
                Game game = gameRepository.findById(gameId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "找不到此遊戲"));

                // 2. 只有公開狀態的遊戲可以透過此 API 查詢媒體
                String status = game.getStatus();

                if (!status.equals("COMING_SOON")
                                && !status.equals("PUBLISHED")
                                && !status.equals("PENDING_OFF_SHELF")) {

                        throw new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "找不到此遊戲");
                }

                // 3. 查詢遊戲媒體
                return gameMediaRepository
                                .findByGameIdOrderByDisplayOrderAsc(gameId);
        }

        // 發行商新增遊戲圖片或影片資料
        @Transactional
        public GameMedia addGameMedia(
                        Integer gameId,
                        Integer memberId,
                        GameMediaRequest request) {

                // 1. 確認遊戲存在
                Game game = gameRepository.findById(gameId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "找不到此遊戲"));

                // 2. 確認是否為遊戲擁有者
                if (!game.getMemberId().equals(memberId)) {
                        throw new ResponseStatusException(
                                        HttpStatus.FORBIDDEN,
                                        "你沒有權限新增此遊戲的媒體");
                }

                // 3. 檢查圖片或影片類型
                String mediaType = request.getMediaType();

                if (mediaType == null ||
                                (!mediaType.equals("IMAGE")
                                                && !mediaType.equals("VIDEO"))) {

                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "媒體類型只能是 IMAGE 或 VIDEO");
                }

                // 4. 檢查媒體路徑
                String mediaUrl = request.getMediaUrl();

                if (mediaUrl == null || mediaUrl.isBlank()) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "請提供媒體路徑");
                }

                mediaUrl = mediaUrl.trim();

                if (mediaUrl.length() > 500
                                || !mediaUrl.startsWith("/")
                                || mediaUrl.startsWith("//")
                                || mediaUrl.contains("..")
                                || mediaUrl.contains("\\")
                                || mediaUrl.contains("?")
                                || mediaUrl.contains("#")) {

                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "媒體路徑格式不正確");
                }

                // 5. 檢查展示順序
                Integer displayOrder = request.getDisplayOrder();

                if (displayOrder == null || displayOrder < 1) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "展示順序必須大於 0");
                }

                // 6. 建立媒體資料
                GameMedia media = new GameMedia();

                media.setGameId(gameId);
                media.setMediaType(mediaType);
                media.setMediaUrl(mediaUrl);
                media.setDisplayOrder(displayOrder);

                // 7. 儲存到資料庫
                return gameMediaRepository.save(media);
        }

        @Transactional
        public GameMedia updateGameMedia(
                        Integer gameId,
                        Integer mediaId,
                        Integer memberId,
                        GameMediaRequest request) {

                // 1. 確認遊戲存在
                Game game = gameRepository.findById(gameId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "找不到此遊戲"));

                // 2. 確認遊戲擁有者
                if (!game.getMemberId().equals(memberId)) {
                        throw new ResponseStatusException(
                                        HttpStatus.FORBIDDEN,
                                        "你沒有權限修改此遊戲的媒體");
                }

                // 3. 查詢媒體
                GameMedia media = gameMediaRepository.findById(mediaId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "找不到此媒體"));

                // 4. 確認媒體屬於指定遊戲
                if (!media.getGameId().equals(gameId)) {
                        throw new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "此媒體不屬於指定遊戲");
                }

                // 5. 驗證媒體類型
                String mediaType = request.getMediaType();

                if (mediaType == null ||
                                (!mediaType.equals("IMAGE")
                                                && !mediaType.equals("VIDEO"))) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "媒體類型只能是 IMAGE 或 VIDEO");
                }

                // 6. 驗證媒體路徑
                String mediaUrl = request.getMediaUrl();

                if (mediaUrl == null || mediaUrl.isBlank()) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "請提供媒體路徑");
                }

                mediaUrl = mediaUrl.trim();

                if (mediaUrl.length() > 500
                                || !mediaUrl.startsWith("/")
                                || mediaUrl.startsWith("//")
                                || mediaUrl.contains("..")
                                || mediaUrl.contains("\\")
                                || mediaUrl.contains("?")
                                || mediaUrl.contains("#")) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "媒體路徑格式不正確");
                }

                // 7. 驗證展示順序
                Integer displayOrder = request.getDisplayOrder();

                if (displayOrder == null || displayOrder < 1) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "展示順序必須大於 0");
                }

                // 8. 更新媒體資料
                media.setMediaType(mediaType);
                media.setMediaUrl(mediaUrl);
                media.setDisplayOrder(displayOrder);

                // 9. 儲存
                return gameMediaRepository.save(media);
        }

        // 刪除遊戲媒體
        @Transactional
        public void deleteGameMedia(
                        Integer gameId,
                        Integer mediaId,
                        Integer memberId) {

                // 1. 確認遊戲存在
                Game game = gameRepository.findById(gameId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "找不到此遊戲"));

                // 2. 確認遊戲擁有者
                if (!game.getMemberId().equals(memberId)) {
                        throw new ResponseStatusException(
                                        HttpStatus.FORBIDDEN,
                                        "你沒有權限刪除此遊戲的媒體");
                }

                // 3. 確認媒體存在
                GameMedia media = gameMediaRepository.findById(mediaId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "找不到此媒體"));

                // 4. 確認媒體屬於指定遊戲
                if (!media.getGameId().equals(gameId)) {
                        throw new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "此媒體不屬於指定遊戲");
                }

                // 5. 取得原本的媒體路徑
                String mediaUrl = media.getMediaUrl();

                String prefix = "/api/uploads/games/";

                Path target = null;

                // 只處理系統上傳的本機檔案
                if (mediaUrl != null && mediaUrl.startsWith(prefix)) {

                        String filename = mediaUrl.substring(prefix.length());

                        if (UPLOADED_FILENAME.matcher(filename).matches()) {

                                Path directory = Paths.get(uploadDir)
                                                .toAbsolutePath()
                                                .normalize();

                                Path filePath = directory.resolve(filename).normalize();

                                if (filePath.startsWith(directory)) {
                                        target = filePath;
                                }
                        }
                }

                // 6. 刪除資料庫紀錄
                gameMediaRepository.delete(media);

                // 7. 資料庫提交成功後，再刪除實際檔案
                if (target != null) {

                        Path fileToDelete = target;

                        TransactionSynchronizationManager.registerSynchronization(
                                        new TransactionSynchronization() {

                                                @Override
                                                public void afterCommit() {
                                                        try {
                                                                Files.deleteIfExists(fileToDelete);
                                                        } catch (IOException e) {
                                                                // 正式環境建議改用 Logger
                                                                System.err.println(
                                                                                "媒體檔案刪除失敗：" + fileToDelete);
                                                                e.printStackTrace();
                                                        }
                                                }
                                        });
                }
        }
}