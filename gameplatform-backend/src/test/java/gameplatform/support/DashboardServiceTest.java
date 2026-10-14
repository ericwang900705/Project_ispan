package gameplatform.support;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import gameplatform.support.service.DashboardService;
import gameplatform.support.model.*;
class DashboardServiceTest {
 JdbcTemplate db;DashboardService service;
 @BeforeEach void setup(){
  var source=new DriverManagerDataSource("jdbc:h2:mem:dash_"+UUID.randomUUID()+";MODE=MSSQLServer;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
  new ResourceDatabasePopulator(new ClassPathResource("demo-schema.sql"),new ClassPathResource("demo-data.sql")).execute(source);
  db=new JdbcTemplate(source);service=new DashboardService(db);
 }
 @Test void readsExistingCountsAndRefreshesWithoutSyntheticCommerce(){
  var admin=new Actor(1,"ADMIN","admin1","");var result=service.overview(admin);
  assertEquals(db.queryForObject("SELECT COUNT(*) FROM members",Long.class),result.get("memberCount"));assertEquals(db.queryForObject("SELECT COUNT(*) FROM games",Long.class),result.get("gameCount"));
  assertNull(result.get("monthlyOrderCount"));assertNull(result.get("monthlyRevenue"));
  assertEquals(false,result.get("commerceConnected"));assertEquals(java.util.List.of(),result.get("revenueTrend"));
  db.update("INSERT INTO members(username) VALUES('新會員')");
  assertEquals((Long)result.get("memberCount")+1L,service.overview(admin).get("memberCount"));
 }
 @Test void otherRolesCannotReadPlatformMemberStatistics(){
  assertThrows(Problem.class,()->service.overview(null));
  for(String role:new String[]{"MEMBER","SUPPORT","REVIEWER","PUBLISHER"})
   assertThrows(Problem.class,()->service.overview(new Actor(1,role,"user","")));
 }
}
