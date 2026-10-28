package gameplatform.support.service;

import gameplatform.support.model.Actor;
import gameplatform.support.model.EventHub;
import gameplatform.support.model.Problem;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    // 客服、遊戲及退款通知依目前身分與授權查詢。
    private static final String SUPPORT_EVENTS = " AND event_type IN "
            + "('NEW_CHAT','TICKET_CREATED','CHAT_REPLY','TICKET_REPLY','CHAT_MESSAGE','TICKET_MESSAGE','ROOM_CLOSED','TICKET_CLOSED')";

    private static final String MEMBER_EVENTS = " AND event_type IN "
            + "('REFUND_REQUESTED','REFUND_UPDATE','NEW_CHAT','TICKET_CREATED','CHAT_REPLY','TICKET_REPLY','CHAT_MESSAGE','TICKET_MESSAGE','ROOM_CLOSED','TICKET_CLOSED')";

    private static final String PUBLISHER_EVENTS = " AND event_type IN "
            + "('REFUND_REQUESTED','REFUND_UPDATE','NEW_CHAT','TICKET_CREATED','CHAT_REPLY',"
            + "'TICKET_REPLY','CHAT_MESSAGE','TICKET_MESSAGE','ROOM_CLOSED','TICKET_CLOSED',"
            + "'GAME_APPROVED','GAME_REJECTED','GAME_FORCE_OFF_SHELF')";

    // 管理員可以看到客服通知與遊戲送審通知
    private static final String ADMIN_EVENTS = " AND event_type IN "
            + "('NEW_CHAT','TICKET_CREATED','CHAT_REPLY',"
            + "'TICKET_REPLY','CHAT_MESSAGE','TICKET_MESSAGE','ROOM_CLOSED','TICKET_CLOSED',"
            + "'GAME_SUBMITTED')";

    // 審核人員可以看到送審通知
    private static final String REVIEWER_EVENTS = " AND event_type IN ('GAME_SUBMITTED')";

    private String visibleEvents(Actor actor) {
        if (actor.role().equals("PUBLISHER"))
            return PUBLISHER_EVENTS;
        if (actor.isMember())
            return MEMBER_EVENTS;
        var granted = permissions.forActor(actor);
        boolean support = granted.contains("TICKET_HANDLE");
        boolean review = granted.contains("GAME_REVIEW");
        String filter = support && review ? ADMIN_EVENTS : support ? SUPPORT_EVENTS : review ? REVIEWER_EVENTS : " AND 1=0";
        return granted.contains("REFUND_MANAGE") ? " AND (event_type='REFUND_REQUESTED' OR (1=1" + filter + "))" : filter;
    }

    private final JdbcTemplate db;
    private final EventHub events;
    private final gameplatform.support.config.StaffPermissionService permissions;

    public NotificationService(JdbcTemplate db, EventHub events,
            gameplatform.support.config.StaffPermissionService permissions) {
        this.db = db;
        this.events = events;
        this.permissions = permissions;
    }

    private String kind(Actor a) {
        return a.role().equals("MEMBER") || a.role().equals("PUBLISHER") ? "MEMBER" : "ADMIN";
    }

    public Object list(Actor a) {
        String k = kind(a);
        var items = db.queryForList(
                "SELECT TOP 40 notification_id,event_type,title,body,target_path,related_game_id,created_at,read_at FROM dbo.notifications WHERE recipient_type=? AND recipient_id=?"
                        + visibleEvents(a) + " ORDER BY notification_id DESC",
                k, a.id());
        Integer unread = db.queryForObject(
                "SELECT COUNT(*) FROM dbo.notifications WHERE recipient_type=? AND recipient_id=? AND read_at IS NULL"
                        + visibleEvents(a),
                Integer.class, k, a.id());
        return Map.of("items", items, "unread", unread == null ? 0 : unread);
    }

    @Transactional
    public void read(Actor a, long id) {
        db.update(
                "UPDATE dbo.notifications SET read_at=SYSDATETIME() WHERE notification_id=? AND recipient_type=? AND recipient_id=? AND read_at IS NULL"
                        + visibleEvents(a),
                id, kind(a), a.id());
    }

    @Transactional
    public void readAll(Actor a) {
        db.update(
                "UPDATE dbo.notifications SET read_at=SYSDATETIME() WHERE recipient_type=? AND recipient_id=? AND read_at IS NULL"
                        + visibleEvents(a),
                kind(a), a.id());
    }

    // One notification per event and authorized destination; do not trust the
    // browser to choose recipients.
    public void member(int id, String type, String title, String body, String path) {
        create("MEMBER", id, type, title, body, path);
    }

    public void staff(int id, String type, String title, String body, String path) {
        create("ADMIN", id, type, title, body, path);
    }

    public void supportQueue(String type, String title, String body, String path) {
        for (Integer id : db.queryForList(
                "SELECT a.admin_id FROM dbo.admin a JOIN dbo.admin_roles r ON r.admin_role_id=a.admin_role_id "
                        + "WHERE a.status='ACTIVE' AND (r.role_code IN ('ADMIN','SUPPORT') OR (r.role_code='STAFF' "
                        + "AND EXISTS (SELECT 1 FROM dbo.admin_user_permissions u JOIN dbo.admin_permissions p ON p.permission_id=u.permission_id "
                        + "WHERE u.admin_id=a.admin_id AND p.permission_code='TICKET_HANDLE')))",
                Integer.class))
            staff(id, type, title, body, path);
    }

    /** 退款待辦只送給有處理權限的人員。 */
    public void refundQueue(String title, String body) {
        for (Integer id : db.queryForList(
                "SELECT a.admin_id FROM dbo.admin a JOIN dbo.admin_roles r ON r.admin_role_id=a.admin_role_id "
                + "WHERE a.status='ACTIVE' AND (r.role_code='ADMIN' OR (r.role_code='STAFF' AND EXISTS "
                + "(SELECT 1 FROM dbo.admin_user_permissions u JOIN dbo.admin_permissions p ON p.permission_id=u.permission_id "
                + "WHERE u.admin_id=a.admin_id AND p.permission_code='REFUND_MANAGE')))", Integer.class))
            staff(id, "REFUND_REQUESTED", title, body, "/admin/refund-requests");
    }

    // Notify ADMIN, fixed REVIEWER, and STAFF granted GAME_REVIEW.
    public void reviewers(String type, String title, String body, String path) {
        for (Integer id : db.queryForList(
                "SELECT a.admin_id FROM dbo.admin a JOIN dbo.admin_roles r ON r.admin_role_id=a.admin_role_id "
                        + "WHERE a.status='ACTIVE' AND (r.role_code IN ('ADMIN','REVIEWER') OR "
                        + "(r.role_code='STAFF' AND EXISTS (SELECT 1 FROM dbo.admin_user_permissions u "
                        + "JOIN dbo.admin_permissions p ON p.permission_id=u.permission_id "
                        + "WHERE u.admin_id=a.admin_id AND p.permission_code='GAME_REVIEW')))",
                Integer.class))
            staff(id, type, title, body, path);
    }

    // Keep the related game ID available to the publisher notifications page.
    public void gameResult(int memberId, int gameId, String type, String title, String body, String path) {
        if (memberId <= 0 || gameId <= 0)
            return;
        db.update(
                "INSERT INTO dbo.notifications(recipient_type,recipient_id,event_type,title,body,target_path,related_game_id) VALUES ('MEMBER',?,?,?,?,?,?)",
                memberId, type, title, body, path, gameId);
        events.notification("MEMBER", memberId);
    }

    public void create(String kind, int id, String type, String title, String body, String path) {
        if (id <= 0)
            return;
        db.update(
                "INSERT INTO dbo.notifications(recipient_type,recipient_id,event_type,title,body,target_path) VALUES (?,?,?,?,?,?)",
                kind, id, type, title, body, path);
        events.notification(kind, id);
    }
}
