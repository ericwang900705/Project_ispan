package gameplatform.support.extension;

import gameplatform.admin.service.AdminPermissionService;
import gameplatform.support.model.Actor;
import gameplatform.support.model.EventHub;
import gameplatform.support.model.Problem;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 客服關聯、私人備註、範本、FAQ 與審計；所有私人查詢均重新驗證對話及權限。 */
@Service
@Transactional
public class SupportExtensionService {

  private final JdbcTemplate db;
  private final AdminPermissionService permissions;
  private final EventHub events;
  private final boolean sqlServer;

  public SupportExtensionService(
    JdbcTemplate db,
    AdminPermissionService permissions,
    EventHub events,
    @Value("${app.dialect:sqlserver}") String dialect
  ) {
    this.db = db;
    this.permissions = permissions;
    this.events = events;
    this.sqlServer = "sqlserver".equals(dialect);
  }

  public record Context(int memberId, Integer adminId, String status) {}

  public record ContentInput(
    String title,
    String content,
    Integer categoryId,
    String status,
    Integer sortOrder,
    Integer version
  ) {}

  private static int num(Object v) {
    return ((Number) v).intValue();
  }

  private static Problem bad(String message) {
    return new Problem(400, message);
  }

  private static String text(String value, int max) {
    if (value == null || value.isBlank() || value.length() > max) throw bad(
      "請填寫完整資料，並確認字數限制"
    );
    return value.strip();
  }

  private static String term(String q) {
    if (q == null) return "%";
    if (q.length() > 100) throw bad("搜尋最多 100 字");
    return (
      "%" +
      q
        .strip()
        .replace("!", "!!")
        .replace("%", "!%")
        .replace("_", "!_")
        .replace("[", "![") +
      "%"
    );
  }

  private static int offset(int page) {
    if (page < 0 || page > 100000) throw bad("頁碼不正確");
    return page * 20;
  }

  private static String scopeColumn(String scope) {
    return switch (scope) {
      case "rooms" -> "support_room_id";
      case "tickets" -> "ticket_id";
      default -> throw bad("不支援的客服範圍");
    };
  }

  public void requireSupport(Actor actor) {
    if (
      actor == null ||
      actor.isMember() ||
      !permissions.forActor(actor).contains("TICKET_HANDLE")
    ) throw new Problem(403, "未授權使用客服私人資料");
  }

  public Context context(Actor actor, String scope, int id, boolean write) {
    String column = scopeColumn(scope),
      table = scope.equals("rooms") ? "chat_rooms" : "customer_service_tickets";
    if (actor == null) throw new Problem(401, "請先登入");
    if (!actor.isMember()) requireSupport(actor);
    String lock = write && sqlServer ? " WITH (UPDLOCK,ROWLOCK)" : "";
    String suffix = write && !sqlServer ? " FOR UPDATE" : "";
    var rows = db.queryForList(
      "SELECT member_id,admin_id,status FROM dbo." +
        table +
        lock +
        " WHERE " +
        column +
        "=?" +
        suffix,
      id
    );
    if (rows.isEmpty()) throw new Problem(404, "找不到此對話或案件");
    var row = rows.getFirst();
    int member = num(row.get("member_id"));
    if (actor.isMember() && actor.id() != member) throw new Problem(
      404,
      "找不到此對話或案件"
    );
    Integer assigned =
      row.get("admin_id") == null ? null : num(row.get("admin_id"));
    String status = (String) row.get("status");
    if (write) {
      if ("CLOSED".equals(status)) throw new Problem(
        409,
        "對話或案件已結束，僅可檢視"
      );
      if (
        !actor.isMember() && !Objects.equals(assigned, actor.id())
      ) throw new Problem(409, "請先接手此對話或案件");
    }
    return new Context(member, assigned, status);
  }

  private long insert(String key, String sql, Object... args) {
    var holder = new GeneratedKeyHolder();
    db.update(c -> {
      var p = sqlServer
        ? c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
        : c.prepareStatement(sql, new String[] { key });
      for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
      return p;
    }, holder);
    return Objects.requireNonNull(holder.getKey()).longValue();
  }

  private Map<String, Object> page(List<Map<String, Object>> rows) {
    boolean more = rows.size() > 20;
    var result = new ArrayList<>(rows.subList(0, Math.min(20, rows.size())));
    return Map.of("items", result, "hasMore", more);
  }

  private List<Map<String, Object>> orderRows(
    int member,
    Integer orderId,
    String q,
    int page
  ) {
    var args = new ArrayList<Object>();
    args.add(member);
    String filter = "";
    if (orderId != null) {
      filter = " AND o.order_id=?";
      args.add(orderId);
    } else {
      filter =
        " AND (o.order_no LIKE ? ESCAPE '!' OR EXISTS(SELECT 1 FROM dbo.order_items i JOIN dbo.games g ON g.game_id=i.game_id WHERE i.order_id=o.order_id AND g.game_name LIKE ? ESCAPE '!'))";
      args.add(term(q));
      args.add(term(q));
    }
    args.add(offset(page));
    var rows = db.queryForList(
      "SELECT o.order_id AS [orderId],o.order_no AS [orderNo],o.order_status AS [orderStatus],o.payment_status AS [paymentStatus],o.total_amount AS [totalAmount],o.payment_type AS [paymentType],o.created_at AS [createdAt],o.bank_code AS [bankCode],o.virtual_account AS [virtualAccount],o.payment_no AS [paymentNo],o.payment_expire_date AS [expireDate] FROM dbo.orders o WHERE o.member_id=?" +
        filter +
        " ORDER BY o.order_id DESC OFFSET ? ROWS FETCH NEXT 21 ROWS ONLY",
      args.toArray()
    );
    for (var row : rows)
      row.put(
        "games",
        db.queryForList(
          "SELECT i.game_id AS [gameId],g.game_name AS name,i.unit_price AS [unitPrice],i.quantity FROM dbo.order_items i JOIN dbo.games g ON g.game_id=i.game_id WHERE i.order_id=? ORDER BY i.order_item_id",
          row.get("orderId")
        )
      );
    return rows;
  }

  private List<Map<String, Object>> couponRows(
    int member,
    Integer couponId,
    String q,
    int page
  ) {
    var args = new ArrayList<Object>();
    args.add(member);
    String filter = "";
    if (couponId != null) {
      filter = " AND c.coupon_id=?";
      args.add(couponId);
    } else {
      filter = " AND c.coupon_name LIKE ? ESCAPE '!'";
      args.add(term(q));
    }
    args.add(offset(page));
    var rows = db.queryForList(
      "SELECT c.coupon_id AS [couponId],c.coupon_name AS name,c.discount_type AS [discountType],c.discount_value AS [discountValue],c.min_spend AS [minSpend],c.start_at AS [startAt],c.expires_at AS [expiresAt],c.status,c.total_limit AS [totalLimit],c.used_count AS [usedCount],c.per_member_limit AS [perMemberLimit],mc.received_at AS [receivedAt],COALESCE(cr.scope_type,'ALL') AS [scopeType],cr.publisher_member_id AS [publisherId],p.username AS [publisherName],cr.tag_id AS [tagId],t.tag_name AS [tagName] FROM dbo.member_coupon_claims mc JOIN dbo.coupons c ON c.coupon_id=mc.coupon_id LEFT JOIN dbo.coupon_rules cr ON cr.coupon_id=c.coupon_id LEFT JOIN dbo.members p ON p.member_id=cr.publisher_member_id LEFT JOIN dbo.tags t ON t.tag_id=cr.tag_id WHERE mc.member_id=?" +
        filter +
        " ORDER BY c.coupon_id DESC OFFSET ? ROWS FETCH NEXT 21 ROWS ONLY",
      args.toArray()
    );
    for (var row : rows) {
      int pending = count(
        "SELECT COUNT(*) FROM dbo.order_items i JOIN dbo.orders o ON o.order_id=i.order_id WHERE o.member_id=? AND i.coupon_id=? AND o.order_status='PENDING'",
        member,
        row.get("couponId")
      );
      int paid = count(
        "SELECT COUNT(*) FROM dbo.order_items i JOIN dbo.orders o ON o.order_id=i.order_id WHERE o.member_id=? AND i.coupon_id=? AND o.order_status='PAID'",
        member,
        row.get("couponId")
      );
      row.put("pendingUseCount", pending);
      row.put("paidUseCount", paid);
      row.put(
        "remainingUses",
        Math.max(0, num(row.get("perMemberLimit")) - pending - paid)
      );
      String reason = "";
      var now = LocalDateTime.now();
      if (!"ACTIVE".equals(row.get("status"))) reason = "優惠券已停用或失效";
      else if (
        ((Timestamp) row.get("expiresAt")).toLocalDateTime().isBefore(now)
      ) reason = "優惠券已過期";
      else if (
        ((Timestamp) row.get("startAt")).toLocalDateTime().isAfter(now)
      ) reason = "尚未到開始時間";
      else if (pending + paid >= num(row.get("perMemberLimit"))) reason =
        pending > 0 ? "額度由待付款訂單預留" : "個人使用額度已用完";
      else if (
        row.get("totalLimit") != null &&
        num(row.get("usedCount")) >= num(row.get("totalLimit"))
      ) reason = "總使用額度已滿";
      row.put("unavailableReason", reason);
    }
    return rows;
  }

  private int count(String sql, Object... args) {
    return db.queryForObject(sql, Integer.class, args);
  }

  public Object options(
    Actor actor,
    String scope,
    int id,
    String kind,
    String q,
    int page
  ) {
    var c = context(actor, scope, id, false);
    return page(
      kind.equals("orders")
        ? orderRows(c.memberId(), null, q, page)
        : couponRows(c.memberId(), null, q, page)
    );
  }

  public Object related(Actor actor, String scope, int id) {
    var c = context(actor, scope, id, false);
    var orders = new ArrayList<Map<String, Object>>();
    var coupons = new ArrayList<Map<String, Object>>();
    for (String kind : List.of("order", "coupon")) {
      var ids = db.queryForList(
        "SELECT " +
          kind +
          "_id FROM dbo.support_" +
          kind +
          "_links WHERE " +
          scopeColumn(scope) +
          "=? AND removed_at IS NULL ORDER BY link_id",
        Integer.class,
        id
      );
      for (int resource : ids) {
        var rows = kind.equals("order")
          ? orderRows(c.memberId(), resource, "", 0)
          : couponRows(c.memberId(), resource, "", 0);
        if (!rows.isEmpty()) (kind.equals("order") ? orders : coupons).add(
          rows.getFirst()
        );
      }
    }
    return Map.of(
      "orders",
      orders,
      "coupons",
      coupons,
      "memberId",
      c.memberId(),
      "writable",
      !c.status().equals("CLOSED") &&
        (actor.isMember() || Objects.equals(c.adminId(), actor.id()))
    );
  }

  public void link(
    Actor actor,
    String scope,
    int id,
    String kind,
    int target,
    boolean remove
  ) {
    if (!Set.of("order", "coupon").contains(kind) || target <= 0) throw bad(
      "關聯資料不正確"
    );
    var c = context(actor, scope, id, true);
    String table = "dbo.support_" + kind + "_links",
      column = kind + "_id",
      scopeCol = scopeColumn(scope);
    String owner = kind.equals("order")
      ? "SELECT COUNT(*) FROM dbo.orders WHERE order_id=? AND member_id=?"
      : "SELECT COUNT(*) FROM dbo.member_coupon_claims WHERE coupon_id=? AND member_id=?";
    if (count(owner, target, c.memberId()) != 1) throw new Problem(
      404,
      "找不到此會員的訂單或已領優惠券"
    );
    int existing = count(
      "SELECT COUNT(*) FROM " +
        table +
        " WHERE " +
        scopeCol +
        "=? AND " +
        column +
        "=? AND removed_at IS NULL",
      id,
      target
    );
    if (remove) {
      if (existing == 0) return;
      db.update(
        "UPDATE " +
          table +
          " SET removed_at=CURRENT_TIMESTAMP WHERE " +
          scopeCol +
          "=? AND " +
          column +
          "=? AND removed_at IS NULL",
        id,
        target
      );
    } else {
      if (existing > 0) return;
      if (
        count(
          "SELECT COUNT(*) FROM " +
            table +
            " WHERE " +
            scopeCol +
            "=? AND removed_at IS NULL",
          id
        ) >= 10
      ) throw new Problem(409, "每個對話最多關聯 10 筆同類資料");
      db.update(
        "INSERT INTO " +
          table +
          "(support_room_id,ticket_id,member_id," +
          column +
          ",linked_by_member_id,linked_by_admin_id) VALUES(?,?,?,?,?,?)",
        scope.equals("rooms") ? id : null,
        scope.equals("tickets") ? id : null,
        c.memberId(),
        target,
        actor.isMember() ? actor.id() : null,
        actor.isMember() ? null : actor.id()
      );
    }
    record(
      actor,
      scope,
      id,
      kind.toUpperCase(Locale.ROOT) + (remove ? "_UNLINKED" : "_LINKED"),
      (remove ? "移除" : "新增") +
        (kind.equals("order") ? "訂單" : "優惠券") +
        "關聯 #" +
        target,
      false
    );
    events.changed(c.memberId());
  }

  private String originCondition(String alias, String scope) {
    return scope.equals("rooms")
      ? alias + ".support_room_id=?"
      : "(" +
          alias +
          ".ticket_id=? OR EXISTS(SELECT 1 FROM dbo.chat_rooms r WHERE r.support_room_id=" +
          alias +
          ".support_room_id AND r.ticket_id=?))";
  }

  public Object notes(Actor actor, String scope, int id, long before) {
    requireSupport(actor);
    context(actor, scope, id, false);
    Object[] args = scope.equals("rooms")
      ? new Object[] { id, before }
      : new Object[] { id, id, before };
    var rows = db.queryForList(
      "SELECT n.note_id AS [noteId],n.support_room_id AS [roomId],n.ticket_id AS [ticketId],n.content,n.created_at AS [createdAt],a.admin_name AS [authorName] FROM dbo.support_internal_notes n JOIN dbo.admin a ON a.admin_id=n.author_admin_id WHERE " +
        originCondition("n", scope) +
        " AND n.note_id<? ORDER BY n.note_id DESC OFFSET 0 ROWS FETCH NEXT 21 ROWS ONLY",
      args
    );
    rows.forEach(r -> r.put("noteId", String.valueOf(r.get("noteId"))));
    return page(rows);
  }

  public Object addNote(Actor actor, String scope, int id, String content) {
    requireSupport(actor);
    context(actor, scope, id, true);
    String body = text(content, 2000);
    long note = insert(
      "note_id",
      "INSERT INTO dbo.support_internal_notes(support_room_id,ticket_id,author_admin_id,content) VALUES(?,?,?,?)",
      scope.equals("rooms") ? id : null,
      scope.equals("tickets") ? id : null,
      actor.id(),
      body
    );
    record(actor, scope, id, "NOTE_ADDED", "新增內部備註 #" + note, true);
    events.notification("ADMIN", actor.id());
    return Map.of("noteId", Long.toString(note));
  }

  public void record(
    Actor actor,
    String scope,
    int id,
    String action,
    String summary,
    boolean internal
  ) {
    db.update(
      "INSERT INTO dbo.support_audit_logs(support_room_id,ticket_id,action_code,visibility,actor_member_id,actor_admin_id,summary) VALUES(?,?,?,?,?,?,?)",
      scope.equals("rooms") ? id : null,
      scope.equals("tickets") ? id : null,
      action,
      internal ? "INTERNAL" : "MEMBER",
      actor.isMember() ? actor.id() : null,
      actor.isMember() ? null : actor.id(),
      summary
    );
  }

  public Object logs(
    Actor actor,
    String scope,
    int id,
    long before,
    boolean internal
  ) {
    context(actor, scope, id, false);
    if (internal) requireSupport(actor);
    String filter = internal
      ? ""
      : " AND l.visibility='MEMBER' AND l.action_code IN ('ROOM_CREATED','ROOM_CLAIMED','ROOM_CLOSED','TICKET_CREATED_FROM_ROOM','TICKET_CLAIMED','TICKET_CLOSED')";
    Object[] args = scope.equals("rooms")
      ? new Object[] { id, before }
      : new Object[] { id, id, before };
    var rows = db.queryForList(
      "SELECT l.audit_id AS [auditId],l.action_code AS action,l.summary,l.created_at AS [createdAt]" +
        (internal ? ",COALESCE(a.admin_name,m.username) AS [actorName]" : "") +
        " FROM dbo.support_audit_logs l LEFT JOIN dbo.admin a ON a.admin_id=l.actor_admin_id LEFT JOIN dbo.members m ON m.member_id=l.actor_member_id WHERE " +
        originCondition("l", scope) +
        " AND l.audit_id<?" +
        filter +
        " ORDER BY l.audit_id DESC OFFSET 0 ROWS FETCH NEXT 21 ROWS ONLY",
      args
    );
    rows.forEach(r -> r.put("auditId", String.valueOf(r.get("auditId"))));
    return page(rows);
  }

  public void escalated(Actor actor, int room, int ticket, int member) {
    for (String kind : List.of("order", "coupon"))
      db.update(
        "INSERT INTO dbo.support_" +
          kind +
          "_links(ticket_id,member_id," +
          kind +
          "_id,linked_by_admin_id) SELECT ?,member_id," +
          kind +
          "_id,? FROM dbo.support_" +
          kind +
          "_links WHERE support_room_id=? AND removed_at IS NULL",
        ticket,
        actor.id(),
        room
      );
    record(
      actor,
      "rooms",
      room,
      "TICKET_CREATED_FROM_ROOM",
      "對話已轉為正式案件",
      false
    );
    record(
      actor,
      "tickets",
      ticket,
      "TICKET_CREATED_FROM_ROOM",
      "由聊天室 #" + room + " 轉為案件並保留關聯資料",
      false
    );
  }

  private String contentTable(String kind) {
    return switch (kind) {
      case "faqs" -> "support_faqs";
      case "reply-templates" -> "support_reply_templates";
      default -> throw bad("不支援的內容種類");
    };
  }

  public Object content(
    Actor actor,
    String kind,
    boolean management,
    String q,
    String status,
    Integer category,
    int page
  ) {
    String table = contentTable(kind);
    boolean faq = kind.equals("faqs");
    if (management) permissions.requireHighestAdmin(actor);
    else if (!faq) requireSupport(actor);
    var args = new ArrayList<Object>();
    String where = " WHERE 1=1";
    if (!management || (status != null && !status.isBlank())) {
      where += " AND x.status=?";
      args.add(management ? status : faq ? "PUBLISHED" : "ACTIVE");
    }
    if (category != null) {
      where += " AND x.category_id=?";
      args.add(category);
    }
    where +=
      " AND (x." +
      (faq ? "question" : "title") +
      " LIKE ? ESCAPE '!' OR x." +
      (faq ? "answer" : "content") +
      " LIKE ? ESCAPE '!')";
    args.add(term(q));
    args.add(term(q));
    args.add(offset(page));
    String select =
      "x." +
      (faq ? "faq_id" : "template_id") +
      " AS id,x." +
      (faq ? "question" : "title") +
      " AS title,x." +
      (faq ? "answer" : "content") +
      " AS content,x.category_id AS [categoryId],c.category_name AS [categoryName],x.sort_order AS [sortOrder]" +
      (management ? ",x.status,x.version" : "");
    var rows = db.queryForList(
      "SELECT " +
        select +
        " FROM dbo." +
        table +
        " x LEFT JOIN dbo.customer_service_categories c ON c.category_id=x.category_id" +
        where +
        " ORDER BY x.sort_order,x." +
        (faq ? "faq_id" : "template_id") +
        " DESC OFFSET ? ROWS FETCH NEXT 21 ROWS ONLY",
      args.toArray()
    );
    rows.forEach(r -> r.put("id", String.valueOf(r.get("id"))));
    return page(rows);
  }

  public Object saveContent(
    Actor actor,
    String kind,
    Long id,
    ContentInput in
  ) {
    permissions.requireHighestAdmin(actor);
    String table = contentTable(kind);
    boolean faq = kind.equals("faqs");
    if (in == null) throw bad("請填寫內容");
    String title = text(in.title(), faq ? 200 : 100),
      body = text(in.content(), faq ? 8000 : 4000);
    if (
      !Set.of(
        faq
          ? new String[] { "DRAFT", "PUBLISHED", "ARCHIVED" }
          : new String[] { "ACTIVE", "INACTIVE" }
      ).contains(Objects.toString(in.status(), "")) ||
      in.sortOrder() == null ||
      in.sortOrder() < 0 ||
      in.sortOrder() > 100000
    ) throw bad("狀態或排序不正確");
    if (
      in.categoryId() != null &&
      count(
        "SELECT COUNT(*) FROM dbo.customer_service_categories WHERE category_id=?",
        in.categoryId()
      ) != 1
    ) throw bad("分類不存在");
    String titleCol = faq ? "question" : "title",
      bodyCol = faq ? "answer" : "content",
      key = faq ? "faq_id" : "template_id";
    if (id == null) id = insert(
      key,
      "INSERT INTO dbo." +
        table +
        "(category_id," +
        titleCol +
        "," +
        bodyCol +
        ",status,sort_order,created_by_admin_id,updated_by_admin_id" +
        (faq ? ",published_at" : "") +
        ") VALUES(?,?,?,?,?,?,?" +
        (faq ? ",?" : "") +
        ")",
      faq
        ? new Object[] {
            in.categoryId(),
            title,
            body,
            in.status(),
            in.sortOrder(),
            actor.id(),
            actor.id(),
            "PUBLISHED".equals(in.status()) ? LocalDateTime.now() : null,
          }
        : new Object[] {
            in.categoryId(),
            title,
            body,
            in.status(),
            in.sortOrder(),
            actor.id(),
            actor.id(),
          }
    );
    else {
      if (in.version() == null || in.version() < 0) throw bad(
        "缺少內容版本，請重新載入"
      );
      int changed = db.update(
        "UPDATE dbo." +
          table +
          " SET category_id=?," +
          titleCol +
          "=?," +
          bodyCol +
          "=?,status=?,sort_order=?,updated_by_admin_id=?,updated_at=CURRENT_TIMESTAMP,version=version+1" +
          (faq
            ? ",published_at=CASE WHEN ?='PUBLISHED' THEN CURRENT_TIMESTAMP ELSE published_at END"
            : "") +
          " WHERE " +
          key +
          "=? AND version=?",
        faq
          ? new Object[] {
              in.categoryId(),
              title,
              body,
              in.status(),
              in.sortOrder(),
              actor.id(),
              in.status(),
              id,
              in.version(),
            }
          : new Object[] {
              in.categoryId(),
              title,
              body,
              in.status(),
              in.sortOrder(),
              actor.id(),
              id,
              in.version(),
            }
      );
      if (changed != 1) throw new Problem(
        409,
        "內容已被更新或不存在，請重新載入"
      );
    }
    db.update(
      "INSERT INTO dbo.admin_operation_logs(admin_id,action_code,target_id,detail) VALUES(?,?,?,?)",
      actor.id(),
      faq ? "SUPPORT_FAQ_SAVE" : "SUPPORT_TEMPLATE_SAVE",
      id,
      title
    );
    return Map.of("id", Long.toString(id));
  }
}
