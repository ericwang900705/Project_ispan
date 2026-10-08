package gameplatform.support.service;

import java.net.URI;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import java.sql.Statement;
import java.util.*;
import gameplatform.support.model.*;
import gameplatform.support.model.PublisherModels.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="aki.modules.enabled",havingValue="true")
@Service
@Transactional(readOnly = true)
public class PublisherService {
    private static final List<String> ALLOWED_TAGS=List.of("RPG","多人遊戲","冒險","恐怖","益智","動作","策略","模擬");
    private final JdbcTemplate db;
    private final Clock clock;
    private static final String BUYERS = "(SELECT COUNT(DISTINCT o.member_id) FROM orders o JOIN order_items oi ON oi.order_id=o.order_id WHERE oi.game_id=g.game_id AND o.order_status='PAID')";
    private static final String FIELDS = "g.game_id,g.game_name,g.price,g.description,g.cover_url,g.status,g.release_date,g.pending_review_type,g.review_requested_at,g.review_request_reason,g.pending_request_key,g.off_shelf_source,g.off_shelf_at,g.restore_until,g.off_shelf_previous_status,g.off_shelf_reason,g.off_shelf_admin_id," + BUYERS + " AS buyer_count";

    @Autowired
    public PublisherService(JdbcTemplate db) { this(db,Clock.systemUTC()); }
    public PublisherService(JdbcTemplate db,Clock clock) { this.db=db; this.clock=clock; }

    private void restoration(Map<String,Object> game) {
        long now=clock.millis();
        Number deadline=(Number)game.get("restore_until");
        game.put("server_time",now);
        game.put("can_restore", "OFF_SHELF".equals(game.get("status")) && "PUBLISHER".equals(game.get("off_shelf_source"))
            && game.get("pending_review_type")==null && deadline!=null && deadline.longValue()>now);
    }

    private void check(Actor actor) {
        if (actor == null || !"PUBLISHER".equals(actor.role())) throw new Problem(403,"只有發行商可以存取此介面");
        if (db.queryForObject("SELECT COUNT(*) FROM members WHERE member_id=? AND is_publisher=1",Integer.class,actor.id()) != 1)
            throw new Problem(403,"發行商身分已失效，請重新登入");
    }

    public Map<String,Object> overview(Actor actor) {
        check(actor);
        return Map.of("gameCount", db.queryForObject("SELECT COUNT(*) FROM games WHERE member_id=?",Long.class,actor.id()),
            "buyerCount",db.queryForObject("SELECT COUNT(DISTINCT o.member_id) FROM orders o JOIN order_items oi ON oi.order_id=o.order_id JOIN games g ON g.game_id=oi.game_id WHERE g.member_id=? AND o.order_status='PAID'",Long.class,actor.id()),
            "pendingCount",db.queryForObject("SELECT COUNT(*) FROM games WHERE member_id=? AND pending_review_type IS NOT NULL",Long.class,actor.id()));
    }

    /** Order-line amounts are historical snapshots, never today's game price. */
    public Object sales(Actor actor,String from,String to,Integer gameId) {
        check(actor);
        java.time.LocalDate end, start;
        try {
            end=(to==null || to.isBlank()) ? java.time.LocalDate.now(clock.withZone(java.time.ZoneId.of("Asia/Taipei"))) : java.time.LocalDate.parse(to);
            start=(from==null || from.isBlank()) ? end.minusDays(29) : java.time.LocalDate.parse(from);
            if(start.isAfter(end) || java.time.temporal.ChronoUnit.DAYS.between(start,end)>365 || start.getYear()<1900 || end.getYear()>9998) throw new IllegalArgumentException();
        } catch (java.time.DateTimeException | IllegalArgumentException e) { throw new Problem(400,"請選擇有效的日期範圍，最多366天"); }
        if(gameId!=null) game(actor,gameId);
        String where=" FROM order_items oi JOIN orders o ON o.order_id=oi.order_id JOIN games g ON g.game_id=oi.game_id WHERE g.member_id=? AND o.order_status IN ('PAID','REFUNDED') AND o.paid_at>=? AND o.paid_at<?";
        List<Object> params=new ArrayList<>(List.of(actor.id(),java.sql.Timestamp.valueOf(start.atStartOfDay()),java.sql.Timestamp.valueOf(end.plusDays(1).atStartOfDay())));
        if(gameId!=null) { where+=" AND g.game_id=?"; params.add(gameId); }
        String sums="COALESCE(SUM(CASE WHEN o.order_status='PAID' THEN CAST(oi.quantity AS BIGINT) ELSE 0 END),0) AS paid_units,"
            +"COALESCE(SUM(CASE WHEN o.order_status='PAID' THEN oi.final_amount ELSE 0 END),0) AS paid_amount,"
            +"COUNT(DISTINCT CASE WHEN o.order_status='PAID' THEN o.member_id END) AS buyers,"
            +"COALESCE(SUM(CASE WHEN o.order_status='REFUNDED' THEN CAST(oi.quantity AS BIGINT) ELSE 0 END),0) AS refunded_units,"
            +"COALESCE(SUM(CASE WHEN o.order_status='REFUNDED' THEN oi.final_amount ELSE 0 END),0) AS refunded_amount";
        var summary=db.queryForMap("SELECT "+sums+where,params.toArray());
        var games=db.queryForList("SELECT g.game_id,g.game_name,"+sums+where+" GROUP BY g.game_id,g.game_name ORDER BY paid_amount DESC,g.game_id",params.toArray());
        var daily=db.queryForList("SELECT CAST(o.paid_at AS DATE) AS sale_date,"+sums+where+" GROUP BY CAST(o.paid_at AS DATE) ORDER BY sale_date",params.toArray());
        daily.forEach(row->row.put("sale_date",row.get("sale_date").toString()));
        return Map.of("from",start.toString(),"to",end.toString(),"summary",summary,"games",games,"daily",daily,
            "options",db.queryForList("SELECT game_id,game_name FROM games WHERE member_id=? ORDER BY game_name,game_id",actor.id()));
    }

    public Object notifications(Actor actor,int page) {
        check(actor);
        if(page<0 || page>100000) throw new Problem(400,"無效的頁碼");
        String from=" FROM game_audit_logs l JOIN games g ON g.game_id=l.game_id WHERE g.member_id=?";
        var items=db.queryForList("SELECT l.game_audit_id AS notification_id,l.game_id,g.game_name,l.review_type,l.decision,l.comment,l.reviewed_at,l.publisher_read_at"+from+" ORDER BY l.game_audit_id DESC OFFSET ? ROWS FETCH NEXT 20 ROWS ONLY",actor.id(),page*20);
        return Map.of("items",items,"total",db.queryForObject("SELECT COUNT(*)"+from,Long.class,actor.id()),
            "unread",db.queryForObject("SELECT COUNT(*)"+from+" AND l.publisher_read_at IS NULL",Long.class,actor.id()),
            "latestId",db.queryForObject("SELECT COALESCE(MAX(l.game_audit_id),0)"+from,Long.class,actor.id()));
    }

    @Transactional
    public Object readNotification(Actor actor,int id) {
        check(actor);
        String own="game_audit_id=? AND game_id IN (SELECT game_id FROM games WHERE member_id=?)";
        if(db.queryForObject("SELECT COUNT(*) FROM game_audit_logs WHERE "+own,Integer.class,id,actor.id())==0) throw new Problem(404,"找不到此通知");
        db.update("UPDATE game_audit_logs SET publisher_read_at=CURRENT_TIMESTAMP WHERE "+own+" AND publisher_read_at IS NULL",id,actor.id());
        return Map.of("success",true);
    }

    @Transactional
    public Object readAllNotifications(Actor actor,long throughId) {
        check(actor);
        if(throughId<0) throw new Problem(400,"無效的通知範圍");
        int changed=db.update("UPDATE game_audit_logs SET publisher_read_at=CURRENT_TIMESTAMP WHERE game_audit_id<=? AND publisher_read_at IS NULL AND game_id IN (SELECT game_id FROM games WHERE member_id=?)",throughId,actor.id());
        return Map.of("changed",changed);
    }

    private String pattern(String value) {
        if (value == null || value.length() > 100) throw new Problem(400,"無效的搜尋條件");
        return "%"+value.trim().replace("!","!!").replace("%","!%").replace("_","!_").replace("[","![")+"%";
    }

    public Object games(Actor actor,int page,String keyword) {
        check(actor);
        if (page < 0 || page > 100000) throw new Problem(400,"無效的頁碼");
        String where=" WHERE g.member_id=? AND g.game_name LIKE ? ESCAPE '!'", p=pattern(keyword);
        var total=db.queryForObject("SELECT COUNT(*) FROM games g"+where,Long.class,actor.id(),p);
        var items=db.queryForList("SELECT "+FIELDS+" FROM games g"+where+" ORDER BY g.game_id DESC OFFSET ? ROWS FETCH NEXT 20 ROWS ONLY",actor.id(),p,page*20);
        items.forEach(this::restoration);
        return Map.of("items",items,"total",total,"page",page,"pageSize",20);
    }

    public Map<String,Object> game(Actor actor,int id) {
        check(actor);
        var items=db.queryForList("SELECT "+FIELDS+" FROM games g WHERE g.game_id=? AND g.member_id=?",id,actor.id());
        if(items.isEmpty()) throw new Problem(404,"找不到此遊戲");
        var result=new LinkedHashMap<>(items.getFirst());
        restoration(result);
        result.put("tags",allowedTags(db.queryForList("SELECT t.tag_id,t.tag_name FROM game_tags gt JOIN tags t ON t.tag_id=gt.tag_id WHERE gt.game_id=? ORDER BY t.tag_id",id)));
        result.put("builds",db.queryForList("SELECT build_id,version,file_url,file_size,status,uploaded_at FROM game_builds WHERE game_id=? ORDER BY build_id DESC",id));
        result.put("media",db.queryForList("SELECT media_id,media_type,media_url,display_order FROM game_media WHERE game_id=? ORDER BY display_order,media_id",id));
        result.put("history",historyRows(id));
        result.put("publish_checks",publishChecks(result));
        return result;
    }

    private boolean validUrl(Object value) {
        if(value == null || value.toString().isBlank()) return false;
        try { var uri=URI.create(value.toString().trim()); return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) && uri.getHost()!=null && uri.getUserInfo()==null; }
        catch(IllegalArgumentException e) { return false; }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> publishChecks(Map<String,Object> game) {
        var builds=(List<Map<String,Object>>)game.get("builds");
        var media=(List<Map<String,Object>>)game.get("media");
        boolean basic=game.get("game_name")!=null && !game.get("game_name").toString().isBlank()
            && game.get("description")!=null && !game.get("description").toString().isBlank()
            && game.get("price") instanceof Number n && n.doubleValue()>=0;
        return List.of(
            Map.of("key","basic","label","基本資料完整（名稱、介紹、售價）","complete",basic,"mode","edit"),
            Map.of("key","tags","label","已設定至少一個遊戲標籤","complete",!((List<?>)game.get("tags")).isEmpty(),"mode","tags"),
            Map.of("key","builds","label","已設定啟用的遊戲版本檔案","complete",builds.stream().anyMatch(b->"ACTIVE".equals(b.get("status")) && b.get("version")!=null && !b.get("version").toString().isBlank() && validUrl(b.get("file_url"))),"mode","builds"),
            Map.of("key","media","label","已設定封面與宣傳媒體","complete",validUrl(game.get("cover_url")) && media.stream().anyMatch(m->validUrl(m.get("media_url"))),"mode","media")
        );
    }

    private List<Map<String,Object>> allowedTags(List<Map<String,Object>> tags) {
        return tags.stream().filter(t->ALLOWED_TAGS.contains(t.get("tag_name"))).sorted(Comparator.comparingInt(t->ALLOWED_TAGS.indexOf(t.get("tag_name")))).toList();
    }
    public Object tags(Actor actor) { check(actor); return allowedTags(db.queryForList("SELECT tag_id,tag_name FROM tags")); }
    public Object history(Actor actor,int id) { game(actor,id); return historyRows(id); }
    private List<Map<String,Object>> historyRows(int id) {
        return db.queryForList("SELECT l.game_audit_id AS decision_id,l.review_type,l.decision,l.comment,l.reviewed_at AS decided_at,a.admin_account AS reviewer_name FROM game_audit_logs l JOIN admin a ON a.admin_id=l.admin_id WHERE l.game_id=? ORDER BY l.game_audit_id DESC",id);
    }

    private String url(String value) {
        if(value == null || value.isBlank()) return null;
        try { var u=URI.create(value.trim()); if(!("https".equalsIgnoreCase(u.getScheme()) || "http".equalsIgnoreCase(u.getScheme())) || u.getHost()==null || u.getUserInfo()!=null) throw new IllegalArgumentException(); }
        catch(IllegalArgumentException e) { throw new Problem(400,"網址須使用完整的 http 或 https 網址"); }
        return value.trim();
    }
    private String name(String value) {
        if(value == null || value.trim().isEmpty()) throw new Problem(400,"請填寫遊戲名稱");
        return value.trim();
    }
    private void editable(Actor actor,int id) {
        var g=game(actor,id);
        if("ADMIN".equals(g.get("off_shelf_source"))) throw new Problem(409,"此遊戲已被管理員強制下架，請聯絡管理員處理");
        if(Boolean.TRUE.equals(g.get("can_restore"))) throw new Problem(409,"24小時恢復期間保留原審核資料，暫不可修改或重送審");
        if(g.get("pending_review_type")!=null || !Set.of("DRAFT","OFF_SHELF").contains(g.get("status")))
            throw new Problem(409,"只有未送審的草稿或已下架遊戲可以修改；已上架遊戲請先下架");
    }

    @Transactional
    public Object create(Actor actor,GameInput input) {
        check(actor);
        String cover=url(input.coverUrl()), title=name(input.name());
        var key=new GeneratedKeyHolder();
        db.update(connection -> {
            var ps=connection.prepareStatement("INSERT INTO games(member_id,game_name,price,description,cover_url,release_date,status) VALUES(?,?,?,?,?,?,'DRAFT')",Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1,actor.id()); ps.setString(2,title); ps.setBigDecimal(3,input.price()); ps.setString(4,input.description()); ps.setString(5,cover); ps.setObject(6,input.releaseDate()); return ps;
        },key);
        return game(actor,Objects.requireNonNull(key.getKey()).intValue());
    }

    @Transactional(isolation=Isolation.SERIALIZABLE)
    public Object update(Actor actor,int id,GameInput input) {
        editable(actor,id);
        db.update("UPDATE games SET game_name=?,price=?,description=?,cover_url=?,release_date=? WHERE game_id=? AND member_id=?",name(input.name()),input.price(),input.description(),url(input.coverUrl()),input.releaseDate(),id,actor.id());
        return game(actor,id);
    }

    @Transactional(isolation=Isolation.SERIALIZABLE)
    public Object request(Actor actor,int id,String type,RequestInput input) {
        var g=game(actor,id);
        String state=(String)g.get("status");
        if(g.get("pending_review_type")!=null) throw new Problem(409,"此遊戲已有待處理申請");
        if("PUBLISH".equals(type)) {
            if(!Set.of("DRAFT","OFF_SHELF").contains(state)) throw new Problem(409,"此狀態不能送出上架審核");
            if(g.get("description")==null || g.get("description").toString().isBlank()) throw new Problem(400,"送審前請填寫遊戲介紹");
            if(db.queryForObject("SELECT COUNT(*) FROM game_builds WHERE game_id=? AND status='ACTIVE'",Integer.class,id)==0) throw new Problem(400,"送審前請新增至少一個啟用版本");
        } else throw new Problem(400,"下架已改為直接處理，不需申請審核");
        editable(actor,id);
        @SuppressWarnings("unchecked") var checks=(List<Map<String,Object>>)g.get("publish_checks");
        var missing=checks.stream().filter(c->!Boolean.TRUE.equals(c.get("complete"))).map(c->c.get("label").toString()).toList();
        if(!missing.isEmpty()) throw new Problem(400,"上架設定未完成："+String.join("、",missing));
        db.update("UPDATE games SET pending_review_type=?,review_requested_at=CURRENT_TIMESTAMP,review_request_reason=?,pending_request_key=? WHERE game_id=? AND member_id=?",type,input.reason(),UUID.randomUUID().toString(),id,actor.id());
        return game(actor,id);
    }

    /** 下架即時生效，24小時僅是恢復窗口，不刪除遊戲與購買紀錄。 */
    @Transactional(isolation=Isolation.SERIALIZABLE)
    public Object offShelf(Actor actor,int id,RequestInput input) {
        var g=game(actor,id);
        if("ADMIN".equals(g.get("off_shelf_source"))) throw new Problem(409,"管理員強制下架不能由發行商變更");
        if(g.get("pending_review_type")!=null || !Set.of("PUBLISHED","COMING_SOON").contains(g.get("status")))
            throw new Problem(409,"只有沒有待審申請的已上架或即將上架遊戲可直接下架");
        long now=clock.millis();
        db.update("UPDATE games SET status='OFF_SHELF',off_shelf_source='PUBLISHER',off_shelf_at=?,restore_until=?,off_shelf_previous_status=?,off_shelf_reason=?,off_shelf_admin_id=NULL WHERE game_id=? AND member_id=?",
            now,now+24L*60*60*1000,g.get("status"),input.reason(),id,actor.id());
        return game(actor,id);
    }

    @Transactional(isolation=Isolation.SERIALIZABLE)
    public Object restore(Actor actor,int id) {
        var g=game(actor,id);
        if(!Boolean.TRUE.equals(g.get("can_restore"))) throw new Problem(409,"恢復期限已過，或此遊戲已被管理員強制下架");
        String previous=(String)g.get("off_shelf_previous_status");
        if(previous==null || !Set.of("PUBLISHED","COMING_SOON").contains(previous)) throw new Problem(409,"原上架狀態不存在，請聯絡管理員");
        if(db.update("UPDATE games SET status=?,off_shelf_source=NULL,off_shelf_at=NULL,restore_until=NULL,off_shelf_previous_status=NULL,off_shelf_reason=NULL,off_shelf_admin_id=NULL WHERE game_id=? AND member_id=? AND status='OFF_SHELF' AND off_shelf_source='PUBLISHER' AND restore_until>? AND pending_review_type IS NULL",
            previous,id,actor.id(),clock.millis())!=1) throw new Problem(409,"恢復期限已過或狀態已改變");
        return game(actor,id);
    }

    @Transactional(isolation=Isolation.SERIALIZABLE)
    public Object forceOffShelf(Actor actor,int id,RequestInput input) {
        if(actor==null || !actor.isAdmin()) throw new Problem(403,"只有管理員可以強制下架遊戲");
        if(input.reason()==null || input.reason().isBlank() || input.reason().trim().length()>480) throw new Problem(400,"請填寫強制下架原因，最多480字");
        var rows=db.queryForList("SELECT status,off_shelf_source FROM games WHERE game_id=?",id);
        if(rows.isEmpty()) throw new Problem(404,"找不到此遊戲");
        if("ADMIN".equals(rows.getFirst().get("off_shelf_source"))) throw new Problem(409,"此遊戲已被強制下架");
        db.update("UPDATE games SET status='OFF_SHELF',off_shelf_source='ADMIN',off_shelf_at=?,restore_until=NULL,off_shelf_previous_status=?,off_shelf_reason=?,off_shelf_admin_id=?,pending_review_type=NULL,review_requested_at=NULL,review_request_reason=NULL,pending_request_key=NULL WHERE game_id=?",
            clock.millis(),rows.getFirst().get("status"),input.reason().trim(),actor.id(),id);
        db.update("INSERT INTO game_audit_logs(game_id,admin_id,review_type,decision,comment) VALUES(?,?,'OFF_SHELF','APPROVED',?)",id,actor.id(),"[管理員強制下架] "+input.reason().trim());
        return Map.of("gameId",id,"status","OFF_SHELF","message","已強制下架，發行商不能自行恢復或送審");
    }

    @Transactional(isolation=Isolation.SERIALIZABLE)
    public Object saveTags(Actor actor,int id,TagsInput input) {
        editable(actor,id);
        var ids=new LinkedHashSet<>(input.tagIds());
        for(int tag:ids) {
            var names=db.queryForList("SELECT tag_name FROM tags WHERE tag_id=?",String.class,tag);
            if(names.size()!=1 || !ALLOWED_TAGS.contains(names.getFirst())) throw new Problem(400,"只能選擇平台指定的8種遊戲標籤");
        }
        db.update("DELETE FROM game_tags WHERE game_id=?",id);
        for(int tag:ids) db.update("INSERT INTO game_tags(game_id,tag_id) VALUES(?,?)",id,tag);
        return game(actor,id);
    }

    @Transactional(isolation=Isolation.SERIALIZABLE)
    public Object saveBuilds(Actor actor,int id,BuildsInput input) {
        editable(actor,id);
        var seen=new HashSet<Integer>();
        for(var build:input.builds()) {
            if(build.version().isBlank()) throw new Problem(400,"版本不可為空白");
            String file=url(build.fileUrl());
            if(file==null) throw new Problem(400,"請填寫版本檔案網址");
            if(build.id()==null) db.update("INSERT INTO game_builds(game_id,version,file_url,file_size,status) VALUES(?,?,?,?,?)",id,build.version().trim(),file,build.fileSize(),build.status());
            else {
                if(!seen.add(build.id())) throw new Problem(400,"版本不可重複");
                if(db.update("UPDATE game_builds SET version=?,file_url=?,file_size=?,status=? WHERE build_id=? AND game_id=?",build.version().trim(),file,build.fileSize(),build.status(),build.id(),id)!=1) throw new Problem(404,"版本不屬於此遊戲");
            }
        }
        return game(actor,id);
    }

    @Transactional(isolation=Isolation.SERIALIZABLE)
    public Object saveMedia(Actor actor,int id,MediaList input) {
        editable(actor,id);
        for(var media:input.media()) if(url(media.url())==null) throw new Problem(400,"請填寫媒體網址");
        db.update("DELETE FROM game_media WHERE game_id=?",id);
        for(var media:input.media()) db.update("INSERT INTO game_media(game_id,media_type,media_url,display_order) VALUES(?,?,?,?)",id,media.type(),url(media.url()),media.order());
        return game(actor,id);
    }

    private void reviewer(Actor actor) {
        if(actor==null || !actor.canReview()) throw new Problem(403,"只有審核人員與管理者可以操作");
    }
    private static final String REVIEW_FROM=" FROM games g JOIN members m ON m.member_id=g.member_id LEFT JOIN game_audit_logs l ON l.game_audit_id=(SELECT MAX(a.game_audit_id) FROM game_audit_logs a WHERE a.game_id=g.game_id) ";
    private static final String REVIEW_STATUS="CASE WHEN g.pending_review_type IS NOT NULL THEN 'PENDING' ELSE l.decision END";
    public Object reviews(Actor actor,String status,String q,int page) {
        reviewer(actor);
        if(!Set.of("PENDING","APPROVED","REJECTED","ALL").contains(status) || page<0 || page>100000) throw new Problem(400,"無效的查詢条件");
        String p=pattern(q), where=" WHERE (g.pending_review_type IS NOT NULL OR l.game_audit_id IS NOT NULL) AND (g.game_name LIKE ? ESCAPE '!' OR m.username LIKE ? ESCAPE '!') AND (?='ALL' OR "+REVIEW_STATUS+"=?)";
        var total=db.queryForObject("SELECT COUNT(*)"+REVIEW_FROM+where,Long.class,p,p,status,status);
        var items=db.queryForList("SELECT g.game_id AS review_id,g.game_name,m.username AS submitter_name,"+REVIEW_STATUS+" AS status,COALESCE(g.review_requested_at,l.reviewed_at) AS submitted_at,COALESCE(g.pending_review_type,l.review_type) AS review_type"+REVIEW_FROM+where+" ORDER BY g.game_id DESC OFFSET ? ROWS FETCH NEXT 20 ROWS ONLY",p,p,status,status,page*20);
        return Map.of("items",items,"total",total);
    }
    public Object reviewDetail(Actor actor,int id) {
        reviewer(actor);
        var rows=db.queryForList("SELECT g.game_id AS review_id,g.game_name,g.description,g.price,g.release_date AS release_at,g.pending_request_key AS request_key,g.review_request_reason AS request_reason,m.username AS submitter_name,"+REVIEW_STATUS+" AS status,COALESCE(g.review_requested_at,l.reviewed_at) AS submitted_at,COALESCE(g.pending_review_type,l.review_type) AS review_type,l.comment AS review_comment"+REVIEW_FROM+" WHERE g.game_id=? AND (g.pending_review_type IS NOT NULL OR l.game_audit_id IS NOT NULL)",id);
        if(rows.isEmpty()) throw new Problem(404,"找不到此審核項目");
        var item=new LinkedHashMap<>(rows.getFirst());
        var builds=db.queryForList("SELECT version,file_url,status FROM game_builds WHERE game_id=? ORDER BY build_id DESC",id);
        item.put("build_reference",builds.stream().map(x->x.get("version")+" ("+x.get("status")+")").reduce((a,c)->a+", "+c).orElse("尚無版本"));
        item.put("builds",builds);
        item.put("media",db.queryForList("SELECT media_type,media_url FROM game_media WHERE game_id=? ORDER BY display_order,media_id",id));
        item.put("tags",db.queryForList("SELECT t.tag_name FROM tags t JOIN game_tags gt ON gt.tag_id=t.tag_id WHERE gt.game_id=?",id));
        return Map.of("item",item,"history",historyRows(id));
    }

    @Transactional(isolation=Isolation.SERIALIZABLE)
    public Object decide(Actor actor,int id,DecisionInput input) {
        reviewer(actor);
        if(input.comment().isBlank()) throw new Problem(400,"請填寫處理說明");
        var rows=db.queryForList("SELECT status,pending_review_type,pending_request_key FROM games WHERE game_id=?",id);
        if(rows.isEmpty()) throw new Problem(404,"找不到此遊戲");
        var g=rows.getFirst();
        if(g.get("pending_review_type")==null || !input.requestKey().equals(g.get("pending_request_key"))) throw new Problem(409,"申請已處理或取消，請重新載入");
        String type=(String)g.get("pending_review_type"), state=(String)g.get("status");
        if(!"PUBLISH".equals(type)) throw new Problem(409,"下架已改為直接處理，請重新載入");
        if(input.decision().equals("APPROVED")) state=type.equals("PUBLISH")?"PUBLISHED":"OFF_SHELF";
        String reset=input.decision().equals("APPROVED") ? ",off_shelf_source=NULL,off_shelf_at=NULL,restore_until=NULL,off_shelf_previous_status=NULL,off_shelf_reason=NULL,off_shelf_admin_id=NULL" : "";
        if(db.update("UPDATE games SET status=?,pending_review_type=NULL,review_requested_at=NULL,review_request_reason=NULL,pending_request_key=NULL"+reset+" WHERE game_id=? AND pending_request_key=?",state,id,input.requestKey())!=1) throw new Problem(409,"申請已改變");
        db.update("INSERT INTO game_audit_logs(game_id,admin_id,review_type,decision,comment) VALUES(?,?,?,?,?)",id,actor.id(),type,input.decision(),input.comment().trim());
        return reviewDetail(actor,id);
    }
}
