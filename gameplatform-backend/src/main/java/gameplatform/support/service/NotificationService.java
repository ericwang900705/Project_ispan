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
    // 共用通知表目前僅由客服使用；查詢與已讀操作限定客服事件。
    private static final String SUPPORT_EVENTS = " AND event_type IN "
            + "('NEW_CHAT','TICKET_CREATED','CHAT_REPLY','TICKET_REPLY','CHAT_MESSAGE','TICKET_MESSAGE')";
    @org.springframework.beans.factory.annotation.Value("${app.support.staff-role-code:ROLE_SUPPORT}")
    private String staffRole;
    @org.springframework.beans.factory.annotation.Value("${app.support.admin-role-code:ROLE_SUPER_ADMIN}")
    private String adminRole;
    private final JdbcTemplate db;
    private final EventHub events;
    public NotificationService(JdbcTemplate db, EventHub events) { this.db=db; this.events=events; }
    private String kind(Actor a) { return a.role().equals("MEMBER") || a.role().equals("PUBLISHER") ? "MEMBER" : "ADMIN"; }
    public Object list(Actor a) {
        String k=kind(a);
        var items=db.queryForList("SELECT TOP 40 notification_id,event_type,title,body,target_path,created_at,read_at FROM dbo.notifications WHERE recipient_type=? AND recipient_id=?" + SUPPORT_EVENTS + " ORDER BY notification_id DESC",k,a.id());
        Integer unread=db.queryForObject("SELECT COUNT(*) FROM dbo.notifications WHERE recipient_type=? AND recipient_id=? AND read_at IS NULL" + SUPPORT_EVENTS,Integer.class,k,a.id());
        return Map.of("items",items,"unread",unread==null?0:unread);
    }
    @Transactional
    public void read(Actor a, long id) {
        db.update("UPDATE dbo.notifications SET read_at=SYSDATETIME() WHERE notification_id=? AND recipient_type=? AND recipient_id=? AND read_at IS NULL" + SUPPORT_EVENTS,id,kind(a),a.id());
    }
    @Transactional
    public void readAll(Actor a) {
        db.update("UPDATE dbo.notifications SET read_at=SYSDATETIME() WHERE recipient_type=? AND recipient_id=? AND read_at IS NULL" + SUPPORT_EVENTS,kind(a),a.id());
    }
    // One notification per event and authorized destination; do not trust the browser to choose recipients.
    public void member(int id,String type,String title,String body,String path) { create("MEMBER",id,type,title,body,path); }
    public void staff(int id,String type,String title,String body,String path) { create("ADMIN",id,type,title,body,path); }
    public void supportQueue(String type,String title,String body,String path) {
        for(Integer id:db.queryForList("SELECT a.admin_id FROM dbo.admin a JOIN dbo.admin_roles r ON r.admin_role_id=a.admin_role_id WHERE a.status='ACTIVE' AND r.role_code IN (?, ?)",Integer.class, staffRole, adminRole))
            staff(id,type,title,body,path);
    }
    public void create(String kind,int id,String type,String title,String body,String path) {
        if(id<=0) return;
        db.update("INSERT INTO dbo.notifications(recipient_type,recipient_id,event_type,title,body,target_path) VALUES (?,?,?,?,?,?)",kind,id,type,title,body,path);
        events.notification(kind,id);
    }
}
