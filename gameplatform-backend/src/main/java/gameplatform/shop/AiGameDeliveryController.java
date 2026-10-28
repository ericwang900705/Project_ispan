
package gameplatform.shop;

import gameplatform.support.model.Actor;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * AI 遊戲線上遊玩及 ZIP 下載 API。
 *
 * HTML / ZIP 存放於後端私有資料夾，
 * 不放在 frontend/public。
 *
 * 每次讀取檔案前都驗證會員身分及遊戲擁有權。
 */
@org.springframework.context.annotation.PropertySource(value="classpath:game-assets.properties",ignoreResourceNotFound=true)
@RestController
@RequestMapping("/api/member-games")
public class AiGameDeliveryController {

    private final JdbcTemplate db;
    private final Path root;

    // 遊戲名稱對應素材檔名
    private static final Map<String, String> FILES = Map.ofEntries(
            Map.entry("暗夜倖存者", "dark-survivor"),
            Map.entry("無盡深淵", "abyss"),
            Map.entry("失落王國", "lost-kingdom"),
            Map.entry("星露森林", "starlight-forest"),
            Map.entry("荒野生存", "wilderness"),
            Map.entry("戰場前線", "tactical-frontline"),
            Map.entry("火柴人射擊", "stickman-shooter"),
            Map.entry("火柴人紅藍競技場", "stickman-arena"),
            Map.entry("三國戰棋", "three-kingdoms"),
            Map.entry("牛排大師", "steak-master"),
            Map.entry("火柴人羽毛球", "badminton"),
            Map.entry("火柴人賽車", "racing"));

    public AiGameDeliveryController(
            JdbcTemplate db,
            @Value("${hero.ai-games.dir:}") String dir) {
        this.db = db;
        this.root = assetDirectory(dir);
    }

    /** 支援從專題根目錄或後端資料夾啟動；正式部署可明確指定素材位置。 */
    public static Path assetDirectory(String configured) {
        if (configured != null && !configured.isBlank()) return Paths.get(configured).toAbsolutePath().normalize();
        Path local = Paths.get("ai-game-assets").toAbsolutePath().normalize();
        if (Files.isDirectory(local)) return local;
        Path sibling = Paths.get("..", "ai-game-assets").toAbsolutePath().normalize();
        return Files.isDirectory(sibling) ? sibling : local;
    }

    /**
     * 驗證會員是否擁有該遊戲。
     * 同時取得遊戲名稱及素材 slug。
     */
    private GameAsset gameAsset(
            Actor actor,
            int memberId,
            int gameId) {

        // 驗證登入會員與要求下載的會員相同
        if (actor == null ||
                !actor.isMember() ||
                actor.id() != memberId) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "沒有存取此遊戲的權限");
        }

        // 確認會員已購買且遊戲權益有效
        var rows = db.queryForList(
                """
                        SELECT g.game_name
                        FROM dbo.member_games mg
                        JOIN dbo.games g
                          ON g.game_id = mg.game_id
                        WHERE mg.member_id = ?
                          AND mg.game_id = ?
                          AND mg.is_active = 1
                          AND g.status IN (
                              'PUBLISHED',
                              'PENDING_OFF_SHELF'
                          )
                        """,
                memberId,
                gameId);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "尚未擁有此遊戲或遊戲無法使用");
        }

        String gameName = String.valueOf(
                rows.get(0).get("game_name"));

        // 找出這款遊戲的素材名稱
        String slug = FILES.get(gameName);

        if (slug == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "找不到此遊戲的 HTML 或 ZIP 版本");
        }

        return new GameAsset(gameName, slug);
    }

    /**
     * 遊戲名稱及素材代碼。
     */
    private record GameAsset(
            String gameName,
            String slug) {
    }

    /**
     * 取得素材檔案。
     * 防止路徑跳脫並確認檔案存在。
     */
    private Path asset(
            String slug,
            String extension) {

        Path file = root.resolve(
                slug + extension).normalize();

        if (!file.startsWith(root)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "非法檔案路徑");
        }

        if (!Files.isRegularFile(file)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "尚未匯入遊戲素材：" +
                            slug + extension);
        }

        return file;
    }

    /**
     * HTML 線上遊玩 API。
     *
     * GET
     * /api/member-games/{memberId}/{gameId}/ai-html
     */
    @GetMapping("/{memberId}/{gameId}/ai-html")
    public Map<String, String> html(
            Actor actor,
            @PathVariable int memberId,
            @PathVariable int gameId) throws IOException {

        GameAsset game = gameAsset(
                actor,
                memberId,
                gameId);

        Path file = asset(
                game.slug(),
                ".html");

        // HTML 最大 2 MB
        if (Files.size(file) > 2_000_000) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "遊戲 HTML 檔案過大");
        }

        String htmlContent = Files.readString(
                file,
                StandardCharsets.UTF_8);

        return Map.of(
                "html",
                htmlContent);
    }

    /**
     * ZIP 下載 API。
     *
     * GET
     * /api/member-games/{memberId}/{gameId}/ai-zip
     *
     * 使用中文遊戲名稱下載：
     * 例如 暗夜倖存者.zip
     */
    @GetMapping("/{memberId}/{gameId}/ai-zip")
    public ResponseEntity<byte[]> zip(
            Actor actor,
            @PathVariable int memberId,
            @PathVariable int gameId) throws IOException {

        // 先驗證會員購買權限
        GameAsset game = gameAsset(
                actor,
                memberId,
                gameId);

        // 找到對應的 ZIP
        Path file = asset(
                game.slug(),
                ".zip");

        // ZIP 最大 20 MB
        if (Files.size(file) > 20_000_000) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "遊戲 ZIP 檔案過大");
        }

        // 取得資料庫中的遊戲名稱
        String gameName = game.gameName();

        // 過濾 Windows 不允許的檔名字元
        String safeName = gameName
                .replaceAll(
                        "[\\\\/:*?\"<>|\\r\\n]",
                        "_")
                .trim();

        // 避免空白檔名
        if (safeName.isEmpty()) {
            safeName = "game-" + gameId;
        }

        // UTF-8 中文檔名編碼
        String encodedName = URLEncoder.encode(
                safeName + ".zip",
                StandardCharsets.UTF_8).replace("+", "%20");

        // 讀取 ZIP
        byte[] zipData = Files.readAllBytes(file);

        // Content-Disposition:
        // filename 為英文備用名稱
        // filename* 為 UTF-8 中文名稱
        String contentDisposition = "attachment; filename=\"game-" +
                gameId +
                ".zip\"; filename*=UTF-8''" +
                encodedName;

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        contentDisposition)
                .contentType(
                        MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(zipData.length)
                .body(zipData);
    }
}
