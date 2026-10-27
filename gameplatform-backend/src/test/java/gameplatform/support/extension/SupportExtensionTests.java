package gameplatform.support.extension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import gameplatform.admin.service.AdminPermissionService;
import gameplatform.support.model.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** 真實 JDBC/H2 交易驗證客服會員隔離、私密性、轉案及版本更新。 */
class SupportExtensionTests {

  org.springframework.jdbc.datasource.SingleConnectionDataSource source;

  @AfterEach
  void close() {
    if (source != null) source.destroy();
  }

  JdbcTemplate db;
  SupportExtensionService service;
  TransactionTemplate tx;
  Actor member = new Actor(1, "MEMBER", "Aki", ""),
    other = new Actor(2, "MEMBER", "other", ""),
    support = new Actor(5, "SUPPORT", "agent", ""),
    admin = new Actor(6, "ADMIN", "admin", ""),
    reviewer = new Actor(7, "REVIEWER", "reviewer", "");

  @BeforeEach
  void setup() throws Exception {
    source = new org.springframework.jdbc.datasource.SingleConnectionDataSource(
      "jdbc:h2:mem:" +
        UUID.randomUUID() +
        ";MODE=MSSQLServer;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "sa",
      "",
      true
    );
    db = new JdbcTemplate(source);
    tx = new TransactionTemplate(new DataSourceTransactionManager(source));
    db.execute("CREATE SCHEMA dbo");
    db.execute(
      "CREATE TABLE dbo.members(member_id INT PRIMARY KEY,username VARCHAR(50))"
    );
    db.execute(
      "CREATE TABLE dbo.admin(admin_id INT PRIMARY KEY,admin_name VARCHAR(50))"
    );
    db.execute(
      "CREATE TABLE dbo.customer_service_categories(category_id INT PRIMARY KEY,category_name VARCHAR(50))"
    );
    db.execute(
      "CREATE TABLE dbo.chat_rooms(support_room_id INT PRIMARY KEY,member_id INT,admin_id INT,ticket_id INT,status VARCHAR(20),UNIQUE(support_room_id,member_id))"
    );
    db.execute(
      "CREATE TABLE dbo.customer_service_tickets(ticket_id INT PRIMARY KEY,member_id INT,admin_id INT,status VARCHAR(20),UNIQUE(ticket_id,member_id))"
    );
    db.execute(
      "CREATE TABLE dbo.orders(order_id INT PRIMARY KEY,order_no VARCHAR(30),member_id INT,order_status VARCHAR(20),payment_status VARCHAR(20),total_amount DECIMAL(10,2),payment_type VARCHAR(20),created_at TIMESTAMP,bank_code VARCHAR(20),virtual_account VARCHAR(100),payment_no VARCHAR(100),payment_expire_date VARCHAR(50),UNIQUE(order_id,member_id))"
    );
    db.execute(
      "CREATE TABLE dbo.games(game_id INT PRIMARY KEY,game_name VARCHAR(100))"
    );
    db.execute(
      "CREATE TABLE dbo.order_items(order_item_id INT PRIMARY KEY,order_id INT,game_id INT,unit_price DECIMAL(10,2),quantity INT,coupon_id INT)"
    );
    db.execute(
      "CREATE TABLE dbo.coupons(coupon_id INT PRIMARY KEY,coupon_name VARCHAR(100),discount_type VARCHAR(10),discount_value DECIMAL(10,2),min_spend DECIMAL(10,2),start_at TIMESTAMP,expires_at TIMESTAMP,status VARCHAR(20),total_limit INT,used_count INT,per_member_limit INT)"
    );
    db.execute(
      "CREATE TABLE dbo.member_coupon_claims(member_id INT,coupon_id INT,received_at TIMESTAMP,PRIMARY KEY(member_id,coupon_id))"
    );
    db.execute(
      "CREATE TABLE dbo.coupon_rules(coupon_id INT,scope_type VARCHAR(20),publisher_member_id INT,tag_id INT)"
    );
    db.execute(
      "CREATE TABLE dbo.tags(tag_id INT PRIMARY KEY,tag_name VARCHAR(50))"
    );
    db.execute(
      "CREATE TABLE dbo.admin_operation_logs(admin_id INT,action_code VARCHAR(50),target_id BIGINT,detail VARCHAR(500))"
    );
    String sql = new ClassPathResource(
      "db/support-extension.sql"
    ).getContentAsString(StandardCharsets.UTF_8);
    for (String table : List.of(
      "support_order_links",
      "support_coupon_links",
      "support_reply_templates",
      "support_internal_notes",
      "support_faqs",
      "support_audit_logs"
    )) {
      int start = sql.indexOf("CREATE TABLE dbo." + table + "(");
      int end = sql.indexOf(");", start) + 2;
      db.execute(
        sql.substring(start, end).replace("SYSDATETIME()", "CURRENT_TIMESTAMP")
      );
    }
    db.update("INSERT INTO dbo.members VALUES(1,'Aki'),(2,'other')");
    db.update(
      "INSERT INTO dbo.admin VALUES(5,'客服小林'),(6,'管理員'),(7,'審核人員')"
    );
    db.update(
      "INSERT INTO dbo.customer_service_categories VALUES(1,'付款'),(2,'優惠券')"
    );
    db.update(
      "INSERT INTO dbo.chat_rooms VALUES(12,1,5,NULL,'OPEN'),(13,2,5,NULL,'OPEN'),(14,1,NULL,NULL,'OPEN')"
    );
    db.update(
      "INSERT INTO dbo.customer_service_tickets VALUES(34,1,5,'IN_PROGRESS')"
    );
    db.update(
      "INSERT INTO dbo.orders(order_id,order_no,member_id,order_status,payment_status,total_amount,created_at) VALUES(100,'EC100',1,'PENDING','PENDING',500,CURRENT_TIMESTAMP),(200,'EC200',2,'PAID','PAID',600,CURRENT_TIMESTAMP)"
    );
    db.update("INSERT INTO dbo.games VALUES(7,'星際旅人')");
    db.update("INSERT INTO dbo.order_items VALUES(1,100,7,500,1,NULL)");
    db.update(
      "INSERT INTO dbo.coupons VALUES(8,'秋季八折券','RATE',0.8,0,DATEADD(DAY,-1,CURRENT_TIMESTAMP),DATEADD(DAY,2,CURRENT_TIMESTAMP),'ACTIVE',1,0,1),(9,'other','RATE',0.9,0,DATEADD(DAY,-1,CURRENT_TIMESTAMP),DATEADD(DAY,2,CURRENT_TIMESTAMP),'ACTIVE',NULL,0,1)"
    );
    db.update(
      "INSERT INTO dbo.member_coupon_claims VALUES(1,8,CURRENT_TIMESTAMP),(2,9,CURRENT_TIMESTAMP)"
    );
    var permissions = mock(AdminPermissionService.class);
    when(permissions.forActor(any())).thenAnswer(i -> {
      Actor a = i.getArgument(0);
      return Set.of("ADMIN", "SUPPORT").contains(a.role())
        ? List.of("TICKET_HANDLE")
        : List.of();
    });
    doAnswer(i -> {
      Actor a = i.getArgument(0);
      if (!a.role().equals("ADMIN")) throw new Problem(403, "只有 ADMIN");
      return null;
    })
      .when(permissions)
      .requireHighestAdmin(any());
    service = new SupportExtensionService(
      db,
      permissions,
      mock(EventHub.class),
      "h2"
    );
  }

  void write(Runnable action) {
    tx.executeWithoutResult(s -> action.run());
  }

  int count(String table) {
    return db.queryForObject(
      "SELECT COUNT(*) FROM dbo." + table,
      Integer.class
    );
  }

  void denied(int status, Runnable action) {
    assertEquals(status, assertThrows(Problem.class, action::run).httpStatus());
  }

  @SuppressWarnings("unchecked")
  List<Map<String, Object>> items(Object data) {
    return (List<Map<String, Object>>) ((Map<String, Object>) data).get(
      "items"
    );
  }

  @Test
  void memberOrderOptionsNeverExposeOtherMembers() {
    var rows = items(service.options(member, "rooms", 12, "orders", "", 0));
    assertEquals(1, rows.size());
    assertEquals("EC100", rows.getFirst().get("orderNo"));
    assertTrue(rows.getFirst().keySet().contains("orderNo"));
    denied(404, () -> service.options(other, "rooms", 12, "orders", "", 0));
  }

  @Test
  void staffCanOnlySearchContextOwnerOrders() {
    assertEquals(
      1,
      items(service.options(support, "rooms", 12, "orders", "", 0)).size()
    );
    denied(403, () -> service.options(reviewer, "rooms", 12, "orders", "", 0));
  }

  @Test
  void forgedCrossMemberOrderAndClaimAreRejected() {
    denied(404, () ->
      write(() -> service.link(member, "rooms", 12, "order", 200, false))
    );
    denied(404, () ->
      write(() -> service.link(member, "rooms", 12, "coupon", 9, false))
    );
    assertEquals(0, count("support_order_links"));
  }

  @Test
  void linkingIsIdempotentAndDoesNotConsumeCoupon() {
    write(() -> {
      service.link(member, "rooms", 12, "coupon", 8, false);
      service.link(member, "rooms", 12, "coupon", 8, false);
    });
    assertEquals(1, count("support_coupon_links"));
    assertEquals(1, count("support_audit_logs"));
    assertEquals(
      0,
      db.queryForObject(
        "SELECT used_count FROM dbo.coupons WHERE coupon_id=8",
        Integer.class
      )
    );
  }

  @Test
  void unlinkIsSoftAndRepeatSafe() {
    write(() -> service.link(member, "rooms", 12, "order", 100, false));
    write(() -> {
      service.link(member, "rooms", 12, "order", 100, true);
      service.link(member, "rooms", 12, "order", 100, true);
    });
    assertEquals(1, count("support_order_links"));
    assertEquals(
      0,
      db.queryForObject(
        "SELECT COUNT(*) FROM dbo.support_order_links WHERE removed_at IS NULL",
        Integer.class
      )
    );
    assertEquals(2, count("support_audit_logs"));
  }

  @Test
  void privateNotesRejectMemberAndReviewer() {
    denied(403, () -> service.notes(member, "rooms", 12, Long.MAX_VALUE));
    denied(403, () -> service.notes(reviewer, "rooms", 12, Long.MAX_VALUE));
  }

  @Test
  void notesRequireAssignedAgentAndOpenScope() {
    denied(409, () -> write(() -> service.addNote(admin, "rooms", 12, "no")));
    db.update(
      "UPDATE dbo.chat_rooms SET status='CLOSED' WHERE support_room_id=12"
    );
    denied(409, () -> write(() -> service.addNote(support, "rooms", 12, "no")));
  }

  @Test
  void privateNoteNeverAppearsInMemberTimeline() {
    write(() -> service.addNote(support, "rooms", 12, "PRIVATE-CHECK-RESULT"));
    assertEquals(
      1,
      items(service.notes(support, "rooms", 12, Long.MAX_VALUE)).size()
    );
    assertTrue(
      items(service.logs(member, "rooms", 12, Long.MAX_VALUE, false)).isEmpty()
    );
    assertFalse(
      service
        .related(member, "rooms", 12)
        .toString()
        .contains("PRIVATE-CHECK-RESULT")
    );
  }

  @Test
  void publicTimelineUsesServerWhitelist() {
    service.record(support, "rooms", 12, "ROOM_CLAIMED", "客服已接手", false);
    service.record(
      support,
      "rooms",
      12,
      "PRIORITY_CHANGED",
      "internal priority",
      false
    );
    var rows = items(service.logs(member, "rooms", 12, Long.MAX_VALUE, false));
    assertEquals(1, rows.size());
    assertFalse(rows.getFirst().containsKey("actorName"));
  }

  @Test
  void transferCopiesLinksAndKeepsOriginalNoteIdentity() {
    write(() -> {
      service.link(member, "rooms", 12, "order", 100, false);
      service.link(member, "rooms", 12, "coupon", 8, false);
      service.addNote(support, "rooms", 12, "handoff");
      db.update(
        "UPDATE dbo.chat_rooms SET ticket_id=34,status='CLOSED' WHERE support_room_id=12"
      );
      service.escalated(support, 12, 34, 1);
    });
    assertEquals(2, count("support_order_links"));
    assertEquals(2, count("support_coupon_links"));
    assertEquals(
      1,
      items(service.notes(support, "tickets", 34, Long.MAX_VALUE)).size()
    );
  }

  @Test
  void unrelatedMembersCannotUsePrivateScopeAndClosedLinksAreReadOnly() {
    denied(404, () ->
      write(() -> service.link(other, "rooms", 12, "order", 200, false))
    );
    db.update(
      "UPDATE dbo.chat_rooms SET status='CLOSED' WHERE support_room_id=12"
    );
    denied(409, () ->
      write(() -> service.link(member, "rooms", 12, "order", 100, false))
    );
  }

  SupportExtensionService.ContentInput content(String state, Integer version) {
    return new SupportExtensionService.ContentInput(
      "優惠券問題",
      "<script>text only</script>",
      2,
      state,
      10,
      version
    );
  }

  @Test
  void faqDraftIsNotPublicAndPublishIsVisible() {
    write(() ->
      service.saveContent(admin, "faqs", null, content("DRAFT", null))
    );
    assertTrue(
      items(service.content(null, "faqs", false, "", null, null, 0)).isEmpty()
    );
    write(() ->
      service.saveContent(admin, "faqs", 1L, content("PUBLISHED", 0))
    );
    assertEquals(
      1,
      items(service.content(null, "faqs", false, "", null, null, 0)).size()
    );
  }

  @Test
  void globalContentManagementRejectsSupport() {
    denied(403, () -> service.content(support, "faqs", true, "", "", null, 0));
    denied(403, () ->
      write(() ->
        service.saveContent(
          support,
          "reply-templates",
          null,
          content("ACTIVE", null)
        )
      )
    );
  }

  @Test
  void staleVersionCannotOverwriteNewContent() {
    write(() ->
      service.saveContent(
        admin,
        "reply-templates",
        null,
        content("ACTIVE", null)
      )
    );
    write(() ->
      service.saveContent(admin, "reply-templates", 1L, content("INACTIVE", 0))
    );
    denied(409, () ->
      write(() ->
        service.saveContent(admin, "reply-templates", 1L, content("ACTIVE", 0))
      )
    );
    assertTrue(
      items(
        service.content(support, "reply-templates", false, "", null, null, 0)
      ).isEmpty()
    );
  }

  @Test
  void failedTransactionRollsBackLinksAndAudit() {
    assertThrows(IllegalStateException.class, () ->
      write(() -> {
        service.link(member, "rooms", 12, "order", 100, false);
        throw new IllegalStateException();
      })
    );
    assertEquals(0, count("support_order_links"));
    assertEquals(0, count("support_audit_logs"));
  }

  @Test
  void literalSearchAndValidation() {
    assertTrue(
      items(service.options(member, "rooms", 12, "orders", "%_", 0)).isEmpty()
    );
    denied(400, () -> service.options(member, "rooms", 12, "orders", "", -1));
    denied(400, () -> service.context(member, "admin", 12, false));
    denied(400, () -> write(() -> service.addNote(support, "rooms", 12, " ")));
  }
}
