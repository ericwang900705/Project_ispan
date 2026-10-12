package gameplatform.game.controller;

import java.net.MalformedURLException;
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
@RequestMapping("/api/uploads/covers")
public class GameCoverFileController {

    @Value("${app.upload.cover-dir}")
    private String coverDir;

    // 只允許系統產生的 UUID 圖片檔名
    private static final Pattern SAFE_FILENAME = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                    + "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(jpg|png|gif)$");

    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> getGameCover(
            @PathVariable String filename) {

        // 1. 檢查檔名是否合法
        if (!SAFE_FILENAME.matcher(filename).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "檔案名稱格式不正確");
        }

        // 2. 取得封面資料夾位置
        Path directory = Paths.get(coverDir)
                .toAbsolutePath()
                .normalize();

        Path filePath = directory.resolve(filename).normalize();

        // 3. 防止存取資料夾外的檔案
        if (!filePath.startsWith(directory)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "不合法的檔案路徑");
        }

        // 4. 確認檔案存在且可以讀取
        if (!Files.isRegularFile(filePath)
                || !Files.isReadable(filePath)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "找不到此封面圖片");
        }

        try {
            // 5. 取得圖片資源
            Resource resource = new UrlResource(filePath.toUri());

            // 6. 設定圖片格式
            MediaType mediaType;

            if (filename.endsWith(".png")) {
                mediaType = MediaType.IMAGE_PNG;
            } else if (filename.endsWith(".jpg")) {
                mediaType = MediaType.IMAGE_JPEG;
            } else {
                mediaType = MediaType.parseMediaType("image/gif");
            }

            // 7. 回傳圖片
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "inline")
                    .body(resource);

        } catch (MalformedURLException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "讀取封面圖片失敗");
        }
    }
}