package gameplatform.game.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import gameplatform.game.entity.Game;
import gameplatform.game.repository.GameRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GameCoverUploadService {

    private final GameRepository gameRepository;

    @Value("${app.upload.cover-dir}")
    private String coverDir;

    private static final Pattern COVER_FILENAME = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                    + "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(jpg|png|gif)$");

    @Transactional
    public Game uploadCover(
            Integer gameId,
            Integer memberId,
            MultipartFile file) {

        // 1. 確認遊戲存在
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "找不到此遊戲"));

        // 2. 確認發行商擁有此遊戲
        if (!game.getMemberId().equals(memberId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "你沒有權限修改此遊戲的封面");
        }

        // 3. 檢查檔案
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "請選擇封面圖片");
        }

        if (file.getSize() > 10L * 1024 * 1024) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "封面圖片不可超過 10MB");
        }

        // 4. 檢查圖片格式
        String extension;

        try (InputStream input = file.getInputStream()) {

            byte[] header = input.readNBytes(8);

            if (isJpeg(header)) {
                extension = ".jpg";
            } else if (isPng(header)) {
                extension = ".png";
            } else if (isGif(header)) {
                extension = ".gif";
            } else {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "封面只允許 JPG、PNG、GIF");
            }

        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "無法讀取圖片");
        }

        // 記錄舊封面路徑，供後續清理
        String oldCoverUrl = game.getCoverUrl();

        // 5. 產生隨機檔名
        String filename = UUID.randomUUID() + extension;

        Path directory = Paths.get(coverDir)
                .toAbsolutePath()
                .normalize();

        Path target = directory.resolve(filename);

        // 6. 儲存實際圖片
        try {
            Files.createDirectories(directory);

            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target);
            }

        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "封面圖片儲存失敗");
        }

        // 7. 更新遊戲封面路徑
        String newCoverUrl = "/api/uploads/covers/" + filename;

        game.setCoverUrl(newCoverUrl);

        // 8. 取得舊封面的實際檔案路徑
        Path oldCoverPath = null;

        String prefix = "/api/uploads/covers/";

        if (oldCoverUrl != null
                && oldCoverUrl.startsWith(prefix)) {

            String oldFilename = oldCoverUrl.substring(prefix.length());

            if (COVER_FILENAME.matcher(oldFilename).matches()) {

                Path oldPath = directory
                        .resolve(oldFilename)
                        .normalize();

                if (oldPath.startsWith(directory)
                        && !oldPath.equals(target)) {
                    oldCoverPath = oldPath;
                }
            }
        }

        // 9. 註冊交易完成後的檔案清理
        Path finalOldCoverPath = oldCoverPath;

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {

                        // 資料庫成功提交後才刪除舊封面
                        if (finalOldCoverPath != null) {
                            try {
                                Files.deleteIfExists(finalOldCoverPath);
                            } catch (IOException e) {
                                System.err.println(
                                        "舊封面刪除失敗："
                                                + finalOldCoverPath);
                                e.printStackTrace();
                            }
                        }
                    }

                    @Override
                    public void afterCompletion(int status) {

                        // 資料庫交易未成功提交，清理新上傳的檔案
                        if (status != STATUS_COMMITTED) {
                            try {
                                Files.deleteIfExists(target);
                            } catch (IOException e) {
                                System.err.println(
                                        "新封面清理失敗：" + target);
                                e.printStackTrace();
                            }
                        }
                    }
                });

        // 10. 儲存資料庫
        return gameRepository.saveAndFlush(game);
    }

    private boolean isJpeg(byte[] b) {
        return b.length >= 3
                && (b[0] & 0xff) == 0xff
                && (b[1] & 0xff) == 0xd8
                && (b[2] & 0xff) == 0xff;
    }

    private boolean isPng(byte[] b) {
        return b.length >= 8
                && (b[0] & 0xff) == 0x89
                && b[1] == 0x50
                && b[2] == 0x4e
                && b[3] == 0x47
                && b[4] == 0x0d
                && b[5] == 0x0a
                && b[6] == 0x1a
                && b[7] == 0x0a;
    }

    private boolean isGif(byte[] b) {
        return b.length >= 6
                && b[0] == 'G'
                && b[1] == 'I'
                && b[2] == 'F'
                && b[3] == '8'
                && (b[4] == '7' || b[4] == '9')
                && b[5] == 'a';
    }
}