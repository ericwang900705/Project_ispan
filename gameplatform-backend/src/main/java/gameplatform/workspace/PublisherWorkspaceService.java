package gameplatform.workspace;

import gameplatform.game.dto.GameRequest;
import gameplatform.game.repository.GameRepository;
import gameplatform.game.service.GameService;
import gameplatform.game.service.GameTagService;
import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.net.URI;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PublisherWorkspaceService {
    private final JdbcTemplate db;
    private final gameplatform.support.service.NotificationService notifications;
    private final GameService games;
    private final GameTagService tags;
    private final GameRepository repository;
    private final EntityManager entities;

    public PublisherWorkspaceService(JdbcTemplate db, GameService games, GameTagService tags, GameRepository repository,
            EntityManager entities, gameplatform.support.service.NotificationService notifications) {
        this.notifications = notifications;
        this.db = db;
        this.games = games;
        this.tags = tags;
        this.repository = repository;
        this.entities = entities;
    }

    public record GameInput(@NotBlank @Size(max = 150) String name, @NotNull @PositiveOrZero BigDecimal price,
            @Size(max = 20000) String description, @Size(max = 500) String coverUrl, LocalDateTime releaseDate) {
    }

    public record Tags(@NotNull @Size(max = 50) List<@NotNull Integer> tagIds) {
    }

    public record Media(@Pattern(regexp = "IMAGE|VIDEO") String type, @NotBlank @Size(max = 500) String url,
            @Min(1) @Max(1000) int order) {
    }

    public record MediaList(@NotNull @Size(max = 50) List<@NotNull @Valid Media> media) {
    }

    public record Document(@NotNull @Valid GameInput game, @NotNull @Valid Tags tags, @NotNull @Valid MediaList media) {
    }

    public record Build(Integer id, @NotBlank @Size(max = 30) String version, @NotBlank @Size(max = 500) String fileUrl,
            @PositiveOrZero Long fileSize, @NotNull @Pattern(regexp = "ACTIVE|INACTIVE") String status) {
    }

    public record Builds(@NotNull @Size(max = 50) List<@NotNull @Valid Build> builds) {
    }

    private void publisher(Actor a) {
        if (a == null || !"PUBLISHER".equals(a.role()))
            throw new Problem(403, "只有發行商可使用工作區");
    }

    private Map<String, Object> owned(Actor a, int id, boolean lock) {
        publisher(a);
        var rows = db.queryForList(
                "SELECT * FROM games " + (lock ? "WITH (UPDLOCK,ROWLOCK) " : "") + "WHERE game_id=? AND member_id=?",
                id, a.id());
        if (rows.isEmpty())
            throw new Problem(404, "找不到你的遊戲");
        return rows.getFirst();
    }

    private Map<String, Object> editable(Actor a, int id) {
        var game = owned(a, id, true);
        if (!Set.of("DRAFT", "OFF_SHELF").contains(game.get("status")))
            throw new Problem(409, "目前狀態不可編輯，請重新整理");
        return game;
    }

    private void address(String value, boolean artwork) {
        if (artwork && gameplatform.support.service.PublisherFileStorage.isVideoUrl(value)) return;
        if (artwork && value != null && value.startsWith("/uploads/publisher/")
                && gameplatform.support.service.PublisherImageStorage.isStoredUrl(value))
            return;
        if (artwork && value != null && value.startsWith("/api/uploads/"))
            return;
        try {
            URI uri = URI.create(value);
            if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null)
                throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new Problem(400, "請輸入有效的 HTTP 或 HTTPS 網址");
        }
    }

    public Object overview(Actor a) {
        publisher(a);
        return Map.of("gameCount",
                db.queryForObject("SELECT COUNT(*) FROM games WHERE member_id=?", Long.class, a.id()),
                "pendingCount",
                db.queryForObject("SELECT COUNT(*) FROM games WHERE member_id=? AND status='PENDING_REVIEW'",
                        Long.class, a.id()),
                "buyerCount",
                db.queryForObject(
                        "SELECT COUNT(DISTINCT o.member_id) FROM orders o JOIN order_items oi ON oi.order_id=o.order_id JOIN games g ON g.game_id=oi.game_id WHERE g.member_id=? AND o.order_status='PAID'",
                        Long.class, a.id()));
    }

    public Object tags(Actor a) {
        publisher(a);
        return db.queryForList("SELECT tag_id,tag_name FROM tags ORDER BY tag_id");
    }

    public Object games(Actor a, int page, String q) {
        return games(a, page, q, 20);
    }

    public Object games(Actor a, int page, String q, int size) {
        return games(a,page,q,size,"");
    }

    /** 保留原呼叫方式；指定功能時，先限制合法遊戲狀態，再搜尋及分頁。 */
    public Object games(Actor a, int page, String q, int size, String status) {
        return games(a, page, q, size, status, "");
    }

    public Object games(Actor a, int page, String q, int size, String status, String mode) {
        publisher(a);
        var all = List.of("DRAFT", "PENDING_REVIEW", "COMING_SOON", "PUBLISHED", "PENDING_OFF_SHELF", "OFF_SHELF");
        var scopes = Map.of(
            "edit", List.of("DRAFT"),
            "offShelf", List.of("PUBLISHED", "PENDING_OFF_SHELF"),
            "preview", List.of("DRAFT", "PENDING_REVIEW", "COMING_SOON", "PUBLISHED"),
            "publish", List.of("DRAFT", "PENDING_REVIEW", "COMING_SOON", "PUBLISHED"),
            "history", List.of("DRAFT", "PENDING_REVIEW", "COMING_SOON", "PUBLISHED"),
            "buyers", List.of("PUBLISHED"));
        String currentMode = Objects.toString(mode, "");
        String currentStatus = Objects.toString(status, "");
        if (page < 0 || page > 100000 || q == null || q.length() > 100 || size < 1 || size > 20
                || !Set.of("", "list", "new", "edit", "preview", "publish", "history", "offShelf", "buyers", "builds", "tags", "media").contains(currentMode))
            throw new Problem(400, "無效的查詢條件");
        var allowed = scopes.getOrDefault(currentMode, all);
        if (!currentStatus.isEmpty() && !allowed.contains(currentStatus))
            throw new Problem(400, "此頁不提供所選的遊戲狀態");
        String where = " FROM games g WHERE g.member_id=? AND g.game_name LIKE ?";
        var args = new ArrayList<Object>();
        args.add(a.id()); args.add("%" + q.strip() + "%");
        where += " AND g.status IN (" + String.join(",", Collections.nCopies(allowed.size(), "?")) + ")";
        args.addAll(allowed);
        if (!currentStatus.isEmpty()) { where += " AND g.status=?"; args.add(currentStatus); }
        Long total = db.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
        args.add(page * size); args.add(size);
        var items = db.queryForList("SELECT g.game_id,g.game_name,g.status,g.cover_url," + buyers() + " AS buyer_count" + where
                + " ORDER BY g.game_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", args.toArray());
        items.forEach(g -> g.put("pending_review_type", "PENDING_REVIEW".equals(g.get("status")) ? "PUBLISH" : null));
        return Map.of("items", items, "total", total);
    }

    private String buyers() {
        return "(SELECT COUNT(DISTINCT o.member_id) FROM orders o JOIN order_items oi ON oi.order_id=o.order_id WHERE oi.game_id=g.game_id AND o.order_status='PAID')";
    }

    public Map<String, Object> game(Actor a, int id) {
        var game = owned(a, id, false);
        game.put("buyer_count",
                db.queryForObject("SELECT " + buyers() + " FROM games g WHERE g.game_id=?", Long.class, id));
        game.put("tags", db.queryForList(
                "SELECT t.tag_id,t.tag_name FROM tags t JOIN game_tags gt ON gt.tag_id=t.tag_id WHERE gt.game_id=? ORDER BY t.tag_id",
                id));
        var builds = db.queryForList("SELECT * FROM game_builds WHERE game_id=? ORDER BY build_id", id);
        var media = db.queryForList("SELECT * FROM game_media WHERE game_id=? ORDER BY display_order,media_id", id);
        game.put("builds", builds);
        game.put("media", media);
        game.put("history", db.queryForList(
                "SELECT l.game_audit_id AS decision_id,l.review_type,l.decision,l.comment,l.reviewed_at AS decided_at,a.admin_name AS reviewer_name FROM game_audit_logs l JOIN admin a ON a.admin_id=l.admin_id WHERE l.game_id=? ORDER BY l.reviewed_at DESC,l.game_audit_id DESC",
                id));
        game.put("pending_review_type", "PENDING_REVIEW".equals(game.get("status")) ? "PUBLISH" : null);
        game.put("server_time", System.currentTimeMillis());
        var deadline = game.get("scheduled_off_shelf_at");
        Long until = deadline instanceof java.sql.Timestamp t
                ? t.toLocalDateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                : null;
        game.put("restore_until", until);
        game.put("can_restore",
                "PENDING_OFF_SHELF".equals(game.get("status")) && until != null && until > System.currentTimeMillis());
        game.put("publish_checks", List.of(
                Map.of("key", "info", "mode", "edit", "label", "遊戲名稱、售價、介紹與發售時間", "complete",
                        game.get("release_date") != null && game.get("description") != null
                                && !game.get("description").toString().isBlank()),
                Map.of("key", "builds", "mode", "builds", "label", "至少一個啟用版本", "complete",
                        builds.stream().anyMatch(b -> "ACTIVE".equals(b.get("status")))),
                Map.of("key", "tags", "mode", "tags", "label", "至少一個遊戲標籤", "complete",
                        !((List<?>) game.get("tags")).isEmpty()),
                Map.of("key", "media", "mode", "media", "label", "遊戲封面與宣傳素材", "complete", game.get("cover_url") != null
                        && !game.get("cover_url").toString().isBlank() && !media.isEmpty())));
        return game;
    }

    @Transactional
    public Object document(Actor a, Integer id, Document input) {
        publisher(a);
        if (id != null)
            editable(a, id);
        var in = input.game();
        if (in.coverUrl() != null && !in.coverUrl().isBlank())
            address(in.coverUrl(), true);
        var req = new GameRequest();
        req.setMemberId(a.id());
        req.setGameName(in.name().trim());
        req.setPrice(in.price());
        req.setDescription(in.description());
        req.setCoverUrl(in.coverUrl());
        req.setReleaseDate(in.releaseDate());
        var result = id == null ? games.insert(req) : games.update(id, req);
        entities.flush();
        int gameId = result.getGameId();
        tags.updateGameTags(gameId, a.id(), input.tags().tagIds());
        replaceMedia(gameId, input.media());
        entities.flush();
        return game(a, gameId);
    }

    @Transactional
    public Object saveTags(Actor a, int id, Tags input) {
        editable(a, id);
        tags.updateGameTags(id, a.id(), input.tagIds());
        entities.flush();
        return game(a, id);
    }

    @Transactional
    public Object saveMedia(Actor a, int id, MediaList input) {
        editable(a, id);
        replaceMedia(id, input);
        return game(a, id);
    }

    private void replaceMedia(int id, MediaList input) {
        for (var media : input.media())
            address(media.url(), true);
        db.update("DELETE FROM game_media WHERE game_id=?", id);
        for (var media : input.media())
            db.update("INSERT INTO game_media(game_id,media_type,media_url,display_order) VALUES(?,?,?,?)", id,
                    media.type(), media.url(), media.order());
    }

    /** 已上架遊戲只能新增待發布版本；既有檔案及版本號保留，避免覆蓋玩家正在使用的版本。 */
    @Transactional
    public Object saveBuilds(Actor a, int id, Builds input) {
        var current = owned(a, id, true);
        boolean live = "PUBLISHED".equals(current.get("status"));
        if (!live && !Set.of("DRAFT", "OFF_SHELF").contains(current.get("status")))
            throw new Problem(409, "審核、即將發售或預定下架期間不可更新版本");
        var existing = db.queryForList("SELECT * FROM game_builds WHERE game_id=?", id);
        var byId = new HashMap<Integer,Map<String,Object>>();
        existing.forEach(row -> byId.put(((Number)row.get("build_id")).intValue(), row));
        var seen = new HashSet<Integer>();
        var versions = new HashSet<String>();
        // 上架後保留所有歷史版本，因此新版本號也不可與省略的既有版本重複。
        if (live) existing.forEach(row -> versions.add(Objects.toString(row.get("version")).toLowerCase(Locale.ROOT)));
        if (input == null || input.builds() == null || input.builds().size() > 50)
            throw new Problem(400, "每次最多設定 50 個版本");
        int added = 0;
        for (var build : input.builds()) {
            if (build == null || build.version() == null || build.version().isBlank() || build.version().length()>30
                    || build.fileUrl()==null || build.fileUrl().isBlank() || build.fileUrl().length()>500
                    || !Set.of("ACTIVE","INACTIVE").contains(Objects.toString(build.status(),""))
                    || (build.fileSize()!=null && build.fileSize()<0)) throw new Problem(400,"版本資料無效");
            var old = build.id() == null ? null : byId.get(build.id());
            if (build.id()!=null && (old==null || !seen.add(build.id()))) throw new Problem(400,"版本不屬於此遊戲或重複");
            if (live && old!=null) {
                Long oldSize = old.get("file_size") == null ? null : ((Number)old.get("file_size")).longValue();
                if (!Objects.equals(old.get("version"),build.version()) || !Objects.equals(old.get("file_url"),build.fileUrl())
                        || !Objects.equals(oldSize,build.fileSize()) || !Objects.equals(old.get("status"),build.status()))
                    throw new Problem(409,"已上架的既有版本不可覆寫，請新增版本後發布");
                continue;
            }
            if (!versions.add(build.version().strip().toLowerCase(Locale.ROOT))) throw new Problem(400,"版本號不可重複");
            if (live && !"INACTIVE".equals(build.status())) throw new Problem(400,"新版本請先儲存為待發布，再確認發布");
            if (build.fileUrl().startsWith("/api/publisher-assets/builds/")) {
                if (!gameplatform.support.service.PublisherFileStorage.isBuildUrl(build.fileUrl(), a.id()))
                    throw new Problem(400,"只能使用自己上傳的 ZIP 版本檔案");
            } else if (old==null || !Objects.equals(old.get("file_url"),build.fileUrl())) address(build.fileUrl(), false);
            if (old!=null) db.update("UPDATE game_builds SET version=?,file_url=?,file_size=?,status=? WHERE build_id=? AND game_id=?",
                    build.version().strip(),build.fileUrl(),build.fileSize(),build.status(),build.id(),id);
            else {
                if (live && existing.size() + ++added > 50) throw new Problem(400,"每款遊戲最多保留 50 個版本");
                var holder = new GeneratedKeyHolder();
                db.update(c -> {
                    var ps=c.prepareStatement("INSERT INTO game_builds(game_id,version,file_url,file_size,status) VALUES(?,?,?,?,?)",new String[]{"build_id"});
                    ps.setInt(1,id);ps.setString(2,build.version().strip());ps.setString(3,build.fileUrl());ps.setObject(4,build.fileSize());ps.setString(5,build.status());return ps;
                },holder);
                seen.add(Objects.requireNonNull(holder.getKey()).intValue());
            }
        }
        if (!live) for (var buildId : byId.keySet()) if (!seen.contains(buildId))
            db.update("UPDATE game_builds SET status='INACTIVE' WHERE build_id=? AND game_id=?",buildId,id);
        return game(a,id);
    }

    /** 在同一交易內切換啟用版本；失敗會保留舊版本，也可明確重新啟用歷史版本。 */
    @Transactional
    public Object releaseBuild(Actor a,int gameId,int buildId) {
        var current=owned(a,gameId,true);
        if (!"PUBLISHED".equals(current.get("status"))) throw new Problem(409,"只有已上架遊戲可發布更新版本");
        var rows=db.queryForList("SELECT * FROM game_builds WHERE build_id=? AND game_id=?",buildId,gameId);
        if (rows.isEmpty()) throw new Problem(404,"找不到此遊戲的版本");
        db.update("UPDATE game_builds SET status='INACTIVE' WHERE game_id=? AND status='ACTIVE'",gameId);
        db.update("UPDATE game_builds SET status='ACTIVE' WHERE build_id=? AND game_id=?",buildId,gameId);
        return game(a,gameId);
    }

    @Transactional
    public Object action(Actor a, int id, String action) {
        var current = owned(a, id, true);
        switch (action) {
            case "submit" -> {
                if (!Set.of("DRAFT", "OFF_SHELF").contains(current.get("status")))
                    throw new Problem(409, "目前狀態不能送審");
                var detail = game(a, id);
                for (var item : (List<?>) detail.get("publish_checks"))
                    if (!Boolean.TRUE.equals(((Map<?, ?>) item).get("complete")))
                        throw new Problem(400, "請先完成所有上架條件");
                var game = repository.findById(id).orElseThrow();
                if (game.getReleaseDate().isBefore(LocalDateTime.now().plusHours(24)))
                    throw new Problem(400, "發售時間須至少在現在的 24 小時後，並預留審核時間");
                if ("OFF_SHELF".equals(game.getStatus())) {
                    game.setStatus("DRAFT");
                    repository.saveAndFlush(game);
                }
                games.submitForReview(id);
                notifications.reviewers("GAME_SUBMITTED", "新的遊戲上架申請", current.get("game_name").toString(),
                        "/admin/games?mode=pending&game=" + id);
            }
            case "off-shelf" -> games.requestOffShelf(id);
            case "cancel-off-shelf" -> {
                if (!Boolean.TRUE.equals(game(a, id).get("can_restore")))
                    throw new Problem(409, "取消下架期限已過");
                games.cancelOffShelf(id);
            }
            default -> throw new Problem(400, "不支援的操作");
        }
        entities.flush();
        return game(a, id);
    }

    public Object sales(Actor a, String from, String to, Integer gameId) {
        publisher(a);
        LocalDate start, end;
        try {
            end = to == null || to.isBlank() ? LocalDate.now(ZoneId.of("Asia/Taipei")) : LocalDate.parse(to);
            start = from == null || from.isBlank() ? end.minusDays(29) : LocalDate.parse(from);
            if (start.isAfter(end) || java.time.temporal.ChronoUnit.DAYS.between(start, end) > 365)
                throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new Problem(400, "請選擇有效的日期範圍，最多 366 天");
        }
        if (gameId != null)
            owned(a, gameId, false);
        String where = " FROM order_items oi JOIN orders o ON o.order_id=oi.order_id JOIN games g ON g.game_id=oi.game_id WHERE g.member_id=? AND o.order_status IN ('PAID','REFUNDED') AND o.paid_at>=? AND o.paid_at<?";
        var args = new ArrayList<Object>(List.of(a.id(), java.sql.Timestamp.valueOf(start.atStartOfDay()),
                java.sql.Timestamp.valueOf(end.plusDays(1).atStartOfDay())));
        if (gameId != null) {
            where += " AND g.game_id=?";
            args.add(gameId);
        }
        String sums = "COALESCE(SUM(CASE WHEN o.order_status='PAID' THEN CAST(oi.quantity AS BIGINT) ELSE 0 END),0) AS paid_units,COALESCE(SUM(CASE WHEN o.order_status='PAID' THEN oi.final_amount ELSE 0 END),0) AS paid_amount,COUNT(DISTINCT CASE WHEN o.order_status='PAID' THEN o.member_id END) AS buyers,COALESCE(SUM(CASE WHEN o.order_status='REFUNDED' THEN CAST(oi.quantity AS BIGINT) ELSE 0 END),0) AS refunded_units,COALESCE(SUM(CASE WHEN o.order_status='REFUNDED' THEN oi.final_amount ELSE 0 END),0) AS refunded_amount";
        var daily = db.queryForList("SELECT CAST(o.paid_at AS DATE) AS sale_date," + sums + where
                + " GROUP BY CAST(o.paid_at AS DATE) ORDER BY sale_date", args.toArray());
        daily.forEach(r -> r.put("sale_date", r.get("sale_date").toString()));
        return Map.of("from", start.toString(), "to", end.toString(), "summary",
                db.queryForMap("SELECT " + sums + where, args.toArray()), "games",
                db.queryForList(
                        "SELECT g.game_id,g.game_name," + sums + where
                                + " GROUP BY g.game_id,g.game_name ORDER BY paid_amount DESC,g.game_id",
                        args.toArray()),
                "daily", daily, "options", db.queryForList(
                        "SELECT game_id,game_name FROM games WHERE member_id=? ORDER BY game_name,game_id", a.id()));
    }
}
