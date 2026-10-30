package gameplatform.support.extension;

import static org.junit.jupiter.api.Assertions.*;

import gameplatform.admin.service.AdminPermissionService;
import gameplatform.support.model.Actor;
import gameplatform.support.model.EventHub;
import gameplatform.support.model.Problem;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/** 使用實際服務驗證關聯權限與資料隔離；JDBC 替身不連資料庫，也不使用 H2。 */
class SupportLinkAuthorizationTests {
  static class Database extends JdbcTemplate {
    int reads;
    int assigned = 10;
    boolean ownsTarget = true;
    boolean linked;
    String status = "OPEN";
    final List<String> writes = new ArrayList<>();

    @Override
    public List<Map<String, Object>> queryForList(String sql, Object... args) {
      reads++;
      return List.of(Map.of("member_id", 1, "admin_id", assigned, "status", status));
    }

    @Override
    public <T> List<T> queryForList(String sql, Class<T> type, Object... args) {
      reads++;
      return List.of();
    }

    @Override
    public <T> T queryForObject(String sql, Class<T> type, Object... args) {
      reads++;
      boolean ownership = sql.contains("FROM dbo.orders") || sql.contains("FROM dbo.member_coupon_claims");
      return type.cast(ownership ? (ownsTarget ? 1 : 0) : (linked ? 1 : 0));
    }

    @Override
    public int update(String sql, Object... args) {
      writes.add(sql);
      return 1;
    }
  }

  static SupportExtensionService service(Database db, boolean grant) {
    var permissions = new AdminPermissionService(null, null, null, db) {
      @Override
      public List<String> forActor(Actor actor) {
        return grant ? List.of("TICKET_HANDLE") : List.of();
      }
    };
    return new SupportExtensionService(db, permissions, new EventHub(null, null), "sqlserver");
  }

  @TestFactory
  Stream<DynamicTest> membersPublishersGuestsAndUnprivilegedStaffCannotWrite() {
    return Stream.of("MEMBER", "PUBLISHER", "GUEST", "STAFF", "REVIEWER").flatMap(role ->
      Stream.of("rooms", "tickets").flatMap(scope ->
        Stream.of("order", "coupon").flatMap(kind -> Stream.of(false, true).map(remove ->
          DynamicTest.dynamicTest(role + "/" + scope + "/" + kind + "/remove=" + remove, () -> {
            var db = new Database();
            // 即使錯誤地授予會員權限，仍不可繞過角色檢查。
            var service = service(db, role.equals("MEMBER") || role.equals("PUBLISHER"));
            Actor actor = role.equals("GUEST") ? null : new Actor(1, role, "test", "");
            var error = assertThrows(Problem.class, () -> service.link(actor, scope, 12, kind, 20, remove));
            assertEquals(403, error.httpStatus());
            assertEquals(0, db.reads, "拒絕後不得查詢案件或訂單");
            assertTrue(db.writes.isEmpty(), "拒絕後不得變更資料或操作紀錄");
          })))));
  }

  @TestFactory
  Stream<DynamicTest> assignedAuthorizedStaffCanAddAndRemove() {
    return Stream.of("ADMIN", "SUPPORT", "STAFF").flatMap(role ->
      Stream.of("rooms", "tickets").flatMap(scope ->
        Stream.of("order", "coupon").flatMap(kind -> Stream.of(false, true).map(remove ->
          DynamicTest.dynamicTest(role + "/" + scope + "/" + kind + "/remove=" + remove, () -> {
            var db = new Database();
            db.linked = remove;
            service(db, true).link(new Actor(10, role, "test", ""), scope, 12, kind, 20, remove);
            assertTrue(db.reads > 0);
            if (!remove) {
              assertTrue(db.writes.stream().anyMatch(sql -> sql.startsWith("INSERT INTO dbo.support_" + kind + "_links")));
              assertTrue(db.writes.stream().anyMatch(sql -> sql.contains("support_audit_logs")));
            }
            else {
              assertTrue(db.writes.stream().anyMatch(sql -> sql.startsWith("UPDATE dbo.support_" + kind + "_links")));
              assertTrue(db.writes.stream().anyMatch(sql -> sql.contains("support_audit_logs")));
            }
          })))));
  }

  @Test
  void authorizedStaffStillMustClaimAndCannotChangeClosedCasesOrOtherMembersData() {
    var actor = new Actor(10, "SUPPORT", "test", "");
    for (boolean remove : List.of(false, true)) {
      var unassigned = new Database();
      unassigned.assigned = 99;
      assertEquals(409, assertThrows(Problem.class,
          () -> service(unassigned, true).link(actor, "tickets", 12, "order", 20, remove)).httpStatus());
      assertTrue(unassigned.writes.isEmpty());

      var closed = new Database();
      closed.status = "CLOSED";
      assertEquals(409, assertThrows(Problem.class,
          () -> service(closed, true).link(actor, "rooms", 12, "order", 20, remove)).httpStatus());
      assertTrue(closed.writes.isEmpty());

      var foreign = new Database();
      foreign.ownsTarget = false;
      assertEquals(404, assertThrows(Problem.class,
          () -> service(foreign, true).link(actor, "tickets", 12, "order", 20, remove)).httpStatus());
      assertTrue(foreign.writes.isEmpty());
    }
  }

  @Test
  void memberRelatedDataIsNeverWritable() {
    var db = new Database();
    Map<?, ?> data = (Map<?, ?>) service(db, true).related(new Actor(1, "MEMBER", "test", ""), "rooms", 12);
    assertEquals(false, data.get("writable"));
  }
}
