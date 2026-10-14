package gameplatform.support;

import gameplatform.member.service.MemberAccountService;
import gameplatform.support.model.Actor;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemberAccountServiceTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate db;
    MemberAccountService service;
    JavaMailSender mail;
    @Configuration @EnableTransactionManagement static class Config {
        @Bean DriverManagerDataSource source() {
            return new DriverManagerDataSource("jdbc:h2:mem:members_" + UUID.randomUUID()
                + ";MODE=MSSQLServer;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean JdbcTemplate jdbc(DriverManagerDataSource source) {
            var db = new JdbcTemplate(source);
            db.execute("CREATE TABLE members(member_id INT IDENTITY PRIMARY KEY,username VARCHAR(30),email VARCHAR(100),national_id VARCHAR(10),failed_login_count INT DEFAULT 0,locked_until TIMESTAMP,birth_date DATE,address VARCHAR(500),avatar_url VARCHAR(500),total_points INT DEFAULT 0,balance DECIMAL(10,2) DEFAULT 0,is_publisher BIT DEFAULT 0,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            db.execute("CREATE TABLE password_history(password_history_id INT IDENTITY PRIMARY KEY,member_id INT,password_hash VARCHAR(255),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            db.execute("CREATE TABLE member_verifications(verification_id INT IDENTITY PRIMARY KEY,email VARCHAR(100),verification_token VARCHAR(200),expires_at TIMESTAMP,is_verified BIT DEFAULT 0,verified_at TIMESTAMP,watch_token_hash VARCHAR(64))");
            db.execute("CREATE TABLE password_resets(reset_id INT IDENTITY PRIMARY KEY,member_id INT,reset_token VARCHAR(200),expires_at TIMESTAMP,is_reset BIT DEFAULT 0,reset_at TIMESTAMP)");
            return db;
        }
        @Bean DataSourceTransactionManager transactions(DriverManagerDataSource source) { return new DataSourceTransactionManager(source); }
        @Bean JavaMailSender mail() { return mock(JavaMailSender.class); }
        @Bean MemberAccountService accounts(JdbcTemplate db, ObjectProvider<JavaMailSender> mail) {
            return new MemberAccountService(db,new BCryptPasswordEncoder(4),mail,"hero@example.com","https://hero.example");
        }
    }
    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        db = context.getBean(JdbcTemplate.class); service = context.getBean(MemberAccountService.class); mail = context.getBean(JavaMailSender.class);
    }
    @AfterEach void close() { context.close(); }
    MemberAccountService.Register registration(String password) { return new MemberAccountService.Register("aki_test","aki@example.com","A123456789",password); }
    @Test void registrationStoresHashedPasswordAndVerificationIsSingleUse() {
        service.register(registration("Password123!"));
        String hash = db.queryForObject("SELECT password_hash FROM password_history",String.class);
        assertTrue(new BCryptPasswordEncoder().matches("Password123!",hash));
        verify(mail).send(any(SimpleMailMessage.class));
        String token = db.queryForObject("SELECT verification_token FROM member_verifications",String.class);
        service.verify(token);
        assertThrows(ResponseStatusException.class,() -> service.verify(token));
    }
    @Test void mailFailureRollsBackRegistrationAndUnicodePasswordLimitIsCheckedBeforeInsert() {
        doThrow(new MailSendException("SMTP unavailable")).when(mail).send(any(SimpleMailMessage.class));
        assertThrows(MailSendException.class,() -> service.register(registration("Password123!")));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM members",Integer.class));
        assertThrows(ResponseStatusException.class,() -> service.register(registration("密".repeat(25))));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM password_history",Integer.class));
    }
    @Test void resettingOneLinkInvalidatesAllOtherLinksAndReusingCurrentPasswordIsRejected() {
        service.register(registration("Password123!"));
        service.forgot(new MemberAccountService.EmailRequest("aki@example.com"));
        service.forgot(new MemberAccountService.EmailRequest("aki@example.com"));
        var tokens = db.queryForList("SELECT reset_token FROM password_resets ORDER BY reset_id",String.class);
        assertThrows(ResponseStatusException.class,() -> service.reset(new MemberAccountService.Reset(tokens.getFirst(),"Password123!")));
        service.reset(new MemberAccountService.Reset(tokens.getFirst(),"NewPassword456!"));
        assertThrows(ResponseStatusException.class,() -> service.reset(new MemberAccountService.Reset(tokens.getLast(),"OtherPassword789!")));
        assertThrows(ResponseStatusException.class,() -> service.changePassword(new Actor(1,"MEMBER","aki_test",""),new MemberAccountService.ChangePassword("NewPassword456!","NewPassword456!")));
    }
    @Test void birthdayCanBeSetOnceAndAddressRemainsEditable() {
        service.register(registration("Password123!"));var actor=new Actor(1,"MEMBER","aki_test","");
        var date=java.time.LocalDate.of(2000,1,2);service.update(actor,new MemberAccountService.ProfileUpdate(date,"address one",""));
        assertEquals("2000-01-02",service.profile(actor).get("birthDate"));
        assertThrows(ResponseStatusException.class,()->service.update(actor,new MemberAccountService.ProfileUpdate(date.plusDays(1),"changed","")));
        assertEquals("address one",service.profile(actor).get("address"));
        service.update(actor,new MemberAccountService.ProfileUpdate(null,"address two","https://example.com/avatar.png"));
        assertEquals("2000-01-02",service.profile(actor).get("birthDate"));assertEquals("address two",service.profile(actor).get("address"));
    }
    @Test void watchTicketCanOnlyObserveAndCannotActivateVerification() {
        var result=service.register(registration("Password123!"));assertEquals(false,service.verificationStatus(result.watchTicket()).get("verified"));
        assertThrows(ResponseStatusException.class,()->service.verify(result.watchTicket()));
        String token=db.queryForObject("SELECT verification_token FROM member_verifications",String.class);service.verify(token);
        assertEquals(true,service.verificationStatus(result.watchTicket()).get("verified"));
        assertThrows(ResponseStatusException.class,()->service.verificationStatus("wrong"));
    }
    @Test void resendIssuesSeparateWatchTicketAndOldWaitingTabCanObserveCompletion() {
        var first=service.register(registration("Password123!"));var second=service.resend(new MemberAccountService.EmailRequest("aki@example.com"));
        assertNotEquals(first.watchTicket(),second.watchTicket());
        String token=db.queryForObject("SELECT verification_token FROM member_verifications ORDER BY verification_id DESC OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY",String.class);service.verify(token);
        assertEquals(true,service.verificationStatus(first.watchTicket()).get("verified"));assertEquals(true,service.verificationStatus(second.watchTicket()).get("verified"));
    }
}
