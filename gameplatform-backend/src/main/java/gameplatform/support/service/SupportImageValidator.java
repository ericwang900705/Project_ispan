package gameplatform.support.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Decode and re-encode supported raster images before saving; never trust file extensions or MIME headers. */
public final class SupportImageValidator {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final int MAX_FILES = 3;
    private static final int MAX_SIDE = 4096;
    private static final long MAX_PIXELS = 12_000_000;

    private SupportImageValidator() {
    }

    public record Image(String fileName, String contentType, int width, int height, byte[] bytes) {
    }

    public static Image validate(byte[] bytes, String originalName) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("每張圖片須介於 1 byte 與 5 MB 之間。");
        }
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("請上傳有效的 JPG 或 PNG 圖片。");
            }
            var reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("jpg") && !format.equals("png")) {
                    throw new IllegalArgumentException("目前只支援 JPG、PNG 圖片。");
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_SIDE || height > MAX_SIDE
                        || (long) width * height > MAX_PIXELS) {
                    throw new IllegalArgumentException("圖片每邊最多 4096 像素，總像素最多 1200 萬；請縮小後再上傳。");
                }
                var decoded = reader.read(0);
                var output = new ByteArrayOutputStream();
                String extension = format.equals("png") ? "png" : "jpg";
                if (!ImageIO.write(decoded, extension, output)) {
                    throw new IllegalArgumentException("無法處理此圖片，請轉存為 JPG 或 PNG 後再試。");
                }
                byte[] clean = output.toByteArray();
                if (clean.length > MAX_BYTES) {
                    throw new IllegalArgumentException("圖片處理後超過 5 MB，請縮小後再上傳。");
                }
                // Do not retain uploaded paths or control characters; extension follows decoded format.
                String name = originalName == null ? "image" : originalName.replace('\\', '/');
                name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
                int dot = name.lastIndexOf('.');
                if (dot > 0) name = name.substring(0, dot);
                if (name.isBlank() || name.equals(".") || name.equals("..")) name = "image";
                if (name.length() > 100) name = name.substring(0, 100);
                return new Image(name + "." + extension, extension.equals("png") ? "image/png" : "image/jpeg",
                        width, height, clean);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("圖片已損壞或無法讀取，請重新選擇。", e);
        }
    }
}
