package gameplatform.support;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;
import gameplatform.support.service.PublisherImageStorage;

class PublisherImageStorageTest {
    @TempDir Path directory;
    private MockMultipartFile image() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(12, 8, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return new MockMultipartFile("file", "C:\\Users\\aki\\Downloads\\cover.png", "image/png", bytes.toByteArray());
    }
    @Test void persistsImageAcrossStorageInstancesAndIgnoresClientPath() throws Exception {
        var storage = new PublisherImageStorage(directory.toString());
        var result = storage.upload(new Actor(3,"PUBLISHER","publisher1",""), image());
        String url = result.get("url").toString();
        assertTrue(PublisherImageStorage.isStoredUrl(url));
        var restored = new PublisherImageStorage(directory.toString());
        var file = restored.resolve(url.substring(url.lastIndexOf('/') + 1));
        assertEquals(12, ImageIO.read(file.toFile()).getWidth());
        assertEquals(directory, file.getParent());
        assertThrows(Problem.class, () -> storage.resolve("../cover.png"));
        assertFalse(PublisherImageStorage.isStoredUrl("/uploads/publisher/../secret"));
    }
    @Test void rejectsNonPublisherAndDisguisedNonImage() throws Exception {
        var storage = new PublisherImageStorage(directory.toString());
        assertThrows(Problem.class, () -> storage.upload(new Actor(1,"MEMBER","member1",""), image()));
        assertThrows(Problem.class, () -> storage.upload(new Actor(3,"PUBLISHER","publisher1",""),
                new MockMultipartFile("file","fake.png","image/png","<script>alert(1)</script>".getBytes())));
    }
}
