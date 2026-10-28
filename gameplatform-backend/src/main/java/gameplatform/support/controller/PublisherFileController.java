package gameplatform.support.controller;

import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;
import gameplatform.support.service.PublisherFileStorage;
import gameplatform.support.config.StaffPermissionService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 影片可公開展示；ZIP 只允許檔案擁有者、有審核權限的人員或已取得遊戲的會員下載。 */
@RestController
public class PublisherFileController {
    private final PublisherFileStorage storage;
    private final JdbcTemplate db;
    private final StaffPermissionService permissions;

    public PublisherFileController(PublisherFileStorage storage, JdbcTemplate db, StaffPermissionService permissions) {
        this.storage = storage;
        this.db = db;
        this.permissions = permissions;
    }

    @PostMapping(value = "/api/publisher/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object upload(Actor actor, @RequestParam("file") MultipartFile file, @RequestParam String kind) {
        return storage.upload(actor, file, kind);
    }

    @GetMapping("/uploads/publisher-files/videos/{owner}/{name}")
    public ResponseEntity<FileSystemResource> video(@PathVariable int owner, @PathVariable String name) {
        return ResponseEntity.ok().header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.valueOf("video/mp4"))
                .body(new FileSystemResource(storage.resolve("videos", owner, name)));
    }

    @GetMapping("/api/publisher-assets/builds/{owner}/{name}")
    public ResponseEntity<FileSystemResource> build(Actor actor, @PathVariable int owner, @PathVariable String name) {
        if (actor == null)
            throw new Problem(401, "請先登入");
        var file = storage.resolve("builds", owner, name);
        boolean allowed = "PUBLISHER".equals(actor.role()) && actor.id() == owner;
        if (!allowed && actor.isStaff())
            allowed = permissions.forActor(actor).contains("GAME_REVIEW") &&
                    db.queryForObject(
                            "SELECT COUNT(*) FROM game_builds b JOIN games g ON g.game_id=b.game_id WHERE g.member_id=? AND b.file_url=? AND g.status<>'DRAFT'",
                            Integer.class,
                            owner, "/api/publisher-assets/builds/" + owner + "/" + name) > 0;
        if (!allowed && actor.isMember())
            allowed = db.queryForObject(
                    "SELECT COUNT(*) FROM game_builds b JOIN member_games l ON l.game_id=b.game_id JOIN games g ON g.game_id=b.game_id WHERE g.member_id=? AND b.file_url=? AND b.status='ACTIVE' AND l.member_id=? AND l.is_active=1",
                    Integer.class, owner, "/api/publisher-assets/builds/" + owner + "/" + name, actor.id()) > 0;
        if (!allowed)
            throw new Problem(404, "找不到可下載的版本檔案");
        return ResponseEntity.ok().header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"game-build.zip\"")
                .cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(file));
    }
}
