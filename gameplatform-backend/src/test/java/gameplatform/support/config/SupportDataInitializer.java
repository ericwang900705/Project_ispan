package gameplatform.support.config;

import java.sql.Statement;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 開發用客服資料：admin 表包含管理者、審核人員與客服人員。只引用既有會員與客服，不建立登入帳號。
 * 同時啟用 support-seed profile 與 app.support.seed.enabled 才會執行。
 */
@Component
@Profile("support-seed")
@ConditionalOnProperty(name = "app.support.seed.enabled", havingValue = "true")
public class SupportDataInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SupportDataInitializer.class);
    private final JdbcTemplate db;
    private final TransactionTemplate transaction;
    private final boolean sqlServer;
    private final int member1;
    private final int member2;
    private final int admin1;
    private final int admin2;

    public SupportDataInitializer(JdbcTemplate db, PlatformTransactionManager manager,
            @Value("${app.dialect}") String dialect,
            @Value("${app.member1-id}") int member1,
            @Value("${app.member2-id}") int member2,
            @Value("${app.admin1-id}") int admin1,
            @Value("${app.admin2-id}") int admin2) {
        this.db = db;
        this.transaction = new TransactionTemplate(manager);
        if (!dialect.equals("h2") && !dialect.equals("sqlserver")) {
            throw new IllegalArgumentException("客服測試資料只支援 h2 / sqlserver");
        }
        this.sqlServer = dialect.equals("sqlserver");
        this.member1 = member1;
        this.member2 = member2;
        this.admin1 = admin1;
        this.admin2 = admin2;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean created = Boolean.TRUE.equals(transaction.execute(status -> seed()));
        if (created) {
            log.info("客服測試資料完成：4 個聊天室、3 個案件、6 筆聊天訊息、5 筆案件訊息；管理者及客服角色可操作客服。");
        } else {
            log.info("客服已有聊天室或案件資料，跳過測試資料初始化，未修改既有資料。");
        }
    }

    private boolean seed() {
        // 不覆蓋或混入既有客服資料。SQL Server 用交易內資料表鎖防止重複初始化。
        String hint = sqlServer ? " WITH (TABLOCKX, HOLDLOCK)" : "";
        long rooms = count("SELECT COUNT(*) FROM chat_rooms" + hint);
        long tickets = count("SELECT COUNT(*) FROM customer_service_tickets" + hint);
        if (rooms != 0 || tickets != 0) {
            return false;
        }
        if (member1 <= 0 || member2 <= 0 || admin1 <= 0 || admin2 <= 0
                || member1 == member2 || admin1 == admin2) {
            throw new IllegalStateException("請設定兩個不同的會員 ID，以及兩個不同的客服 ADMIN ID。");
        }
        requireAccount("members", "member_id", member1);
        requireAccount("members", "member_id", member2);
        requireAccount("admin", "admin_id", admin1);
        requireAccount("admin", "admin_id", admin2);

        var categories = db.queryForList(
                "SELECT category_id FROM customer_service_categories WHERE category_name=?", Integer.class, "其他");
        int category = categories.isEmpty()
                ? insert("category_id", "INSERT INTO customer_service_categories(category_name,description) VALUES (?,?)",
                        "其他", "其他需要協助的問題")
                : categories.get(0);

        int waiting = ticket(member1, category, "【測試】購買後未收到通知", "已完成購買，但沒有收到通知信。", "OPEN", null);
        ticketMessage(waiting, member1, null, "MEMBER", "請協助確認通知寄送情況。");

        int working = ticket(member2, category, "【測試】下載中斷", "下載到一半中斷，已由即時聊天轉為案件。", "IN_PROGRESS", admin1);
        ticketMessage(working, member2, null, "MEMBER", "重新下載仍然中斷。");
        ticketMessage(working, null, admin1, "ADMIN", "已收到，正在協助確認下載狀況。");

        int closed = ticket(member1, category, "【測試】通知設定已完成", "已協助會員完成通知設定。", "CLOSED", admin2);
        ticketMessage(closed, member1, null, "MEMBER", "現在已能收到通知，謝謝。");
        ticketMessage(closed, null, admin2, "ADMIN", "已確認處理完成，此案件結案。");

        int origin = room(member2, "CLOSED", admin1, working);
        chatMessage(origin, member2, null, "MEMBER", "下載一直中斷，想請客服協助。");
        chatMessage(origin, null, admin1, "ADMIN", "需要進一步調查，已轉為案件繼續追蹤。");

        int history = room(member1, "CLOSED", admin2, null);
        chatMessage(history, null, admin2, "ADMIN", "您的問題已解決，這次聊天已結束。");

        int unassigned = room(member1, "OPEN", null, null);
        chatMessage(unassigned, member1, null, "MEMBER", "【測試】想詢問購買紀錄，請客服協助。");

        int assigned = room(member2, "OPEN", admin2, null);
        chatMessage(assigned, member2, null, "MEMBER", "【測試】還有另一個問題，想詢問通知設定。");
        chatMessage(assigned, null, admin2, "ADMIN", "您好，我是客服，請描述您的問題。");
        return true;
    }

    private long count(String sql, Object... args) {
        return Objects.requireNonNull(db.queryForObject(sql, Long.class, args));
    }

    private void requireAccount(String table, String key, int id) {
        // table / key 只能來自上方寫死的程式常數，不接收外部輸入。
        if (count("SELECT COUNT(*) FROM " + table + " WHERE " + key + "=?", id) != 1) {
            throw new IllegalStateException("找不到 " + table + "." + key + "=" + id
                    + "；請先由會員／客服模組建立帳號，再設定 SUPPORT_MEMBER1_ID 等 ID。");
        }
    }

    private int ticket(int member, int category, String subject, String content, String status, Integer admin) {
        // 測試單號使用獨立前綴，不占用正式 CS 流水號，也不依賴特定 sequence 的目前數值。
        String number = "DEMO-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        return insert("ticket_id", """
                INSERT INTO customer_service_tickets
                    (ticket_no,member_id,category_id,game_id,subject,content,status,created_at,updated_at,admin_id)
                VALUES (?,?,?,NULL,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?)
                """, number, member, category, subject, content, status, admin);
    }

    private int room(int member, String status, Integer admin, Integer ticket) {
        return insert("support_room_id", """
                INSERT INTO chat_rooms(member_id,status,created_at,closed_at,admin_id,ticket_id)
                VALUES (?,?,CURRENT_TIMESTAMP,CASE WHEN ?='CLOSED' THEN CURRENT_TIMESTAMP ELSE NULL END,?,?)
                """, member, status, status, admin, ticket);
    }

    private void chatMessage(int room, Integer member, Integer admin, String type, String content) {
        db.update("""
                INSERT INTO chat_messages(support_room_id,member_id,admin_id,sender_type,content,sent_at)
                VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)
                """, room, member, admin, type, content);
    }

    private void ticketMessage(int ticket, Integer member, Integer admin, String type, String content) {
        db.update("""
                INSERT INTO customer_service_messages(ticket_id,sender_member_id,admin_id,sender_type,message_content,sent_at)
                VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)
                """, ticket, member, admin, type, content);
    }

    private int insert(String keyColumn, String sql, Object... args) {
        var keys = new GeneratedKeyHolder();
        db.update(connection -> {
            var statement = sqlServer ? connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
                    : connection.prepareStatement(sql, new String[] { keyColumn });
            for (int index = 0; index < args.length; index++) {
                statement.setObject(index + 1, args[index]);
            }
            return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).intValue();
    }
}
