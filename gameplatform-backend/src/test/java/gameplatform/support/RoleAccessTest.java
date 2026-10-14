package gameplatform.support;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import gameplatform.support.model.*;
import gameplatform.support.model.Models.MessageInput;
import gameplatform.support.controller.WorkspaceController;
import gameplatform.support.service.SupportService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

class RoleAccessTest {
    Actor actor(int id, String role) { return new Actor(id,role,role+id,""); }
    @Test void managerInheritsBothRolesWithoutInheritingMember() {
        var roles = actor(1,"ADMIN").getAuthorities().stream().map(a -> a.getAuthority()).toList();
        assertEquals(Set.of("ROLE_ADMIN","ROLE_SUPPORT","ROLE_REVIEWER"),new HashSet<>(roles));
        var controller = new WorkspaceController();
        assertNotNull(controller.review(actor(1,"ADMIN")));
        assertNotNull(controller.review(actor(4,"REVIEWER")));
        assertThrows(Problem.class,() -> controller.review(actor(2,"SUPPORT")));
        assertThrows(Problem.class,() -> controller.modules(actor(4,"REVIEWER")));
    }
    @Test void reviewerCannotReadMemberDataWithCollidingIdAndSupportCanReply() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:roles_"+UUID.randomUUID()
            +";MODE=MSSQLServer;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        new ResourceDatabasePopulator(new ClassPathResource("demo-schema.sql"),
            new ClassPathResource("demo-data.sql")).execute(source);
        var db = new JdbcTemplate(source);
        var service = new SupportService(db,mock(EventHub.class),"h2");
        var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        db.update("INSERT INTO chat_rooms(support_room_id,member_id,admin_id,status) VALUES (1,1,2,'OPEN')");
        assertThrows(Problem.class,() -> service.rooms(actor(1,"REVIEWER"),null,false,0));
        assertThrows(Problem.class,() -> service.tickets(actor(1,"REVIEWER"),null,0));
        assertThrows(Problem.class,() -> service.getRoom(actor(1,"REVIEWER"),1));
        assertThrows(Problem.class,() -> service.startRoom(actor(1,"REVIEWER")));
        var message = tx.execute(s -> service.send(actor(2,"SUPPORT"),true,1,new MessageInput("您好")));
        assertEquals("ADMIN",message.senderType());
        assertEquals(2,db.queryForObject("SELECT admin_id FROM chat_messages WHERE support_message_id=?",Integer.class,message.id()));
        assertNull(db.queryForObject("SELECT member_id FROM chat_messages WHERE support_message_id=?",Integer.class,message.id()));
    }
}
