package gameplatform.backoffice;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import gameplatform.admin.service.AdminPermissionService;
import gameplatform.support.model.Actor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.interceptor.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import java.util.*;
class BackofficeTransactionTests {
 JdbcTemplate db;BackofficeService service;Actor actor=new Actor(1,"ADMIN","admin","");
 @BeforeEach void setup(){var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MSSQLServer;DB_CLOSE_DELAY=-1","sa","");db=new JdbcTemplate(ds);db.execute("CREATE SCHEMA dbo");db.execute("CREATE TABLE dbo.game_reviews(review_id INT PRIMARY KEY,status VARCHAR(20),updated_at TIMESTAMP)");db.update("INSERT INTO dbo.game_reviews VALUES(7,'NORMAL',NULL)");var permissions=mock(AdminPermissionService.class);when(permissions.forActor(actor)).thenReturn(List.of("COMMENT_REVIEW"));var target=new BackofficeService(db,permissions);var source=new AnnotationTransactionAttributeSource();var interceptor=new TransactionInterceptor(new DataSourceTransactionManager(ds),source);var proxy=new ProxyFactory(target);proxy.addAdvice(interceptor);service=(BackofficeService)proxy.getProxy();}
 @Test void reviewAndAuditCommitTogether(){db.execute("CREATE TABLE dbo.admin_operation_logs(admin_id INT,action_code VARCHAR(50),target_id INT,detail VARCHAR(500))");service.moderate(actor,7,new BackofficeService.ReviewInput("HIDDEN","reason"));assertEquals("HIDDEN",db.queryForObject("SELECT status FROM dbo.game_reviews",String.class));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dbo.admin_operation_logs",Integer.class));}
 @Test void auditFailureRollsBackReviewChange(){assertThrows(org.springframework.dao.DataAccessException.class,()->service.moderate(actor,7,new BackofficeService.ReviewInput("HIDDEN","reason")));assertEquals("NORMAL",db.queryForObject("SELECT status FROM dbo.game_reviews",String.class));}
}
