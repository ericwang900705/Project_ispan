package gameplatform.support;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import gameplatform.support.model.*;
import gameplatform.support.service.*;
class PublisherAccessTest {
 static String randomKey(){byte[] bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);return java.util.Base64.getEncoder().encodeToString(bytes);}

 JdbcTemplate db;PublisherService service;
 Actor p1=new Actor(3,"PUBLISHER","publisher1",""),p2=new Actor(4,"PUBLISHER","publisher2","");
 @BeforeEach void setup(){var ds=new DriverManagerDataSource("jdbc:h2:mem:publisher_"+UUID.randomUUID()+";MODE=MSSQLServer;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");new ResourceDatabasePopulator(new ClassPathResource("demo-schema.sql"),new ClassPathResource("demo-data.sql")).execute(ds);db=new JdbcTemplate(ds);service=new PublisherService(db);}
 @SuppressWarnings("unchecked") @Test void listAndDetailNeverExposeOtherPublishersGames(){
  var result=(Map<String,Object>)service.games(p1,0,"");assertEquals(1L,result.get("total"));var items=(List<Map<String,Object>>)result.get("items");assertEquals(1,items.getFirst().get("game_id"));
  assertNotNull(service.game(p1,1));assertThrows(Problem.class,()->service.game(p1,2));assertThrows(Problem.class,()->service.game(p1,3));assertThrows(Problem.class,()->service.game(p2,1));
  assertEquals(0L,((Map<String,Object>)service.games(p1,0,"%" )).get("total"));
 }
 @Test void revokedPublisherAndOtherRolesAreRejected(){
  for(String role:List.of("MEMBER","ADMIN","SUPPORT","REVIEWER"))assertThrows(Problem.class,()->service.overview(new Actor(3,role,"user","")));
  db.update("UPDATE members SET is_publisher=FALSE WHERE member_id=3");assertThrows(Problem.class,()->service.games(p1,0,""));
 }
 @Test void productionUsesLatestHashPublisherFlagAndLockState(){
  db.execute("ALTER TABLE members ADD locked_until TIMESTAMP");
  db.execute("ALTER TABLE members ADD email VARCHAR(100)");
  db.execute("CREATE TABLE member_verifications(email VARCHAR(100),is_verified BIT)");
  db.execute("CREATE TABLE password_history(password_history_id INT PRIMARY KEY,member_id INT,password_hash VARCHAR(255),created_at TIMESTAMP)");
  db.update("INSERT INTO password_history VALUES(1,3,'older',DATEADD('DAY',-1,CURRENT_TIMESTAMP)),(2,3,'latest',CURRENT_TIMESTAMP)");
  var accounts=new FrontAccountService(db);var actor=(Actor)accounts.loadUserByUsername("publisher1");assertEquals("latest",actor.password());assertEquals("PUBLISHER",actor.role());
  db.update("UPDATE members SET is_publisher=FALSE WHERE member_id=3");assertEquals("MEMBER",((Actor)accounts.loadUserByUsername("publisher1")).role());
  db.update("UPDATE members SET locked_until=DATEADD('DAY',1,CURRENT_TIMESTAMP) WHERE member_id=3");assertThrows(org.springframework.security.core.userdetails.UsernameNotFoundException.class,()->accounts.loadUserByUsername("publisher1"));
 }
 @Test void publisherJwtHasNoStaffOrMemberAuthority(){
  var tokens=new JwtService(randomKey(),"gameplatform",900);var jwt=tokens.decoder().decode((String)tokens.issue(p1).get("accessToken"));assertEquals("PUBLISHER",tokens.actor(jwt).role());
  assertEquals(List.of("ROLE_PUBLISHER"),p1.getAuthorities().stream().map(x->x.getAuthority()).toList());
 }
}
