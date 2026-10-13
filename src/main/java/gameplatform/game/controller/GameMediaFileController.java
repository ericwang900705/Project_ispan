package gameplatform.game.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/uploads/games")
public class GameMediaFileController {

    @Value("${app.upload.game-dir}")
    private String uploadDir;

    private static final Pattern SAFE_FILENAME = Pattern.compile(
            "^[0-9a-fA-F-]{36}\\.(jpg|png|gif|mp4)$");

    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> getGameMediaFile(
            @PathVariable String filename) {

        // 1. 驗證檔案名稱
        if (!SAFE_FILENAME.matcher(filename).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "檔案名稱格式不正確");
        }

        // 2. 取得檔案路徑
        Path directory = Paths.get(uploadDir)
                .toAbsolutePath()
                .normalize();

        Path filePath = directory.resolve(filename).normalize();

        // 3. 確認檔案在指定目錄內
        if (!filePath.startsWith(directory)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "不合法的檔案路徑");
        }

        // 4. 確認檔案存在
        if (!Files.isRegularFile(filePath)
                || !Files.isReadable(filePath)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "找不到此檔案");
        }

        try {
            // 5. 讀取檔案
            Resource resource = new UrlResource(filePath.toUri());

            // 6. 判斷回傳類型
            MediaType mediaType;

            if (filename.endsWith(".png")) {
                mediaType = MediaType.IMAGE_PNG;
            } else if (filename.endsWith(".jpg")) {
                mediaType = MediaType.IMAGE_JPEG;
            } else if (filename.endsWith(".gif")) {
                mediaType = MediaType.parseMediaType("image/gif");
            } else {
                mediaType = MediaType.parseMediaType("video/mp4");
            }

            // 7. 回傳檔案
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "inline")
                    .body(resource);

        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "讀取檔案失敗");
        }
    }
}