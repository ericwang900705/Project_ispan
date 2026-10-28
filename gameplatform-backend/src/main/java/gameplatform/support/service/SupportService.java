package gameplatform.support.service;

import static gameplatform.support.model.Models.*;

import java.sql.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import gameplatform.support.model.Actor;
import gameplatform.support.model.EventHub;
import gameplatform.support.model.Problem;

@Service
@Transactional
public class SupportService {
    private final JdbcTemplate db;
    private final EventHub events;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private NotificationService notifications;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private gameplatform.support.extension.SupportExtensionService extension;
    private final boolean sqlServer;

    public SupportService(JdbcTemplate db, EventHub events, @Value("${app.dialect}") String dialect) {
        this.db = db;
        this.events = events;
        this.sqlServer = dialect.equals("sqlserver");
    }

    private static Integer nullableInt(ResultSet rs, String key) throws SQLException {
        int v = rs.getInt(key);
        return rs.wasNull() ? null : v;
    }

    private static java.time.LocalDateTime time(ResultSet rs, String key) throws SQLException {
        Timestamp t = rs.getTimestamp(key);
        return t == null ? null : t.toLocalDateTime();
    }

    private static final RowMapper<Room> ROOM = (rs, n) -> new Room(rs.getInt("support_room_id"),
            rs.getInt("member_id"),
            rs.getString("member_name"), rs.getString("status"), nullableInt(rs, "admin_id"),
            nullableInt(rs, "ticket_id"),
            rs.getString("ticket_no"), time(rs, "created_at"), time(rs, "closed_at"));
    private static final RowMapper<Ticket> TICKET = (rs, n) -> new Ticket(rs.getInt("ticket_id"),
            rs.getString("ticket_no"),
            rs.getInt("member_id"), rs.getString("member_name"), rs.getInt("category_id"),
            rs.getString("category_name"),
            nullableInt(rs, "game_id"), rs.getString("game_name"), rs.getString("subject"), rs.getString("content"),
            rs.getString("status"),
            nullableInt(rs, "admin_id"), time(rs, "created_at"), time(rs, "updated_at"), List.of(),
            rs.getString("priority"), rs.getString("waiting_for"), time(rs, "waiting_since"));
    private static final String ROOM_SELECT = "SELECT r.*,t.ticket_no,m.username AS member_name FROM chat_rooms r JOIN members m ON r.member_id=m.member_id LEFT JOIN customer_service_tickets t ON t.ticket_id=r.ticket_id ";
    private static final String TICKET_SELECT = "SELECT t.*,m.username AS member_name,c.category_name,g.game_name, "
            + "CASE WHEN t.status='CLOSED' THEN 'CLOSED' WHEN (SELECT sender_type FROM customer_service_messages "
            + "WHERE message_id=(SELECT MAX(message_id) FROM customer_service_messages WHERE ticket_id=t.ticket_id))='ADMIN' "
            + "THEN 'MEMBER' ELSE 'ADMIN' END AS waiting_for, "
            + "COALESCE((SELECT MAX(sent_at) FROM customer_service_messages WHERE ticket_id=t.ticket_id),t.created_at) AS waiting_since "
            + "FROM customer_service_tickets t JOIN members m ON m.member_id=t.member_id "
            + "JOIN customer_service_categories c ON c.category_id=t.category_id LEFT JOIN games g ON g.game_id=t.game_id ";

    private int insert(String keyColumn, String sql, Object... args) {
        var key = new GeneratedKeyHolder();
        db.update(c -> {
            var ps = sqlServer ? c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
                    : c.prepareStatement(sql, new String[] {
                            keyColumn
                    });
            for (int i = 0; i < args.length; i++)
                ps.setObject(i + 1, args[i]);
            return ps;
        }, key);
        return Objects.requireNonNull(key.getKey()).intValue();
    }

    private void lock(String table, String key, int id) {
        String q = sqlServer ? "SELECT " + key + " FROM " + table + " WITH (UPDLOCK, ROWLOCK) WHERE " + key + " = ?"
                : "SELECT " + key + " FROM " + table + " WHERE " + key + " = ? FOR UPDATE";
        if (db.queryForList(q, id).isEmpty())
            throw Problem.missing();
    }

    private void participant(Actor a) {
        if (!a.isMember() && !a.canSupport())
            throw Problem.denied();
    }

    private void member(Actor a) {
        if (!a.isMember())
            throw Problem.denied();
    }

    /** 建立會員本人的退款客服案件；實際款項及遊戲權益由客服核對處理。 */
    public Map<String, Object> requestRefund(Actor actor, int orderId, String reason) {
        member(actor);
        if (reason == null || reason.isBlank() || reason.length() > 2000)
            throw new Problem(400, "請填寫退款原因（最多 2000 字）");
        String query = sqlServer
                ? "SELECT order_status,paid_at FROM orders WITH (UPDLOCK,ROWLOCK) WHERE order_id=? AND member_id=?"
                : "SELECT order_status,paid_at FROM orders WHERE order_id=? AND member_id=? FOR UPDATE";
        var orders = db.queryForList(query, orderId, actor.id());
        if (orders.isEmpty()) throw Problem.missing();
        var previous = db.queryForList("SELECT refund_id,status,ticket_id FROM refund_requests WHERE order_id=? AND member_id=?", orderId, actor.id());
        if (!previous.isEmpty()) return Map.of("refundId", previous.getFirst().get("refund_id"), "message", "此訂單已有退款申請，請在我的訂單查看處理結果");
        var order = orders.getFirst();
        Timestamp paidAt = (Timestamp) order.get("paid_at");
        if (!"PAID".equals(order.get("order_status")) || paidAt == null
                || !paidAt.toLocalDateTime().plusDays(14).isAfter(java.time.LocalDateTime.now()))
            throw new Problem(409, "此訂單不在付款後 14 天的申請期限內，請聯絡客服核對");
        // 正常退款直接加入退款申請表；客服案件僅在後續需要補件/諮詢時建立。
        int refundId = insert("refund_id", "INSERT INTO refund_requests(order_id,member_id,reason) VALUES(?,?,?)",
                orderId, actor.id(), reason.strip());
        events.changed(actor.id());
        if (notifications != null) {
            notifications.member(actor.id(), "REFUND_REQUESTED", "退款申請已送出", "訂單 #" + orderId, "/orders");
        }
        return Map.of("refundId", refundId, "message", "退款申請已送出，請在我的訂單查看進度");
    }

    private void admin(Actor a) {
        if (!a.canSupport())
            throw Problem.denied();
    }

    private void visible(Actor a, int memberId) {
        participant(a);
        if (!a.canSupport() && a.id() != memberId)
            throw Problem.denied();
    }

    private void assigned(Actor a, Integer adminId) {
        admin(a);
        if (!Objects.equals(adminId, a.id()))
            throw new Problem(409, "請先接手此對話；目前可能由其他客服負責");
    }

    private void open(String status) {
        if (status.equals("CLOSED"))
            throw new Problem(409, "對話已結束，請查看案件或開始新對話");
    }

    // One batch query per page; retain legacy single-game tickets and demo seed
    // data.
    private List<Ticket> withTicketGames(List<Ticket> tickets) {
        if (tickets.isEmpty())
            return tickets;
        Map<Integer, List<Game>> games = new HashMap<>();
        String marks = String.join(",", Collections.nCopies(tickets.size(), "?"));
        db.query("SELECT tg.ticket_id,g.game_id,g.game_name FROM customer_service_ticket_games tg "
                + "JOIN games g ON g.game_id=tg.game_id WHERE tg.ticket_id IN (" + marks
                + ") ORDER BY tg.ticket_id,tg.sort_order,tg.game_id",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> games
                        .computeIfAbsent(rs.getInt("ticket_id"), ignored -> new ArrayList<>())
                        .add(new Game(rs.getInt("game_id"), rs.getString("game_name"))),
                tickets.stream().map(Ticket::id).toArray());
        return tickets.stream().map(t -> t.withGames(games.getOrDefault(t.id(),
                t.gameId() == null ? List.of() : List.of(new Game(t.gameId(), t.gameName()))))).toList();
    }

    private Room room(int id) {
        return db.query(ROOM_SELECT + "WHERE r.support_room_id=?", ROOM, id).stream().findFirst()
                .orElseThrow(Problem::missing);
    }

    private Ticket ticket(int id) {
        return withTicketGames(db.query(TICKET_SELECT + "WHERE t.ticket_id=?", TICKET, id)).stream().findFirst()
                .orElseThrow(Problem::missing);
    }

    public Room getRoom(Actor a, int id) {
        Room r = room(id);
        visible(a, r.memberId());
        return r;
    }

    public Ticket getTicket(Actor a, int id) {
        Ticket t = ticket(id);
        visible(a, t.memberId());
        return t;
    }

    public Room latestRoom(Actor a) {
        member(a);
        return db.query(ROOM_SELECT
                + "WHERE r.member_id=? ORDER BY CASE WHEN r.status='OPEN' THEN 0 ELSE 1 END,r.support_room_id DESC OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY",
                ROOM, a.id()).stream().findFirst().orElse(null);
    }

    public Room startRoom(Actor a) {
        member(a);
        lock("members", "member_id", a.id());
        var existing = db.query(ROOM_SELECT + "WHERE r.member_id=? AND r.status='OPEN'", ROOM, a.id());
        if (!existing.isEmpty())
            return existing.get(0);
        int id = insert("support_room_id",
                "INSERT INTO chat_rooms(member_id,status,created_at) VALUES (?,'OPEN',CURRENT_TIMESTAMP)",
                a.id());
        if (extension != null) extension.record(a,"rooms",id,"ROOM_CREATED","會員開始客服對話",false);
        events.changed(a.id());
        if (notifications != null)
            notifications.supportQueue("NEW_CHAT", "新的即時聊天室", "聊天室 #" + id + " 等待接手", "/admin/chats?room=" + id);
        return room(id);
    }

    public List<Room> rooms(Actor a, String status, boolean mine, int page) {
        return rooms(a, status, mine, page, "");
    }

    public List<Room> rooms(Actor a, String status, boolean mine, int page, String q) {
        participant(a);
        int offset = pageOffset(page);
        String filter = "WHERE 1=1";
        List<Object> args = new ArrayList<>();
        if (!a.canSupport()) {
            filter += " AND r.member_id=?";
            args.add(a.id());
        } else if (mine) {
            filter += " AND r.admin_id=?";
            args.add(a.id());
        }
        if (status != null && !status.isBlank()) {
            if (!Set.of("OPEN", "CLOSED").contains(status))
                throw new Problem(400, "無效的聊天室狀態");
            filter += " AND r.status=?";
            args.add(status);
        }
        String term = searchTerm(q);
        if (!term.isEmpty()) {
            filter += " AND (CAST(r.support_room_id AS VARCHAR(20))=? OR EXISTS(SELECT 1 FROM chat_messages cm WHERE cm.support_room_id=r.support_room_id AND cm.content LIKE ? ESCAPE '!')";
            args.add(term.startsWith("#") ? term.substring(1) : term);
            args.add(likeTerm(term));
            if (a.canSupport()) {
                filter += " OR m.username LIKE ? ESCAPE '!'";
                args.add(likeTerm(term));
            }
            filter += ")";
        }
        args.add(offset);
        return db.query(ROOM_SELECT + filter + " ORDER BY r.support_room_id DESC OFFSET ? ROWS FETCH NEXT 30 ROWS ONLY",
                ROOM, args.toArray());
    }

    public List<Ticket> tickets(Actor a, String status, int page) {
        return tickets(a, status, page, "", null, null, null);
    }

    public List<Ticket> tickets(Actor a, String status, int page, String q) {
        return tickets(a, status, page, q, null, null, null);
    }

    public List<Ticket> tickets(Actor a, String status, int page, String q, Integer categoryId,
            String priority, String waitingFor) {
        participant(a);
        int offset = pageOffset(page);
        String filter = "WHERE 1=1";
        List<Object> args = new ArrayList<>();
        if (!a.canSupport()) {
            filter += " AND t.member_id=?";
            args.add(a.id());
        }
        if (status != null && !status.isBlank()) {
            if (!Set.of("OPEN", "IN_PROGRESS", "CLOSED").contains(status))
                throw new Problem(400, "無效的案件狀態");
            filter += " AND t.status=?";
            args.add(status);
        }
        if (categoryId != null) {
            if (categoryId <= 0)
                throw new Problem(400, "無效的分類");
            filter += " AND t.category_id=?";
            args.add(categoryId);
        }
        if (priority != null && !priority.isBlank()) {
            if (!Set.of("URGENT", "HIGH", "NORMAL", "LOW").contains(priority))
                throw new Problem(400, "無效的優先程度");
            filter += " AND t.priority=?";
            args.add(priority);
        }
        if (waitingFor != null && !waitingFor.isBlank()) {
            if (!Set.of("ADMIN", "MEMBER").contains(waitingFor))
                throw new Problem(400, "無效的回覆對象");
            filter += " AND t.status<>'CLOSED' AND " +
                    (waitingFor.equals("MEMBER") ? "" : "NOT ") +
                    "EXISTS (SELECT 1 FROM customer_service_messages lastMsg WHERE lastMsg.ticket_id=t.ticket_id " +
                    "AND lastMsg.message_id=(SELECT MAX(mx.message_id) FROM customer_service_messages mx WHERE mx.ticket_id=t.ticket_id) "
                    +
                    "AND lastMsg.sender_type='ADMIN')";
        }
        String term = searchTerm(q);
        if (!term.isEmpty()) {
            filter += " AND (CAST(t.ticket_id AS VARCHAR(20))=? OR t.ticket_no LIKE ? ESCAPE '!' OR t.subject LIKE ? ESCAPE '!' OR t.content LIKE ? ESCAPE '!'";
            args.add(term.startsWith("#") ? term.substring(1) : term);
            args.add(likeTerm(term));
            args.add(likeTerm(term));
            args.add(likeTerm(term));
            if (a.canSupport()) {
                filter += " OR m.username LIKE ? ESCAPE '!'";
                args.add(likeTerm(term));
            }
            filter += ")";
        }
        args.add(offset);
        return withTicketGames(
                db.query(TICKET_SELECT + filter + " ORDER BY t.ticket_id DESC OFFSET ? ROWS FETCH NEXT 30 ROWS ONLY",
                        TICKET, args.toArray()));
    }

    private String searchTerm(String q) {
        String term = q == null ? "" : q.strip();
        if (term.length() > 100)
            throw new Problem(400, "搜尋最多 100 字");
        return term;
    }

    // Escape LIKE metacharacters: search is literal, never a SQL expression.
    private String likeTerm(String q) {
        return "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_").replace("[", "![") + "%";
    }

    private int pageOffset(int page) {
        if (page < 0 || page > 100000)
            throw new Problem(400, "頁碼超出範圍");
        return page * 30;
    }

    public Room claimRoom(Actor a, int id) {
        admin(a);
        lock("chat_rooms", "support_room_id", id);
        Room r = room(id);
        open(r.status());
        if (r.adminId() != null && !r.adminId().equals(a.id()))
            throw new Problem(409, "其他客服已接手此對話");
        db.update("UPDATE chat_rooms SET admin_id=? WHERE support_room_id=?", a.id(), id);
        if (extension != null && r.adminId() == null) extension.record(a,"rooms",id,"ROOM_CLAIMED","客服已接手對話",false);
        events.changed(r.memberId());
        return room(id);
    }

    public Ticket updateTicketPriority(Actor a, int id, String priority) {
        admin(a);
        if (priority == null || !Set.of("URGENT", "HIGH", "NORMAL", "LOW").contains(priority))
            throw new Problem(400, "無效的優先程度");
        lock("customer_service_tickets", "ticket_id", id);
        Ticket t = ticket(id);
        open(t.status());
        // 已分配案件僅負責客服能調整；未分配案件由任一客服設定。
        if (t.adminId() != null)
            assigned(a, t.adminId());
        db.update("UPDATE customer_service_tickets SET priority=?, updated_at=CURRENT_TIMESTAMP WHERE ticket_id=?",
                priority, id);
        if (extension != null && !priority.equals(t.priority())) extension.record(a,"tickets",id,"PRIORITY_CHANGED","優先程度由 "+t.priority()+" 調整為 "+priority,true);
        events.changed(t.memberId());
        return ticket(id);
    }

    public Ticket claimTicket(Actor a, int id) {
        admin(a);
        lock("customer_service_tickets", "ticket_id", id);
        Ticket t = ticket(id);
        open(t.status());
        if (t.adminId() != null && !t.adminId().equals(a.id()))
            throw new Problem(409, "此案件已有負責客服");
        db.update(
                "UPDATE customer_service_tickets SET admin_id=?,status='IN_PROGRESS',updated_at=CURRENT_TIMESTAMP WHERE ticket_id=?",
                a.id(), id);
        events.changed(t.memberId());
        if (extension != null && t.adminId() == null) extension.record(a,"tickets",id,"TICKET_CLAIMED","客服已接手案件",false);
        return ticket(id);
    }

    public Room closeRoom(Actor a, int id) {
        lock("chat_rooms", "support_room_id", id);
        Room r = getRoom(a, id);
        if (a.canSupport())
            assigned(a, r.adminId());
        if (r.status().equals("CLOSED"))
            return r;
        db.update("UPDATE chat_rooms SET status='CLOSED',closed_at=CURRENT_TIMESTAMP WHERE support_room_id=?", id);
        if (extension != null) extension.record(a,"rooms",id,"ROOM_CLOSED","對話已結束",false);
        if (notifications != null && !a.isMember()) notifications.member(r.memberId(),"ROOM_CLOSED","客服對話已結束","可查看對話紀錄或開始新的詢問","/member/support?room="+id);
        events.changed(r.memberId());
        return room(id);
    }

    public Ticket closeTicket(Actor a, int id) {
        admin(a);
        lock("customer_service_tickets", "ticket_id", id);
        Ticket t = ticket(id);
        assigned(a, t.adminId());
        if ("CLOSED".equals(t.status())) return t;
        db.update("UPDATE customer_service_tickets SET status='CLOSED',updated_at=CURRENT_TIMESTAMP WHERE ticket_id=?", id);
        if (extension != null) extension.record(a,"tickets",id,"TICKET_CLOSED","客服案件已結案",false);
        if (notifications != null) notifications.member(t.memberId(),"TICKET_CLOSED","客服案件已結案",t.ticketNo(),"/member/support/"+id);
        events.changed(t.memberId());
        return ticket(id);
    }

    public Ticket escalate(Actor a, int id, EscalateInput input) {
        admin(a);
        lock("chat_rooms", "support_room_id", id);
        Room r = room(id);
        assigned(a, r.adminId());
        // A repeated click returns the already-created ticket rather than inserting
        // another one.
        if (r.ticketId() != null)
            return ticket(r.ticketId());
        open(r.status());
        var categories = db.queryForList("SELECT category_name FROM customer_service_categories WHERE category_id=?",
                String.class,
                input.categoryId());
        if (categories.isEmpty())
            throw new Problem(400, "問題分類不存在");
        List<Integer> requested = input.gameIds() != null ? input.gameIds()
                : input.gameId() == null ? List.of() : List.of(input.gameId());
        if (requested.size() > 20 || requested.stream().anyMatch(g -> g == null || g <= 0))
            throw new Problem(400, "請選擇有效的遊戲，每個案件最多 20 款");
        List<Integer> gameIds = List.copyOf(new LinkedHashSet<>(requested));
        if (Set.of("遊戲問題", "遊戲").contains(categories.get(0).strip()) && gameIds.isEmpty())
            throw new Problem(400, "遊戲問題必須至少選擇一款遊戲");
        if (!gameIds.isEmpty()) {
            String marks = String.join(",", Collections.nCopies(gameIds.size(), "?"));
            int found = db.queryForObject("SELECT COUNT(*) FROM games WHERE game_id IN (" + marks + ")",
                    Integer.class, gameIds.toArray());
            if (found != gameIds.size())
                throw new Problem(400, "選擇的遊戲不存在，請重新搜尋");
        }
        String requestedPriority = input.priority() == null ? "NORMAL"
                : input.priority().strip().toUpperCase(Locale.ROOT);
        if (!Set.of("URGENT", "HIGH", "NORMAL", "LOW").contains(requestedPriority))
            throw new Problem(400, "無效的案件優先程度");
        Integer firstGameId = gameIds.isEmpty() ? null : gameIds.get(0);
        Long serial = db.queryForObject("SELECT NEXT VALUE FOR customer_service_ticket_no_seq", Long.class);
        String ticketNo = String.format(Locale.ROOT, "CS%010d", serial);
        int ticketId = insert("ticket_id",
                "INSERT INTO customer_service_tickets(ticket_no,member_id,category_id,game_id,subject,content,status,priority,created_at,updated_at,admin_id) VALUES (?,?,?,?,?,?,'IN_PROGRESS',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?)",
                ticketNo, r.memberId(), input.categoryId(), firstGameId, input.subject().strip(),
                input.summary().strip(), requestedPriority,
                a.id());
        for (int i = 0; i < gameIds.size(); i++) {
            db.update("INSERT INTO customer_service_ticket_games(ticket_id,game_id,sort_order) VALUES (?,?,?)",
                    ticketId, gameIds.get(i), i);
        }
        db.update(
                "UPDATE chat_rooms SET ticket_id=?,status='CLOSED',closed_at=CURRENT_TIMESTAMP WHERE support_room_id=?",
                ticketId, id);
        if (extension != null) extension.escalated(a,id,ticketId,r.memberId());
        events.changed(r.memberId());
        if (notifications != null)
            notifications.member(r.memberId(), "TICKET_CREATED", "客服已建立正式案件", ticketNo, "/tickets/" + ticketId);
        return ticket(ticketId);
    }

    public Message send(Actor a, boolean chat, int id, MessageInput input) {
        int owner;
        Integer assignedId;
        if (chat) {
            lock("chat_rooms", "support_room_id", id);
            Room r = getRoom(a, id);
            open(r.status());
            owner = r.memberId();
            assignedId = r.adminId();
        } else {
            lock("customer_service_tickets", "ticket_id", id);
            Ticket t = getTicket(a, id);
            open(t.status());
            owner = t.memberId();
            assignedId = t.adminId();
        }
        if (a.canSupport())
            assigned(a, assignedId);
        String table = chat ? "chat_messages" : "customer_service_messages",
                parent = chat ? "support_room_id" : "ticket_id",
                sender = chat ? "member_id" : "sender_member_id", body = chat ? "content" : "message_content";
        int messageId = insert(chat ? "support_message_id" : "message_id",
                "INSERT INTO " + table + "(" + parent + "," + sender + ",admin_id,sender_type," + body
                        + ",sent_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)",
                id, a.canSupport() ? null : a.id(), a.canSupport() ? a.id() : null, a.senderType(),
                input.content().strip());
        if (!chat)
            db.update("UPDATE customer_service_tickets SET updated_at=CURRENT_TIMESTAMP WHERE ticket_id=?", id);
        events.changed(owner);
        if (notifications != null) {
            if (a.canSupport())
                notifications.member(owner, chat ? "CHAT_REPLY" : "TICKET_REPLY", chat ? "客服已回覆聊天室" : "客服已回覆案件",
                        chat ? "聊天室 #" + id + " 有新訊息" : "案件 #" + id + " 有新訊息",
                        chat ? "/member/support?room=" + id : "/member/support/" + id);
            else if (assignedId != null)
                notifications.staff(assignedId, chat ? "CHAT_MESSAGE" : "TICKET_MESSAGE", chat ? "聊天室有新訊息" : "負責案件有新訊息",
                        chat ? "聊天室 #" + id + " 有會員回覆" : "案件 #" + id + " 有會員回覆", chat ? "/admin/chats?room=" + id : "/tickets/" + id);
        }
        return messageQuery(chat, "WHERE x." + (chat ? "support_message_id" : "message_id") + "=?", List.of(messageId),
                false).get(0);
    }

    private List<Message> messageQuery(boolean chat, String where, List<Object> args, boolean desc) {
        String table = chat ? "chat_messages" : "customer_service_messages",
                key = chat ? "support_message_id" : "message_id",
                sender = chat ? "member_id" : "sender_member_id", body = chat ? "content" : "message_content";
        String sql = "SELECT x." + key + " AS id,x.sender_type,x." + body
                + " AS body,x.sent_at,CASE WHEN x.sender_type='ADMIN' THEN a.admin_account ELSE m.username END AS sender_name FROM "
                + table + " x LEFT JOIN members m ON m.member_id=x." + sender
                + " LEFT JOIN admin a ON a.admin_id=x.admin_id " + where + " ORDER BY x." + key
                + (desc ? " DESC" : " ASC") + " OFFSET 0 ROWS FETCH NEXT 51 ROWS ONLY";
        List<Message> messages = db.query(sql,
                (rs, n) -> new Message(rs.getInt("id"), rs.getString("sender_type"), rs.getString("sender_name"),
                        rs.getString("body"), time(rs, "sent_at")),
                args.toArray());
        if (chat || messages.isEmpty())
            return messages;
        Map<Integer, List<Attachment>> attachments = new HashMap<>();
        String placeholders = String.join(",", Collections.nCopies(messages.size(), "?"));
        db.query("SELECT attachment_id,message_id,file_name,content_type,size_bytes,width,height "
                + "FROM customer_service_attachments WHERE message_id IN (" + placeholders + ") ORDER BY attachment_id",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> attachments
                        .computeIfAbsent(rs.getInt("message_id"), ignored -> new ArrayList<>())
                        .add(new Attachment(rs.getInt("attachment_id"), rs.getString("file_name"),
                                rs.getString("content_type"), rs.getInt("size_bytes"), rs.getInt("width"),
                                rs.getInt("height"))),
                messages.stream().map(Message::id).toArray());
        return messages.stream().map(m -> new Message(m.id(), m.senderType(), m.senderName(), m.content(), m.sentAt(),
                List.copyOf(attachments.getOrDefault(m.id(), List.of())))).toList();
    }

    /**
     * Text and images commit together. Reuses the same ownership, assigned-agent
     * and closed-ticket checks as text.
     */
    public Message sendTicketImages(Actor actor, int ticketId, String content, List<MultipartFile> files) {
        Ticket ticket = getTicket(actor, ticketId);
        open(ticket.status());
        if (actor.canSupport())
            assigned(actor, ticket.adminId());
        String text = content == null ? "" : content.strip();
        if (text.length() > 4000)
            throw new Problem(400, "訊息最多 4000 字。");
        if (files == null || files.isEmpty() || files.size() > SupportImageValidator.MAX_FILES) {
            throw new Problem(400, "每則訊息請選擇 1 至 3 張圖片。");
        }
        List<SupportImageValidator.Image> images = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty() || file.getSize() > SupportImageValidator.MAX_BYTES) {
                throw new Problem(400, "每張圖片須介於 1 byte 與 5 MB 之間。");
            }
            try {
                images.add(SupportImageValidator.validate(file.getBytes(), file.getOriginalFilename()));
            } catch (java.io.IOException | IllegalArgumentException e) {
                throw new Problem(400, e instanceof IllegalArgumentException ? e.getMessage() : "無法讀取上傳圖片。");
            }
        }
        // send() locks and rechecks the ticket, so a concurrent close/claim cannot
        // bypass permissions.
        Message message = send(actor, false, ticketId, new MessageInput(text));
        for (var image : images) {
            db.update("""
                    INSERT INTO customer_service_attachments
                        (message_id,file_name,content_type,size_bytes,width,height,image_data)
                    VALUES (?,?,?,?,?,?,?)
                    """, message.id(), image.fileName(), image.contentType(), image.bytes().length,
                    image.width(), image.height(), image.bytes());
        }
        if (notifications != null) {
            if (actor.canSupport())
                notifications.member(ticket.memberId(), "TICKET_REPLY", "客服已回覆案件", "案件 #" + ticketId + " 有新訊息",
                        "/tickets/" + ticketId);
            else if (ticket.adminId() != null)
                notifications.staff(ticket.adminId(), "TICKET_MESSAGE", "負責案件有新訊息", "案件 #" + ticketId + " 有會員回覆",
                        "/tickets/" + ticketId);
        }
        return messageQuery(false, "WHERE x.message_id=?", List.of(message.id()), false).get(0);
    }

    public AttachmentData ticketImage(Actor actor, int ticketId, int attachmentId) {
        getTicket(actor, ticketId); // Membership checked before returning any bytes; closed tickets remain
                                    // readable.
        return db.query("""
                SELECT a.content_type,a.image_data FROM customer_service_attachments a
                JOIN customer_service_messages m ON m.message_id=a.message_id
                WHERE a.attachment_id=? AND m.ticket_id=?
                """, (rs, n) -> new AttachmentData(rs.getString("content_type"), rs.getBytes("image_data")),
                attachmentId, ticketId).stream().findFirst().orElseThrow(Problem::missing);
    }

    public MessagePage messages(Actor a, boolean chat, int id, Integer before, Integer after) {
        if (chat)
            getRoom(a, id);
        else
            getTicket(a, id);
        if (before != null && after != null || before != null && before < 1 || after != null && after < 0)
            throw new Problem(400,
                    "訊息游標無效");
        String parent = chat ? "support_room_id" : "ticket_id", key = chat ? "support_message_id" : "message_id";
        String where = "WHERE x." + parent + "=?";
        List<Object> args = new ArrayList<>();
        args.add(id);
        if (before != null) {
            where += " AND x." + key + "<?";
            args.add(before);
        }
        if (after != null) {
            where += " AND x." + key + ">?";
            args.add(after);
        }
        List<Message> all = messageQuery(chat, where, args, after == null);
        boolean more = all.size() > 50;
        var items = new ArrayList<>(all.subList(0, Math.min(50, all.size())));
        if (after == null)
            Collections.reverse(items);
        return new MessagePage(items, more);
    }

    public List<Room> origins(Actor a, int ticketId) {
        getTicket(a, ticketId);
        return db.query(ROOM_SELECT + "WHERE r.ticket_id=? ORDER BY r.support_room_id", ROOM, ticketId);
    }

    public Map<String, Object> workload(Actor a) {
        admin(a);
        return Map.of("waitingRooms",
                db.queryForObject("SELECT COUNT(*) FROM chat_rooms WHERE status='OPEN' AND admin_id IS NULL",
                        Integer.class),
                "myRooms", db.queryForObject("SELECT COUNT(*) FROM chat_rooms WHERE status='OPEN' AND admin_id=?",
                        Integer.class, a.id()),
                "waitingTickets",
                db.queryForObject(
                        "SELECT COUNT(*) FROM customer_service_tickets WHERE status<>'CLOSED' AND admin_id IS NULL",
                        Integer.class),
                "myTickets",
                db.queryForObject("SELECT COUNT(*) FROM customer_service_tickets WHERE status<>'CLOSED' AND admin_id=?",
                        Integer.class, a.id()));
    }

    public Map<String, Object> workQueue(Actor a, String kind, String scope, String q, int page) {
        admin(a);
        int offset = pageOffset(page);
        if (!Set.of("rooms", "tickets").contains(kind)
                || !Set.of("unassigned", "mine", "others", "all").contains(scope))
            throw new Problem(400,
                    "無效的工作篩選");
        boolean chat = kind.equals("rooms");
        String alias = chat ? "r" : "t", key = chat ? "support_room_id" : "ticket_id";
        String from = chat ? " FROM chat_rooms r JOIN members m ON m.member_id=r.member_id "
                : " FROM customer_service_tickets t JOIN members m ON m.member_id=t.member_id ";
        String where = " WHERE " + alias + ".status<>'CLOSED'";
        List<Object> args = new ArrayList<>();
        switch (scope) {
            case "unassigned":
                where += " AND " + alias + ".admin_id IS NULL";
                break;
            case "mine":
                where += " AND " + alias + ".admin_id=?";
                args.add(a.id());
                break;
            case "others":
                where += " AND " + alias + ".admin_id IS NOT NULL AND " + alias + ".admin_id<>?";
                args.add(a.id());
                break;
            default:
                break;
        }
        String term = searchTerm(q);
        if (!term.isEmpty()) {
            where += " AND (CAST(" + alias + "." + key + " AS VARCHAR(20))=? OR m.username LIKE ? ESCAPE '!'";
            args.add(term.startsWith("#") ? term.substring(1) : term);
            args.add(likeTerm(term));
            if (chat) {
                where += " OR EXISTS(SELECT 1 FROM chat_messages cm WHERE cm.support_room_id=r.support_room_id AND cm.content LIKE ? ESCAPE '!')";
                args.add(likeTerm(term));
            } else {
                where += " OR t.ticket_no LIKE ? ESCAPE '!' OR t.subject LIKE ? ESCAPE '!' OR t.content LIKE ? ESCAPE '!'";
                args.add(likeTerm(term));
                args.add(likeTerm(term));
                args.add(likeTerm(term));
            }
            where += ")";
        }
        int total = db.queryForObject("SELECT COUNT(*)" + from + where, Integer.class, args.toArray());
        args.add(offset);
        String order = " ORDER BY " + alias + ".created_at," + alias + "." + key
                + " OFFSET ? ROWS FETCH NEXT 30 ROWS ONLY";
        List<?> items = chat ? db.query(ROOM_SELECT + where + order, ROOM, args.toArray())
                : withTicketGames(db.query(TICKET_SELECT + where + order,
                        TICKET, args.toArray()));
        return Map.of("items", items, "total", total);
    }

    public List<Map<String, Object>> categories() {
        return db.queryForList(
                "SELECT category_id AS id,category_name AS name,description FROM customer_service_categories "
                + "ORDER BY CASE LTRIM(RTRIM(category_name)) "
                + "WHEN N'帳號與登入' THEN 10 WHEN N'遊戲問題' THEN 20 WHEN N'訂單問題' THEN 30 "
                + "WHEN N'付款問題' THEN 40 WHEN N'退款問題' THEN 50 WHEN N'優惠券問題' THEN 60 "
                + "WHEN N'網站操作異常' THEN 70 WHEN N'其他問題' THEN 90 ELSE 80 END,category_id");
    }

    public List<Map<String, Object>> games(String q) {
        return db.queryForList(
                "SELECT game_id AS id,game_name AS name FROM games WHERE status IN ('PUBLISHED','COMING_SOON','PENDING_OFF_SHELF') AND game_name LIKE ? ORDER BY game_id OFFSET 0 ROWS FETCH NEXT 30 ROWS ONLY",
                "%" + q.strip() + "%");
    }
}
