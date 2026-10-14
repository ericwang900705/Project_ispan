package gameplatform.support;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;

import gameplatform.support.model.Actor;
import gameplatform.support.model.EventHub;
import gameplatform.support.model.Problem;
import gameplatform.support.service.SupportImageValidator;
import gameplatform.support.service.SupportService;

class SupportImageTest {
    private JdbcTemplate db;
    private SupportService service;
    private TransactionTemplate tx;
    private final Actor member = new Actor(1, "MEMBER", "member1", "");
    private final Actor otherMember = new Actor(2, "MEMBER", "member2", "");
    private final Actor admin = new Actor(1, "ADMIN", "admin1", "");
    private final Actor otherAdmin = new Actor(2, "ADMIN", "admin2", "");

    @BeforeEach
    void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:images_" + UUID.randomUUID()
                + ";MODE=MSSQLServer;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("demo-schema.sql"),
                new ClassPathResource("demo-data.sql")).execute(source);
        db = new JdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        service = new SupportService(db, mock(EventHub.class), "h2");
        db.update("""
                INSERT INTO customer_service_tickets(ticket_id,ticket_no,member_id,category_id,subject,content,status,admin_id)
                VALUES (1,'TEST-1',1,3,'圖片測試','問題內容','IN_PROGRESS',1),
                       (2,'TEST-2',2,3,'另一位會員案件','問題內容','IN_PROGRESS',2)
                """);
    }

    private byte[] png() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }

    private MockMultipartFile image() throws Exception {
        return new MockMultipartFile("images", "截圖.png", "image/png", png());
    }

    @Test
    void imageOnlyMessageIsVisibleToOwnerAndStaffButNotOtherMembers() throws Exception {
        var file = image();
        var message = tx.execute(status -> service.sendTicketImages(member, 1, "", List.of(file)));
        assertEquals("", message.content());
        assertEquals(1, message.attachments().size());
        int attachmentId = message.attachments().get(0).id();
        assertArrayEquals(service.ticketImage(member, 1, attachmentId).bytes(),
                service.ticketImage(admin, 1, attachmentId).bytes());
        assertThrows(Problem.class, () -> service.ticketImage(otherMember, 1, attachmentId));
        assertThrows(Problem.class, () -> service.ticketImage(admin, 2, attachmentId));
        var page = service.messages(member, false, 1, null, null);
        assertEquals(attachmentId, page.items().get(0).attachments().get(0).id());
    }

    @Test
    void replyAuthorizationAndClosedTicketRulesAlsoApplyToImages() throws Exception {
        var file = image();
        assertThrows(Problem.class, () -> tx.execute(s -> service.sendTicketImages(otherMember, 1, "", List.of(file))));
        assertThrows(Problem.class, () -> tx.execute(s -> service.sendTicketImages(otherAdmin, 1, "", List.of(file))));
        var item = tx.execute(s -> service.sendTicketImages(admin, 1, "客服截圖", List.of(file)));
        tx.execute(s -> service.closeTicket(admin, 1));
        assertThrows(Problem.class, () -> tx.execute(s -> service.sendTicketImages(member, 1, "", List.of(file))));
        assertNotNull(service.ticketImage(member, 1, item.attachments().get(0).id()));
    }

    @Test
    void forgedFilesAndTooManyImagesAreRejectedWithoutCreatingMessages() throws Exception {
        var fake = new MockMultipartFile("images", "fake.png", "image/png", "<script>alert(1)</script>".getBytes());
        var file = image();
        assertThrows(Problem.class, () -> tx.execute(s -> service.sendTicketImages(member, 1, "", List.of(fake))));
        assertThrows(Problem.class, () -> tx.execute(s -> service.sendTicketImages(member, 1, "", List.of(file, file, file, file))));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM customer_service_messages", Integer.class));
    }

    @Test
    void failedAttachmentInsertRollsBackTheMessage() throws Exception {
        var file = image();
        db.execute("ALTER TABLE customer_service_attachments ADD CONSTRAINT reject_png CHECK(content_type <> 'image/png')");
        assertThrows(RuntimeException.class, () -> tx.execute(s -> service.sendTicketImages(member, 1, "test", List.of(file))));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM customer_service_messages", Integer.class));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM customer_service_attachments", Integer.class));
    }

    @Test
    void validatorChecksBytesDimensionsAndUsesDecodedFormat() throws Exception {
        var valid = SupportImageValidator.validate(png(), "C:\\fakepath\\screenshot.jpg");
        assertEquals("screenshot.png", valid.fileName());
        assertEquals("image/png", valid.contentType());
        assertEquals(20, valid.width());
        assertThrows(IllegalArgumentException.class,
                () -> SupportImageValidator.validate(new byte[SupportImageValidator.MAX_BYTES + 1], "huge.png"));
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4097, 1, BufferedImage.TYPE_INT_RGB), "png", output);
        assertThrows(IllegalArgumentException.class, () -> SupportImageValidator.validate(output.toByteArray(), "wide.png"));
    }
}
