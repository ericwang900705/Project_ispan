package gameplatform.support;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import gameplatform.support.config.SupportDataInitializer;

class SupportDataInitializerTest {
    private JdbcTemplate db;
    private DataSourceTransactionManager manager;

    @BeforeEach
    void setup() {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:seed_" + UUID.randomUUID()
                        + ";MODE=MSSQLServer;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("demo-schema.sql")).execute(source);
        db = new JdbcTemplate(source);
        manager = new DataSourceTransactionManager(source);
        db.update("INSERT INTO members(member_id,username) VALUES (11,'會員甲'),(22,'會員乙')");
        db.update("INSERT INTO admin(admin_id,admin_account) VALUES (33,'客服甲'),(44,'客服乙')");
    }

    private SupportDataInitializer initializer() {
        return new SupportDataInitializer(db, manager, "h2", 11, 22, 33, 44);
    }

    private int count(String sql) {
        return db.queryForObject(sql, Integer.class);
    }

    @Test
    void createsDashboardScenariosWithConfiguredAccountIds() {
        initializer().run(null);
        assertEquals(4, count("SELECT COUNT(*) FROM chat_rooms"));
        assertEquals(3, count("SELECT COUNT(*) FROM customer_service_tickets"));
        assertEquals(6, count("SELECT COUNT(*) FROM chat_messages"));
        assertEquals(5, count("SELECT COUNT(*) FROM customer_service_messages"));
        assertEquals(1, count("SELECT COUNT(*) FROM chat_rooms WHERE status='OPEN' AND admin_id IS NULL"));
        assertEquals(1, count("SELECT COUNT(*) FROM chat_rooms WHERE status='OPEN' AND admin_id=44"));
        assertEquals(1, count("SELECT COUNT(*) FROM customer_service_tickets WHERE status='OPEN' AND admin_id IS NULL"));
        assertEquals(1, count("SELECT COUNT(*) FROM customer_service_tickets WHERE status='IN_PROGRESS' AND admin_id=33"));
        assertEquals(1, count("SELECT COUNT(*) FROM customer_service_tickets WHERE status='CLOSED' AND admin_id=44"));
        assertEquals(1, count("SELECT COUNT(*) FROM chat_rooms WHERE ticket_id IS NOT NULL AND status='CLOSED'"));
        assertEquals(0, count("SELECT COUNT(*) FROM chat_rooms WHERE member_id NOT IN (11,22)"));
        assertEquals(2, count("SELECT COUNT(*) FROM members"));
        assertEquals(2, count("SELECT COUNT(*) FROM admin"));
    }

    @Test
    void secondRunDoesNotDuplicateOrResetChangedStatus() {
        initializer().run(null);
        db.update("UPDATE customer_service_tickets SET status='CLOSED',admin_id=33 WHERE status='OPEN'");
        initializer().run(null);
        assertEquals(4, count("SELECT COUNT(*) FROM chat_rooms"));
        assertEquals(3, count("SELECT COUNT(*) FROM customer_service_tickets"));
        assertEquals(0, count("SELECT COUNT(*) FROM customer_service_tickets WHERE status='OPEN'"));
        assertEquals(11, count("SELECT (SELECT COUNT(*) FROM chat_messages)+(SELECT COUNT(*) FROM customer_service_messages)"));
    }

    @Test
    void existingConversationIsLeftUntouched() {
        db.update("INSERT INTO chat_rooms(member_id,status) VALUES (11,'OPEN')");
        initializer().run(null);
        assertEquals(1, count("SELECT COUNT(*) FROM chat_rooms"));
        assertEquals(0, count("SELECT COUNT(*) FROM customer_service_tickets"));
        assertEquals(0, count("SELECT COUNT(*) FROM customer_service_categories"));
    }

    @Test
    void missingAccountDoesNotCreateSupportData() {
        var invalid = new SupportDataInitializer(db, manager, "h2", 11, 999, 33, 44);
        assertThrows(IllegalStateException.class, () -> invalid.run(null));
        assertEquals(0, count("SELECT COUNT(*) FROM customer_service_categories"));
        assertEquals(0, count("SELECT COUNT(*) FROM customer_service_tickets"));
    }

    @Test
    void failureDuringMessagesRollsBackAllInsertedData() {
        db.execute("ALTER TABLE chat_messages ADD CONSTRAINT reject_seed_message CHECK (sender_type <> 'MEMBER')");
        assertThrows(RuntimeException.class, () -> initializer().run(null));
        assertEquals(0, count("SELECT COUNT(*) FROM chat_rooms"));
        assertEquals(0, count("SELECT COUNT(*) FROM customer_service_tickets"));
        assertEquals(0, count("SELECT COUNT(*) FROM customer_service_messages"));
        assertEquals(0, count("SELECT COUNT(*) FROM customer_service_categories"));
    }
}
