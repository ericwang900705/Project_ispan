package gameplatform.backoffice;

import gameplatform.admin.service.AdminPermissionService;
import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class BackofficeService {
    private final JdbcTemplate db;
    private final AdminPermissionService permissions;
    static final Map<String, String> ACCESS = Map.of("orders", "ORDER_VIEW", "refunds", "REFUND_MANAGE", "reviews",
            "COMMENT_REVIEW", "review-reports", "COMMENT_REVIEW", "tags", "TAG_MANAGE", "coupons", "COUPON_MANAGE",
            "login-logs", "LOGIN_LOG_VIEW", "operations", "OPERATION_LOG_VIEW");

    public BackofficeService(JdbcTemplate db, AdminPermissionService permissions) {
        this.db = db;
        this.permissions = permissions;
    }

    void require(Actor actor, String area) {
        if("system-logs".equals(area)) {
            if(actor==null||permissions.forActor(actor).stream().noneMatch(c->c.equals("OPERATION_LOG_VIEW")||c.equals("LOGIN_LOG_VIEW")))throw new Problem(403,"未授權使用此功能");
            return;
        }
        String code = ACCESS.get(area);
        if (code == null)
            throw new Problem(404, "找不到管理功能");
        if (actor == null || !permissions.forActor(actor).contains(code))
            throw new Problem(403, "未授權使用此功能");
    }

    static Problem bad(String message) {
        return new Problem(400, message);
    }

    static int number(Object o) {
        return ((Number) o).intValue();
    }

    static String text(String value, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max)
            throw bad("請填寫必填欄位，並確認字數限制");
        return value.trim();
    }

    static int page(int value) {
        if (value < 0 || value > 100000)
            throw bad("分頁範圍不正確");
        return value;
    }

    private Map<String, Object> one(String sql, Object... args) {
        var rows = db.queryForList(sql, args);
        if (rows.isEmpty())
            throw new Problem(404, "找不到資料");
        return rows.getFirst();
    }

    private void audit(Actor a, String action, int id, String detail) {
        db.update("INSERT INTO dbo.admin_operation_logs(admin_id,action_code,target_id,detail) VALUES(?,?,?,?)", a.id(),
                action, id, detail);
    }

    // Fixed query definitions and bind parameters only; request data never becomes
    // a SQL identifier.
    private String operationSource(){return "(SELECT CONCAT('M-',operation_id) AS operation_id,admin_id,action_code,target_id,detail,created_at,N'後台操作' AS source_name FROM dbo.admin_operation_logs "
                        + "UNION ALL SELECT CONCAT('G-',game_audit_id),admin_id,CONCAT('GAME_',review_type,'_',decision),game_id,comment,reviewed_at,N'遊戲審核' FROM dbo.game_audit_logs "
                        + "UNION ALL SELECT CONCAT('S-',audit_id),actor_admin_id,CONCAT('SUPPORT_',action_code),COALESCE(ticket_id,support_room_id),CONCAT(CASE WHEN ticket_id IS NOT NULL THEN N'客服案件 #' ELSE N'客服聊天室 #' END,COALESCE(ticket_id,support_room_id)),created_at,N'客服操作' FROM dbo.support_audit_logs WHERE actor_admin_id IS NOT NULL "
                        + "UNION ALL SELECT CONCAT('P-',application_id),admin_id,CONCAT('PUBLISHER_',approval_status),member_id,review_comment,reviewed_at,N'發行商審核' FROM dbo.publisher_applications WHERE approval_status IN ('APPROVED','REJECTED') AND reviewed_at IS NOT NULL) l LEFT JOIN dbo.admin a ON a.admin_id=l.admin_id";}

    public Map<String, Object> list(Actor actor, String area, String keyword, String status, int page) {
        require(actor, area);
        int offset = page(page) * 20;
        String from, select, search, filter = "", order;
        switch (area) {
            case "orders", "refunds" -> {
                from = "dbo.orders o JOIN dbo.members m ON m.member_id=o.member_id";
                select = "o.order_id,o.order_no,m.username,o.order_status,o.total_amount,o.created_at,o.paid_at,o.payment_type";
                search = "CONCAT(o.order_no,' ',m.username,' ',o.order_id)";
                order = "o.created_at DESC,o.order_id DESC";
                filter = area.equals("refunds") ? " AND o.order_status='REFUNDED'" : "";
                if (!status.isEmpty()) {
                    if (!Set.of("PENDING", "PAID", "CANCELLED", "REFUNDED", "FAILED", "EXPIRED").contains(status))
                        throw bad("訂單狀態不正確");
                    filter += " AND o.order_status=?";
                }
            }
            case "reviews" -> {
                from = "dbo.game_reviews r JOIN dbo.members m ON m.member_id=r.member_id JOIN dbo.games g ON g.game_id=r.game_id";
                select = "r.review_id,r.game_id,g.game_name,m.username,r.rating,r.content,r.status,r.created_at";
                search = "CONCAT(g.game_name,' ',m.username,' ',r.content)";
                order = "r.created_at DESC,r.review_id DESC";
                if (!status.isEmpty()) {
                    if (!Set.of("NORMAL", "HIDDEN", "DELETED").contains(status))
                        throw bad("評論狀態不正確");
                    filter = " AND r.status=?";
                }
            }
            case "review-reports" -> {
                from = "dbo.ugc_reports r JOIN dbo.members m ON m.member_id=r.reporter_member_id LEFT JOIN dbo.game_reviews v ON v.review_id=r.target_id";
                select = "r.report_id,r.target_id,m.username,r.snapshot_text,r.reason,r.status,r.created_at,r.review_comment,v.content AS current_content";
                search = "CONCAT(r.report_id,' ',r.target_id,' ',m.username,' ',r.reason)";
                order = "r.created_at DESC,r.report_id DESC";
                filter = " AND r.target_type='REVIEW'";
                if (!status.isEmpty()) {
                    if (!Set.of("PENDING", "REMOVED", "DISMISSED").contains(status))
                        throw bad("檢舉狀態不正確");
                    filter += " AND r.status=?";
                }
            }
            case "tags" -> {
                from = "dbo.tags t";
                select = "t.tag_id,t.tag_name,(SELECT COUNT(*) FROM dbo.game_tags g WHERE g.tag_id=t.tag_id) AS game_count";
                search = "t.tag_name";
                order = "t.tag_id DESC";
                if (!status.isEmpty())
                    throw bad("此頁不提供狀態篩選");
            }
            case "coupons" -> {
                from = "dbo.coupons c LEFT JOIN dbo.coupon_rules cr ON cr.coupon_id=c.coupon_id LEFT JOIN dbo.members cm ON cm.member_id=cr.publisher_member_id LEFT JOIN dbo.tags ct ON ct.tag_id=cr.tag_id";
                select = "c.coupon_id,c.coupon_name,c.discount_type,c.discount_value,c.min_spend,c.total_limit,c.per_member_limit,c.used_count,c.status,c.start_at,c.expires_at,COALESCE(cr.scope_type,'ALL') AS scope_type,cr.publisher_member_id,cr.tag_id,cm.username AS publisher_name,ct.tag_name";
                search = "c.coupon_name";
                order = "c.coupon_id DESC";
                if (!status.isEmpty()) {
                    if (!Set.of("ACTIVE", "INACTIVE", "EXPIRED").contains(status))
                        throw bad("優惠券狀態不正確");
                    filter = " AND c.status=?";
                }
            }
            case "login-logs" -> {
                from = "dbo.member_login_logs l LEFT JOIN dbo.members m ON m.member_id=l.member_id";
                select = "l.login_log_id,COALESCE(m.username,N'未識別帳號') AS username,l.ip_address,l.login_status,l.login_at";
                search = "CONCAT(m.username,' ',l.ip_address)";
                order = "l.login_at DESC,l.login_log_id DESC";
                if (!status.isEmpty()) {
                    if (!Set.of("SUCCESS", "FAILED", "LOCKED").contains(status))
                        throw bad("登入狀態不正確");
                    filter = " AND l.login_status=?";
                }
            }
            case "system-logs" -> {
                var granted=permissions.forActor(actor);var sources=new ArrayList<String>();
                if(granted.contains("OPERATION_LOG_VIEW"))sources.add("SELECT CONCAT('O-',l.operation_id) AS record_id,l.source_name,a.admin_account AS actor_name,l.action_code,l.target_id,l.detail,l.created_at FROM "+operationSource());
                if(granted.contains("LOGIN_LOG_VIEW"))sources.add("SELECT CONCAT('L-',l.login_log_id) AS record_id,N'會員登入' AS source_name,COALESCE(m.username,N'未識別帳號') AS actor_name,CONCAT('MEMBER_LOGIN_',l.login_status) AS action_code,l.member_id AS target_id,CONCAT(N'連線 IP：',l.ip_address) AS detail,l.login_at AS created_at FROM dbo.member_login_logs l LEFT JOIN dbo.members m ON m.member_id=l.member_id");
                from="("+String.join(" UNION ALL ",sources)+") l";select="l.record_id,l.source_name,l.actor_name,l.action_code,l.target_id,l.detail,l.created_at";
                search="CONCAT(l.source_name,' ',l.actor_name,' ',l.action_code,' ',l.detail,' ',l.target_id)";order="l.created_at DESC,l.record_id DESC";
                if(!status.isEmpty())throw bad("請到分類頁篩選紀錄狀態");
            }
            case "operations" -> {
                // 以現有紀錄彙整，不補造歷史；客服只顯示動作與案件編號，不公開備註內容。
                from = operationSource();
                select = "l.operation_id,l.source_name,a.admin_account,l.action_code,l.target_id,l.detail,l.created_at";
                search = "CONCAT(a.admin_account,' ',l.source_name,' ',l.operation_id,' ',l.target_id,' ',l.action_code,' ',l.detail)";
                order = "l.created_at DESC,l.operation_id DESC";
                if (!status.isEmpty())
                    throw bad("此頁不提供狀態篩選");
            }
            default -> throw new Problem(404, "找不到管理功能");
        }
        if (keyword == null || keyword.length() > 100)
            throw bad("搜尋字數最多 100 字");
        // 空白搜尋代表完整列表，不以空字串的 CHARINDEX 結果決定是否顯示。
        String term = keyword.trim();
        String where = (term.isEmpty() ? " WHERE 1=1" : " WHERE CHARINDEX(?," + search + ")>0") + filter;
        var args = new ArrayList<Object>();
        if (!term.isEmpty()) args.add(term);
        if (!status.isEmpty())
            args.add(status);
        Long total = db.queryForObject("SELECT COUNT_BIG(*) FROM " + from + where, Long.class, args.toArray());
        var paging = new ArrayList<>(args);
        paging.add(offset);
        var rows = db.queryForList("SELECT " + select + " FROM " + from + where + " ORDER BY " + order
                + " OFFSET ? ROWS FETCH NEXT 20 ROWS ONLY", paging.toArray());
        return Map.of("items", rows, "total", total == null ? 0 : total, "page", page, "size", 20);
    }

    public Object order(Actor actor, int id, boolean refund) {
        require(actor, refund ? "refunds" : "orders");
        var order = one(
                "SELECT o.order_id,o.order_no,o.order_status,o.subtotal_amount,o.discount_amount,o.total_amount,o.points_earned,o.created_at,o.paid_at,o.payment_type,o.ecpay_trade_no,m.username FROM dbo.orders o JOIN dbo.members m ON m.member_id=o.member_id WHERE o.order_id=?"
                        + (refund ? " AND o.order_status='REFUNDED'" : ""),
                id);
        var items = db.queryForList(
                "SELECT i.order_item_id,g.game_name,i.quantity,i.unit_price,i.discount_amount,i.final_amount,i.is_gift,m.username AS recipient FROM dbo.order_items i JOIN dbo.games g ON g.game_id=i.game_id LEFT JOIN dbo.members m ON m.member_id=i.recipient_member_id WHERE i.order_id=? ORDER BY i.order_item_id",
                id);
        return Map.of("order", order, "items", items);
    }

    public record TagInput(String name) {
    }

    @Transactional
    public void saveTag(Actor actor, Integer id, TagInput input) {
        require(actor, "tags");
        String name = text(input == null ? null : input.name(), 50);
        if (id == null) {
            Integer created = db.queryForObject("INSERT INTO dbo.tags(tag_name) OUTPUT INSERTED.tag_id VALUES(?)",
                    Integer.class, name);
            audit(actor, "TAG_CREATE", Objects.requireNonNull(created), name);
        } else {
            if (db.update("UPDATE dbo.tags SET tag_name=? WHERE tag_id=?", name, id) != 1)
                throw new Problem(404, "找不到標籤");
            audit(actor, "TAG_UPDATE", id, name);
        }
    }

    @Transactional
    public void deleteTag(Actor actor, int id) {
        require(actor, "tags");
        if (db.queryForObject("SELECT COUNT(*) FROM dbo.game_tags WHERE tag_id=?", Integer.class, id) > 0)
            throw new Problem(409, "此標籤已有遊戲使用，請先移除關聯");
        if (db.update("DELETE FROM dbo.tags WHERE tag_id=?", id) != 1)
            throw new Problem(404, "找不到標籤");
        audit(actor, "TAG_DELETE", id, "刪除未使用的標籤");
    }

    public record CouponInput(String name, String type, BigDecimal value, BigDecimal minimum, Integer totalLimit,
            Integer memberLimit, String status, LocalDateTime start, LocalDateTime end,
            String scopeType, Integer publisherId, Integer tagId) {
        public CouponInput(String name,String type,BigDecimal value,BigDecimal minimum,Integer totalLimit,Integer memberLimit,String status,LocalDateTime start,LocalDateTime end) {
            this(name,type,value,minimum,totalLimit,memberLimit,status,start,end,"ALL",null,null);
        }
    }

    static void validateCoupon(CouponInput c) {
        if (c == null)
            throw bad("請填寫優惠券資料");
        text(c.name(), 100);
        String scope=Objects.toString(c.scopeType(),"ALL");
        if(!Set.of("ALL","PUBLISHER","TAG").contains(scope)
            || (scope.equals("PUBLISHER") && (c.publisherId()==null || c.publisherId()<1 || c.tagId()!=null))
            || (scope.equals("TAG") && (c.tagId()==null || c.tagId()<1 || c.publisherId()!=null))
            || (scope.equals("ALL") && (c.publisherId()!=null || c.tagId()!=null))) throw bad("優惠券使用範圍不正確");
        if (!Set.of("RATE", "AMOUNT").contains(Objects.toString(c.type(), "")) || c.value() == null
                || c.value().signum() <= 0 || c.value().scale() > 2
                || c.value().compareTo(new BigDecimal("99999999.99")) > 0)
            throw bad("優惠券折扣值不正確");
        if (c.type().equals("RATE") && c.value().compareTo(BigDecimal.ONE) >= 0)
            throw bad("折扣率須大於 0 且小於 1，例如 0.80 表示八折");
        if (c.minimum() == null || c.minimum().signum() < 0 || c.minimum().scale() > 2
                || c.minimum().compareTo(new BigDecimal("99999999.99")) > 0 || c.memberLimit() == null
                || c.memberLimit() < 1 || (c.totalLimit() != null && c.totalLimit() < 1))
            throw bad("金額與使用次數不正確");
        if (!Set.of("ACTIVE", "INACTIVE", "EXPIRED").contains(Objects.toString(c.status(), "")) || c.start() == null
                || c.end() == null || !c.end().isAfter(c.start()))
            throw bad("狀態或優惠券時間不正確");
    }

    @Transactional
    public void saveCoupon(Actor actor, Integer id, CouponInput c) {
        require(actor, "coupons");
        validateCoupon(c);
        if("PUBLISHER".equals(c.scopeType()) && db.queryForObject("SELECT COUNT(*) FROM dbo.members WHERE member_id=? AND is_publisher=1",Integer.class,c.publisherId())!=1) throw bad("請選擇有效的發行商");
        if("TAG".equals(c.scopeType()) && db.queryForObject("SELECT COUNT(*) FROM dbo.tags WHERE tag_id=?",Integer.class,c.tagId())!=1) throw bad("請選擇有效的遊戲類型");
        boolean creating = id == null;
        if (id == null)
            id = db.queryForObject(
                    "INSERT INTO dbo.coupons(coupon_name,discount_type,discount_value,min_spend,total_limit,per_member_limit,status,start_at,expires_at) OUTPUT INSERTED.coupon_id VALUES(?,?,?,?,?,?,?,?,?)",
                    Integer.class, c.name().trim(), c.type(), c.value(), c.minimum(), c.totalLimit(), c.memberLimit(),
                    c.status(), c.start(), c.end());
        else {
            if (db.update("UPDATE dbo.coupons SET used_count=used_count WHERE coupon_id=?", id) != 1)
                throw new Problem(404, "找不到優惠券");
            var old = one("SELECT used_count FROM dbo.coupons WHERE coupon_id=?", id);
            if (c.totalLimit() != null && c.totalLimit() < number(old.get("used_count")))
                throw bad("總額度不可低於已使用次數");
            db.update(
                    "UPDATE dbo.coupons SET coupon_name=?,discount_type=?,discount_value=?,min_spend=?,total_limit=?,per_member_limit=?,status=?,start_at=?,expires_at=? WHERE coupon_id=?",
                    c.name().trim(), c.type(), c.value(), c.minimum(), c.totalLimit(), c.memberLimit(), c.status(),
                    c.start(), c.end(), id);
        }
        db.update("DELETE FROM dbo.coupon_rules WHERE coupon_id=?",id);
        db.update("INSERT INTO dbo.coupon_rules(coupon_id,scope_type,publisher_member_id,tag_id) VALUES(?,?,?,?)",id,Objects.toString(c.scopeType(),"ALL"),c.publisherId(),c.tagId());
        audit(actor, creating ? "COUPON_CREATE" : "COUPON_UPDATE", Objects.requireNonNull(id), c.name().trim());
    }

    public Object couponOptions(Actor actor) {
        require(actor,"coupons");
        return Map.of("publishers",db.queryForList("SELECT member_id AS id,username AS name FROM dbo.members WHERE is_publisher=1 ORDER BY username"),
            "tags",db.queryForList("SELECT tag_id AS id,tag_name AS name FROM dbo.tags ORDER BY tag_name"));
    }

    public record ReviewInput(String status, String reason) {
    }

    public Object reviewModeration(Actor actor,int id){
        require(actor,"reviews");var review=one("SELECT member_id FROM dbo.game_reviews WHERE review_id=?",id);
        return new gameplatform.moderation.ReviewModerationPolicy(db).status(number(review.get("member_id")));
    }

    @Transactional
    public void moderate(Actor actor,int id,ReviewInput input){
        require(actor,"reviews");
        if(input==null||!Set.of("NORMAL","HIDDEN").contains(Objects.toString(input.status(),"")))throw bad("審核狀態不正確");
        String reason=text(input.reason(),500);
        var rows=db.queryForList("SELECT member_id FROM dbo.game_reviews WHERE review_id=?",id);
        if(rows.isEmpty())throw new Problem(409,"評論不存在或狀態已改變");
        int member=number(rows.getFirst().get("member_id"));var policy=new gameplatform.moderation.ReviewModerationPolicy(db);policy.lockMember(member);
        if(db.update("UPDATE dbo.game_reviews SET status=?,updated_at=CURRENT_TIMESTAMP WHERE review_id=? AND status IN ('NORMAL','HIDDEN')",input.status(),id)!=1)throw new Problem(409,"評論不存在、已由會員刪除或狀態已改變");
        if("HIDDEN".equals(input.status()))policy.confirm(actor,id,member,null,reason);else policy.reverse(actor,id,member);
        audit(actor,"REVIEW_"+input.status(),id,reason);
    }

    public record ReportInput(boolean remove,String reason){}

    @Transactional
    public void report(Actor actor,int id,ReportInput input){
        require(actor,"review-reports");String reason=text(input==null?null:input.reason(),500);
        var peek=one("SELECT * FROM dbo.ugc_reports WHERE report_id=? AND target_type='REVIEW'",id);
        if(!"PENDING".equals(peek.get("status")))throw new Problem(409,"檢舉已處理，請重新整理");
        int reviewId=number(peek.get("target_id"));Integer member=null;var policy=new gameplatform.moderation.ReviewModerationPolicy(db);
        if(input.remove()){
            var review=one("SELECT content,status,member_id FROM dbo.game_reviews WHERE review_id=?",reviewId);
            if(!Objects.equals(peek.get("snapshot_text"),review.get("content")))throw new Problem(409,"評論內容與檢舉快照不同，請重新核對");
            member=number(review.get("member_id"));policy.lockMember(member);
        }
        var report=one("SELECT * FROM dbo.ugc_reports WITH (UPDLOCK,ROWLOCK) WHERE report_id=? AND target_type='REVIEW'",id);
        if(!"PENDING".equals(report.get("status")))throw new Problem(409,"檢舉已處理，請重新整理");
        if(input.remove()){
            var review=one("SELECT content,status FROM dbo.game_reviews WITH (UPDLOCK,ROWLOCK) WHERE review_id=?",reviewId);
            if(!Objects.equals(report.get("snapshot_text"),review.get("content")))throw new Problem(409,"評論內容與檢舉快照不同，請重新核對");
            if(!Set.of("NORMAL","HIDDEN").contains(review.get("status")))throw new Problem(409,"評論已由會員刪除，請重新確認");
            db.update("UPDATE dbo.game_reviews SET status='HIDDEN',updated_at=CURRENT_TIMESTAMP WHERE review_id=? AND status='NORMAL'",reviewId);
            policy.confirm(actor,reviewId,member,id,reason);
        }
        if(db.update("UPDATE dbo.ugc_reports SET status=?,admin_id=?,reviewed_at=CURRENT_TIMESTAMP,review_comment=? WHERE report_id=? AND status='PENDING'",input.remove()?"REMOVED":"DISMISSED",actor.id(),reason,id)!=1)throw new Problem(409,"檢舉已處理，請重新整理");
        audit(actor,input.remove()?"REPORT_REMOVE":"REPORT_DISMISS",id,reason);
    }
}
