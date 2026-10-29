package gameplatform.shop;

import gameplatform.support.model.Actor;
import gameplatform.support.model.EventHub;
import gameplatform.support.model.Problem;
import gameplatform.support.config.StaffPermissionService;
import gameplatform.support.service.NotificationService;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 集中處理整筆訂單退款；測試退款與已完成的人工退款分開記錄。 */
@Service
public class RefundService {
    private final JdbcTemplate db;
    private final StaffPermissionService permissions;
    private final NotificationService notifications;
    private final EventHub events;
    public RefundService(JdbcTemplate db, StaffPermissionService permissions, NotificationService notifications, EventHub events) {
        this.db=db; this.permissions=permissions; this.notifications=notifications; this.events=events;
    }
    private void require(Actor actor) {
        if (actor==null || !actor.isStaff() || !permissions.forActor(actor).contains("REFUND_MANAGE"))
            throw Problem.denied();
    }
    private static int id(Object value) { return ((Number)value).intValue(); }
    private static final String SELECT = "SELECT r.*,o.order_no,o.order_status,o.total_amount,o.payment_type,o.payment_environment,o.paid_at,o.ecpay_trade_no,m.username,t.ticket_no,a.admin_name AS processed_by_name FROM refund_requests r JOIN orders o ON o.order_id=r.order_id AND o.member_id=r.member_id JOIN members m ON m.member_id=r.member_id LEFT JOIN customer_service_tickets t ON t.ticket_id=r.ticket_id LEFT JOIN admin a ON a.admin_id=r.processed_by_admin_id ";

    public Map<String,Object> list(Actor actor, String q, String status, int page) {
        require(actor);
        if (page<0 || page>100000 || q.length()>100 || !Set.of("","PENDING","REFUNDED","REJECTED").contains(status))
            throw new Problem(400,"搜尋條件無效");
        var args=new ArrayList<Object>(); var where=new StringBuilder(" WHERE 1=1");
        if (!q.isBlank()) {
            where.append(" AND (m.username LIKE ? OR o.order_no LIKE ? OR t.ticket_no LIKE ?)");
            for(int i=0;i<3;i++)args.add("%"+q.strip()+"%");
        }
        if(!status.isEmpty()){where.append(" AND r.status=?");args.add(status);}
        String from=" FROM refund_requests r JOIN orders o ON o.order_id=r.order_id AND o.member_id=r.member_id JOIN members m ON m.member_id=r.member_id LEFT JOIN customer_service_tickets t ON t.ticket_id=r.ticket_id ";
        int total=Objects.requireNonNull(db.queryForObject("SELECT COUNT(*)"+from+where,Integer.class,args.toArray()));
        args.add(page*20);
        return Map.of("items",db.queryForList(SELECT+where+" ORDER BY r.refund_id DESC OFFSET ? ROWS FETCH NEXT 20 ROWS ONLY",args.toArray()),"total",total);
    }
    public Map<String,Object> detail(Actor actor,int refundId) {
        require(actor);var row=find(refundId);
        row.put("items",db.queryForList("SELECT i.game_id,g.game_name,i.unit_price,i.discount_amount,i.is_gift,i.recipient_member_id FROM order_items i JOIN games g ON g.game_id=i.game_id WHERE i.order_id=? ORDER BY i.order_item_id",row.get("order_id")));
        row.put("playTimes", db.queryForList("SELECT mg.game_id,g.game_name,mg.total_play_minutes,mg.last_played_at "
                + "FROM member_games mg JOIN games g ON g.game_id=mg.game_id "
                + "WHERE mg.order_item_id IN (SELECT order_item_id FROM order_items WHERE order_id=?) "
                + "ORDER BY g.game_name", row.get("order_id")));
        return row;
    }
    private Map<String,Object> find(int refundId) {
        var rows=db.queryForList(SELECT+" WHERE r.refund_id=?",refundId);
        if(rows.isEmpty())throw Problem.missing();return new LinkedHashMap<>(rows.getFirst());
    }
    /** 只有需要補件或諮詢時，才為退款申請建立關聯客服案件。 */
    @Transactional
    public Map<String,Object> openSupportCase(Actor actor,int refundId,String note) {
        require(actor);
        if (note==null || note.isBlank() || note.length()>2000) throw new Problem(400,"請填寫需要會員補充的資料（最多 2000 字）");
        var row=find(refundId);
        if (row.get("ticket_id")!=null) return Map.of("ticketId",row.get("ticket_id"),"message","此筆退款已有關聯客服案件");
        if (!"PENDING".equals(row.get("status"))) throw new Problem(409,"只有待處理的退款申請可建立補件案件");
        var categories=db.queryForList("SELECT category_id FROM customer_service_categories WHERE category_name=?",Integer.class,"退款問題");
        if(categories.isEmpty())throw new Problem(409,"退款問題分類尚未建立");
        Long number=db.queryForObject("SELECT NEXT VALUE FOR customer_service_ticket_no_seq",Long.class);
        String ticketNo=String.format(Locale.ROOT,"CS%010d",number);
        int member=id(row.get("member_id")),order=id(row.get("order_id"));
        var keys=db.queryForList("INSERT INTO customer_service_tickets(ticket_no,member_id,category_id,subject,content,status,priority,created_at,updated_at) OUTPUT INSERTED.ticket_id VALUES(?,?,?,?,?,'OPEN','NORMAL',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",Integer.class,
                ticketNo,member,categories.getFirst(),"退款補件｜訂單 #"+order,note.strip());
        int ticketId=keys.getFirst();
        db.update("INSERT INTO support_order_links(ticket_id,member_id,order_id,linked_by_admin_id) VALUES(?,?,?,?)",ticketId,member,order,actor.id());
        db.update("INSERT INTO customer_service_messages(ticket_id,sender_type,admin_id,message_content,sent_at) VALUES(?,'ADMIN',?,?,CURRENT_TIMESTAMP)",ticketId,actor.id(),note.strip());
        if(db.update("UPDATE refund_requests SET ticket_id=? WHERE refund_id=? AND ticket_id IS NULL AND status='PENDING'",ticketId,refundId)!=1)
            throw new Problem(409,"申請已被其他人處理，請重新整理");
        notifications.member(member,"TICKET_CREATED","退款申請需要補充資料",ticketNo,"/member/support/"+ticketId);
        events.changed(member);
        return Map.of("ticketId",ticketId,"ticketNo",ticketNo,"message","已建立關聯客服案件並通知會員");
    }

    public record Decision(String action,String reason,String reference,Boolean confirmed) {}

    @Transactional
    public Map<String,Object> decide(Actor actor,int refundId,Decision input) {
        require(actor);
        if(input==null || !Set.of("REFUND","REJECT").contains(Objects.toString(input.action(),""))
                || input.reason()==null || input.reason().isBlank() || input.reason().length()>1000)
            throw new Problem(400,"請選擇處理方式並填寫原因（最多 1000 字）");
        var initial=find(refundId);int order=id(initial.get("order_id"));
        // 與付款及取消訂單採相同鎖定順序，包含贈禮受贈會員。
        var people=new TreeSet<Integer>();people.add(id(initial.get("member_id")));
        people.addAll(db.queryForList("SELECT recipient_member_id FROM order_items WHERE order_id=? AND recipient_member_id IS NOT NULL",Integer.class,order));
        for(int member:people)db.update("UPDATE members SET failed_login_count=failed_login_count WHERE member_id=?",member);
        db.update("UPDATE orders SET updated_at=updated_at WHERE order_id=?",order);
        var row=find(refundId);
        if(!"PENDING".equals(row.get("status")))throw new Problem(409,"此退款申請已處理，請重新整理");
        if(!"PAID".equals(row.get("order_status")))throw new Problem(409,"訂單已非已付款狀態，請先人工核對");
        String mode=null,reference=null,status="REJECTED";
        boolean refund=input.action().equals("REFUND");
        if(refund) {
            if(!Boolean.TRUE.equals(input.confirmed()))throw new Problem(400,"請先確認退款方式及整筆訂單的影響");
            boolean test="TEST".equals(row.get("payment_environment"));
            mode=test?"TEST":"MANUAL";
            if(!test) {
                if(input.reference()==null || input.reference().strip().length()<5 || input.reference().length()>100)
                    throw new Problem(400,"正式或來源不明訂單須先完成實際退款，並填寫退款紀錄編號");
                reference=input.reference().strip();
            }
            // 僅撤銷這筆訂單授予的權益，不影響其他訂單取得的同款遊戲。
            db.update("UPDATE member_games SET is_active=0,revoked_at=CURRENT_TIMESTAMP WHERE is_active=1 AND order_item_id IN (SELECT order_item_id FROM order_items WHERE order_id=?)",order);
            for(var c:db.queryForList("SELECT coupon_id,1 AS n FROM order_items WHERE order_id=? AND coupon_id IS NOT NULL GROUP BY coupon_id",order)) {
                int n=id(c.get("n"));
                if(db.update("UPDATE coupons SET used_count=used_count-? WHERE coupon_id=? AND used_count>=?",n,c.get("coupon_id"),n)!=1)
                    throw new Problem(409,"優惠券使用次數不一致，退款尚未登記，請先核對");
            }
            db.update("UPDATE orders SET order_status='REFUNDED',payment_status='REFUNDED',updated_at=CURRENT_TIMESTAMP WHERE order_id=?",order);
            status="REFUNDED";
        }
        int changed=db.update("UPDATE refund_requests SET status=?,processed_at=CURRENT_TIMESTAMP,processed_by_admin_id=?,decision_reason=?,refund_mode=?,refund_reference=? WHERE refund_id=? AND status='PENDING'",status,actor.id(),input.reason().strip(),mode,reference,refundId);
        if(changed!=1)throw new Problem(409,"申請狀態已變更，請重新整理");
        String title=refund?("TEST".equals(mode)?"測試退款已完成":"退款已登記完成"):"退款申請未通過";
        String content=title+"｜訂單 "+row.get("order_no")+"\n"+input.reason().strip()
                +(refund&&"TEST".equals(mode)?"\n這是測試訂單的退款流程，未執行真實金流退刷。":"");
        // 僅在先前確實建立客服案件時，才同步客服對話和結案；一般退款不自動建案。
        if (row.get("ticket_id") != null) {
            db.update("INSERT INTO customer_service_messages(ticket_id,sender_type,admin_id,message_content,sent_at) VALUES(?,'ADMIN',?,?,CURRENT_TIMESTAMP)",row.get("ticket_id"),actor.id(),content);
            db.update("UPDATE customer_service_tickets SET status='CLOSED',admin_id=COALESCE(admin_id,?),updated_at=CURRENT_TIMESTAMP WHERE ticket_id=?",actor.id(),row.get("ticket_id"));
            db.update("INSERT INTO support_audit_logs(ticket_id,action_code,visibility,actor_admin_id,summary) VALUES(?,'TICKET_CLOSED','MEMBER',?,?)",row.get("ticket_id"),actor.id(),title);
        }
        db.update("INSERT INTO admin_operation_logs(admin_id,action_code,target_id,detail) VALUES(?,?,?,?)",actor.id(),refund?"REFUND_COMPLETE":"REFUND_REJECT",order,title+"："+input.reason().strip().substring(0,Math.min(700,input.reason().strip().length())));
        notifications.member(id(row.get("member_id")),"REFUND_UPDATE",title,"訂單 "+row.get("order_no")+"："+input.reason().strip().substring(0,Math.min(350,input.reason().strip().length())),"/orders?refundOrder="+order);
        events.changed(id(row.get("member_id")));
        return Map.of("message",title,"status",status);
    }
}
