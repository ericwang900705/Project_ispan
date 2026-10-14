package gameplatform.support.controller;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import gameplatform.support.model.Actor;
import gameplatform.support.service.PublisherImageStorage;

@RestController
public class PublisherImageController {
    private final PublisherImageStorage storage;

    public PublisherImageController(PublisherImageStorage storage) { this.storage = storage; }

    @PostMapping(value="/api/publisher/images", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object upload(@AuthenticationPrincipal Actor actor, @RequestParam("file") MultipartFile file) {
        return storage.upload(actor,file);
    }

    @GetMapping("/uploads/publisher/{name}")
    public ResponseEntity<FileSystemResource> image(@PathVariable String name) {
        var file = storage.resolve(name);
        return ResponseEntity.ok().cacheControl(CacheControl.noCache())
                .header("X-Content-Type-Options","nosniff")
                .contentType(name.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
                .body(new FileSystemResource(file));
    }
}
