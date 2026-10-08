package gameplatform.game.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import gameplatform.game.entity.Game;
import gameplatform.game.entity.GameMedia;
import gameplatform.game.repository.GameMediaRepository;
import gameplatform.game.repository.GameRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GameMediaUploadService {

    private final GameRepository gameRepository;
    private final GameMediaRepository gameMediaRepository;

    @Value("${app.upload.game-dir}")
    private String uploadDir;

    @Transactional
    public GameMedia uploadGameMedia(
            Integer gameId,
            Integer memberId,
            MultipartFile file,
            Integer displayOrder) {

        // 1. 確認遊戲存在
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "找不到此遊戲"));

        // 2. 確認發行商擁有此遊戲
        if (!game.getMemberId().equals(memberId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "你沒有權限上傳此遊戲的媒體");
        }

        // 3. 檢查檔案
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "請選擇要上傳的檔案");
        }

        if (displayOrder == null || displayOrder < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "展示順序必須大於 0");
        }

        // 4. 依檔案內容判斷允許的圖片類型
        // 本階段先支援 JPG、PNG、GIF
        String extension;
        String mediaType;
        long maxSize;

        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(12);

            if (isJpeg(header)) {
                extension = ".jpg";
                mediaType = "IMAGE";
                maxSize = 10L * 1024 * 1024;

            } else if (isPng(header)) {
                extension = ".png";
                mediaType = "IMAGE";
                maxSize = 10L * 1024 * 1024;

            } else if (isGif(header)) {
                extension = ".gif";
                mediaType = "IMAGE";
                maxSize = 10L * 1024 * 1024;

            } else if (isMp4(header)) {
                extension = ".mp4";
                mediaType = "VIDEO";
                maxSize = 100L * 1024 * 1024;

            } else {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "只允許 JPG、PNG、GIF 或 MP4 檔案");
            }

        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "無法讀取上傳檔案");
        }

        if (file.getSize() > maxSize) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "檔案超過允許大小");
        }

        // 5. 使用隨機檔名，避免名稱衝突
        String filename = UUID.randomUUID() + extension;

        Path directory = Paths.get(uploadDir)
                .toAbsolutePath()
                .normalize();

        Path target = directory.resolve(filename);

        // 6. 儲存實際檔案
        try {
            Files.createDirectories(directory);

            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target);
            }

        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "檔案儲存失敗");
        }

        // 7. 儲存 SQL Server 紀錄
        GameMedia media = new GameMedia();

        media.setGameId(gameId);
        media.setMediaType(mediaType);
        media.setMediaUrl("/api/uploads/games/" + filename);
        media.setDisplayOrder(displayOrder);

        try {
            return gameMediaRepository.saveAndFlush(media);
        } catch (RuntimeException e) {
            // 資料庫失敗時，清理已上傳檔案
            try {
                Files.deleteIfExists(target);
            } catch (IOException cleanupError) {
                e.addSuppressed(cleanupError);
            }
            throw e;
        }
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

    private boolean isMp4(byte[] b) {
        return b.length >= 12
                && b[4] == 'f'
                && b[5] == 't'
                && b[6] == 'y'
                && b[7] == 'p';
    }
}