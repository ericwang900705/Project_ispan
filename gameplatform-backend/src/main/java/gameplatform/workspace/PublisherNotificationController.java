package gameplatform.workspace;

import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Game-review results and support alerts share the same per-account inbox. */
@RestController
@RequestMapping("/api/workspace/publisher/notifications")
public class PublisherNotificationController {
    private static final String EVENTS = " AND n.event_type IN ('GAME_APPROVED','GAME_REJECTED','GAME_FORCE_OFF_SHELF')";
    private final JdbcTemplate db;

    public PublisherNotificationController(JdbcTemplate db) {
        this.db = db;
    }

    private void check(Actor actor) {
        if (actor == null || !"PUBLISHER".equals(actor.role()))
            throw new Problem(403, "只有發行商可查看審核通知");
    }

    @GetMapping
    public Object list(Actor actor, @RequestParam(defaultValue = "0") int page) {
        check(actor);
        if (page < 0 || page > 100000)
            throw new Problem(400, "無效的頁碼");
        String where = " FROM notifications n LEFT JOIN games g ON g.game_id=n.related_game_id WHERE n.recipient_type='MEMBER' AND n.recipient_id=?"
                + EVENTS;
        var items = db.queryForList(
                "SELECT n.notification_id,n.related_game_id AS game_id,g.game_name,n.read_at AS publisher_read_at,n.created_at AS reviewed_at,n.body AS comment,CASE WHEN n.event_type='GAME_FORCE_OFF_SHELF' THEN 'OFF_SHELF' ELSE 'PUBLISH' END AS review_type,CASE WHEN n.event_type='GAME_REJECTED' THEN 'REJECTED' ELSE 'APPROVED' END AS decision"
                        + where + " ORDER BY n.notification_id DESC OFFSET ? ROWS FETCH NEXT 20 ROWS ONLY",
                actor.id(), page * 20);
        return Map.of("items", items.stream().map(row -> {
            var item = new java.util.LinkedHashMap<String, Object>();
            for (String key : java.util.List.of("notification_id", "game_id", "game_name", "publisher_read_at",
                    "reviewed_at", "comment", "review_type", "decision"))
                item.put(key, row.get(key));
            return item;
        }).toList(), "total", db.queryForObject("SELECT COUNT(*)" + where, Long.class, actor.id()),
                "unread",
                db.queryForObject("SELECT COUNT(*)" + where + " AND n.read_at IS NULL", Long.class, actor.id()),
                "latestId",
                db.queryForObject("SELECT COALESCE(MAX(n.notification_id),0)" + where, Long.class, actor.id()));
    }

    @PostMapping("/{id}/read")
    @Transactional
    public Object read(Actor actor, @PathVariable long id) {
        check(actor);
        db.update(
                "UPDATE notifications SET read_at=CURRENT_TIMESTAMP WHERE notification_id=? AND recipient_type='MEMBER' AND recipient_id=? AND event_type IN ('GAME_APPROVED','GAME_REJECTED','GAME_FORCE_OFF_SHELF') AND read_at IS NULL",
                id, actor.id());
        return Map.of("success", true);
    }

    @PostMapping("/read-all")
    @Transactional
    public Object readAll(Actor actor, @RequestParam long throughId) {
        check(actor);
        if (throughId < 0)
            throw new Problem(400, "無效的通知範圍");
        db.update(
                "UPDATE notifications SET read_at=CURRENT_TIMESTAMP WHERE notification_id<=? AND recipient_type='MEMBER' AND recipient_id=? AND event_type IN ('GAME_APPROVED','GAME_REJECTED','GAME_FORCE_OFF_SHELF') AND read_at IS NULL",
                throughId, actor.id());
        return Map.of("success", true);
    }
}
