package gameplatform.support.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;

/** Public game artwork, separate from private customer-support attachments. */
@Service
public class PublisherImageStorage {
    private static final String PREFIX = "/uploads/publisher/";
    private static final String NAME = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg)";
    private final Path directory;

    public PublisherImageStorage(@Value("${app.publisher-upload-dir:./uploads/publisher}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public static boolean isStoredUrl(String url) {
        return url != null && url.startsWith(PREFIX) && url.substring(PREFIX.length()).matches(NAME);
    }

    public Map<String,Object> upload(Actor actor, MultipartFile file) {
        if (actor == null || !"PUBLISHER".equals(actor.role())) throw new Problem(403,"只有發行商可以上傳遊戲圖片");
        if (file.isEmpty() || file.getSize() > SupportImageValidator.MAX_BYTES) throw new Problem(400,"每張圖片不可超過 5 MB");
        try {
            var image = SupportImageValidator.validate(file.getBytes(),file.getOriginalFilename());
            String name = UUID.randomUUID() + ("image/png".equals(image.contentType()) ? ".png" : ".jpg");
            Files.createDirectories(directory);
            Files.write(directory.resolve(name),image.bytes(),StandardOpenOption.CREATE_NEW);
            return Map.of("url",PREFIX+name,"width",image.width(),"height",image.height());
        } catch (IllegalArgumentException e) {
            throw new Problem(400,e.getMessage());
        } catch (IOException e) {
            throw new Problem(500,"圖片儲存失敗，請確認伺服器上傳目錄可寫入");
        }
    }

    public Path resolve(String name) {
        if (!name.matches(NAME)) throw new Problem(404,"找不到圖片");
        Path file = directory.resolve(name);
        if (!Files.isRegularFile(file)) throw new Problem(404,"找不到圖片");
        return file;
    }
}
