package gameplatform.support;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import gameplatform.support.model.*;
import gameplatform.support.model.Models.*;
import gameplatform.support.service.SupportService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

class SupportTicketGamesTest {
    JdbcTemplate db;
    SupportService service;
    TransactionTemplate tx;
    Actor admin = new Actor(1, "ADMIN", "admin1", "");
    @BeforeEach void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:games_" + UUID.randomUUID()
                + ";MODE=MSSQLServer;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("demo-schema.sql"),
                new ClassPathResource("demo-data.sql")).execute(source);
        db = new JdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        service = new SupportService(db, mock(EventHub.class), "h2");
        db.update("INSERT INTO chat_rooms(support_room_id,member_id,admin_id,status) VALUES (1,1,1,'OPEN')");
    }
    @Test void savesAllGamesInOrderAndRepeatedTransferDoesNotDuplicate() {
        var input = new EscalateInput(1, null, List.of(3,1,3), "遊戲問題", "摘要");
        Ticket ticket = tx.execute(s -> service.escalate(admin,1,input));
        assertEquals(List.of(3,1),ticket.games().stream().map(Game::id).toList());
        assertEquals(3,ticket.gameId());
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM customer_service_ticket_games",Integer.class));
        assertEquals(ticket.id(),tx.execute(s -> service.escalate(admin,1,input)).id());
        assertEquals("CLOSED",db.queryForObject("SELECT status FROM chat_rooms WHERE support_room_id=1",String.class));
    }
    @Test void invalidGameLeavesNoTicketAndKeepsRoomOpen() {
        assertThrows(Problem.class,() -> tx.execute(s -> service.escalate(admin,1,
                new EscalateInput(1,null,List.of(1,9999),"遊戲問題","摘要"))));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM customer_service_tickets",Integer.class));
        assertEquals("OPEN",db.queryForObject("SELECT status FROM chat_rooms WHERE support_room_id=1",String.class));
    }
    @Test void supportsOldSingleGameRequest() {
        Ticket ticket = tx.execute(s -> service.escalate(admin,1,new EscalateInput(1,2,null,"舊版請求","摘要")));
        assertEquals(List.of(2),ticket.games().stream().map(Game::id).toList());
    }
    @Test void explicitEmptySelectionCannotFallBackToLegacyId() {
        assertThrows(Problem.class,() -> tx.execute(s -> service.escalate(admin,1,
                new EscalateInput(1,2,List.of(),"空選擇","摘要"))));
    }
    @Test void nonGameCategoryCanOmitGames() {
        Ticket ticket = tx.execute(s -> service.escalate(admin,1,new EscalateInput(2,null,List.of(),"帳戶問題","摘要")));
        assertTrue(ticket.games().isEmpty());
        assertNull(ticket.gameId());
    }
}
