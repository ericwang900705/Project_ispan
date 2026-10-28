package gameplatform.support.service;

import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 發行商 MP4 宣傳影片與 ZIP 版本檔案；隨機檔名且不解壓縮使用者的 ZIP。 */
@Service
public class PublisherFileStorage {
    public static final long MAX_BYTES = 100L * 1024 * 1024;
    private static final String UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private final Path directory;

    public PublisherFileStorage(@Value("${app.publisher-file-dir:./publisher-files}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public static boolean isVideoUrl(String url) {
        return url != null && url.matches("/uploads/publisher-files/videos/[1-9][0-9]*/" + UUID + "\\.mp4");
    }

    public static boolean isBuildUrl(String url, int owner) {
        return url != null && url.matches("/api/publisher-assets/builds/" + owner + "/" + UUID + "\\.zip");
    }

    public Map<String, Object> upload(Actor actor, MultipartFile file, String kind) {
        if (actor == null || !"PUBLISHER".equals(actor.role()))
            throw new Problem(403, "只有發行商可以上傳遊戲檔案");
        if (!Set.of("VIDEO", "BUILD").contains(kind))
            throw new Problem(400, "不支援的檔案類型");
        boolean video = "VIDEO".equals(kind);
        String extension = video ? ".mp4" : ".zip";
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES)
            throw new Problem(400, "檔案不可為空，每個檔案最多 100 MB");
        if (file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(extension))
            throw new Problem(400, video ? "請選擇 MP4 影片" : "請選擇 ZIP 壓縮檔");
        String folder = video ? "videos" : "builds";
        String name = java.util.UUID.randomUUID() + extension;
        Path target = directory.resolve(folder).resolve(String.valueOf(actor.id())).resolve(name);
        try {
            Files.createDirectories(target.getParent());
            try (var input = file.getInputStream()) {
                Files.copy(input, target);
            }
            if (video) {
                byte[] head;
                try (var input = Files.newInputStream(target)) {
                    head = input.readNBytes(12);
                }
                if (head.length < 12 || head[4] != 'f' || head[5] != 't' || head[6] != 'y' || head[7] != 'p')
                    throw new Problem(400, "影片內容不是有效的 MP4 檔案");
            } else {
                try (var zip = new ZipFile(target.toFile())) {
                    if (zip.size() == 0)
                        throw new Problem(400, "ZIP 不可為空");
                } catch (java.util.zip.ZipException e) {
                    throw new Problem(400, "ZIP 壓縮檔已損壞或格式不正確");
                }
            }
            String prefix = video ? "/uploads/publisher-files/videos/" : "/api/publisher-assets/builds/";
            return Map.of("url", prefix + actor.id() + "/" + name, "fileSize", file.getSize(), "type",
                    video ? "VIDEO" : "BUILD");
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
            }
            if (e instanceof Problem problem)
                throw problem;
            throw new Problem(500, "檔案儲存失敗，請確認上傳目錄可寫入");
        }
    }

    public Path resolve(String folder, int owner, String name) {
        String suffix = folder.equals("videos") ? "mp4" : "zip";
        if (owner <= 0 || !Set.of("videos", "builds").contains(folder) || !name.matches(UUID + "\\." + suffix))
            throw new Problem(404, "找不到檔案");
        Path path = directory.resolve(folder).resolve(String.valueOf(owner)).resolve(name);
        if (!Files.isRegularFile(path))
            throw new Problem(404, "找不到檔案");
        return path;
    }
}
