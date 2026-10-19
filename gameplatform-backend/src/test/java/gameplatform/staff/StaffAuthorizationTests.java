package gameplatform.staff;

import gameplatform.support.config.StaffPermissionService;
import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Isolated authorization tests; no SQL Server or application context required.
 */
public class StaffAuthorizationTests {
    static class FakeDb extends JdbcTemplate {
        boolean callerActive = true;
        int permissionCount = 0;
        String targetRole = "ADMIN";
        List<String> grants = new ArrayList<>();
        List<String> writes = new ArrayList<>();
        String lastSql;

        // JdbcTemplate has distinct fixed-arity and varargs overloads.
        // Calls such as lock() must also stay inside the fake implementation.
        @Override
        public <T> T queryForObject(String sql, Class<T> type) {
            return queryForObject(sql, type, new Object[0]);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql) {
            return queryForList(sql, new Object[0]);
        }

        @Override
        public <T> List<T> queryForList(String sql, Class<T> type) {
            return queryForList(sql, type, new Object[0]);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> type, Object... args) {
            lastSql = sql;
            int result = sql.contains("sp_getapplock") ? 0
                    : sql.contains("up.permission_id IS NOT NULL") ? permissionCount
                            : sql.contains("admin_account=? OR email=?") ? 0
                                    : callerActive ? 1 : 0;
            return type.cast(result);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("SELECT r.role_code,a.status"))
                return List.of(Map.of("role_code", targetRole, "status", "ACTIVE"));
            return List.of();
        }

        @Override
        public <T> List<T> queryForList(String sql, Class<T> type, Object... args) {
            if (sql.contains("SELECT r.role_code"))
                return List.of(type.cast(targetRole));
            return grants.stream().map(type::cast).toList();
        }

        @Override
        public int update(String sql, Object... args) {
            writes.add(sql + " " + Arrays.toString(args));
            return 1;
        }
    }

    private final FakeDb db = new FakeDb();
    private final Actor top = new Actor(1, "SUPERADMIN", "owner", "");
    private final Actor ordinary = new Actor(2, "ADMIN", "admin", "");
    private final StaffService staff = new StaffService(db, new PasswordEncoder() {
        public String encode(CharSequence raw) {
            return "encoded";
        }

        public boolean matches(CharSequence raw, String encoded) {
            return true;
        }
    });
    private final StaffPermissionService permissions = new StaffPermissionService(db);

    private void denied(int status, Runnable action) {
        Problem e = assertThrows(Problem.class, action::run);
        assertEquals(status, e.httpStatus());
        assertTrue(db.writes.isEmpty());
    }

    @Test
    public void ordinaryAdminCannotListStaff() {
        denied(403, () -> staff.list(ordinary));
    }

    @Test
    public void ordinaryAdminCannotChangeRoles() {
        denied(403, () -> staff.change(ordinary, 3, "ADMIN"));
    }

    @Test
    public void ordinaryAdminCannotGrantPermissions() {
        denied(403, () -> staff.permissions(ordinary, 3, List.of("GAME_REVIEW")));
    }

    @Test
    public void staleSuperAdminCannotMutate() {
        db.callerActive = false;
        denied(403, () -> staff.permissions(top, 3, List.of("GAME_REVIEW")));
    }

    @Test
    public void cannotCreateSecondSuperAdmin() {
        denied(400, () -> staff.change(top, 3, "SUPERADMIN"));
    }

    @Test
    public void cannotDemoteProtectedSuperAdmin() {
        db.targetRole = "SUPERADMIN";
        denied(409, () -> staff.change(top, 1, "ADMIN"));
    }

    @Test
    public void cannotRevokeProtectedSuperAdmin() {
        db.targetRole = "SUPERADMIN";
        denied(409, () -> staff.permissions(top, 1, List.of()));
    }

    @Test
    public void ordinaryAdminMayReceiveSelectedFunctions() {
        staff.permissions(top, 3, List.of("GAME_REVIEW", "GAME_REVIEW", "SUPPORT_CASES"));
        assertEquals(3, db.writes.size()); // DELETE plus two unique grants
        assertTrue(db.writes.stream().anyMatch(s -> s.contains("[3, 1, GAME_REVIEW]")));
    }

    @Test
    public void emptySelectionRevokesAll() {
        staff.permissions(top, 3, List.of());
        assertEquals(1, db.writes.size());
        assertTrue(db.writes.get(0).startsWith("DELETE"));
    }

    @Test
    public void invalidPermissionRejectedBeforeWrites() {
        denied(400, () -> staff.permissions(top, 3, List.of("STAFF_MANAGE")));
    }

    @Test
    public void supportRoleCannotAcquireReviewFunction() {
        db.targetRole = "SUPPORT";
        denied(400, () -> staff.permissions(top, 3, List.of("GAME_REVIEW")));
    }

    @Test
    public void roleChangeClearsGrantsWithoutAutomaticRegrant() {
        staff.change(top, 3, "SUPPORT");
        assertTrue(db.writes.stream().anyMatch(s -> s.startsWith("DELETE FROM dbo.admin_user_permissions")));
        assertFalse(db.writes.stream().anyMatch(s -> s.startsWith("INSERT INTO dbo.admin_user_permissions")));
    }

    @Test
    public void sameRoleDoesNotRestoreRevokedGrants() {
        staff.change(top, 3, "ADMIN");
        assertTrue(db.writes.isEmpty());
    }

    @Test
    public void publicRegistrationAlwaysCreatesPendingAccount() {
        staff.register(new StaffService.Register("testuser", "Test", "test@example.com", "long-password"));
        assertTrue(db.writes.stream().anyMatch(s -> s.contains("[PENDING,")));
        assertFalse(db.writes.stream().anyMatch(s -> s.contains("SUPERADMIN")));
    }

    @Test
    public void ordinaryAdminNeedsFreshGrantForEveryRequest() {
        var auth = new UsernamePasswordAuthenticationToken(ordinary, null, ordinary.getAuthorities());
        assertFalse(permissions.allowed(auth, "GAME_REVIEW"));
        assertTrue(db.lastSql.contains("r.role_code='SUPERADMIN' OR up.permission_id IS NOT NULL"));
        db.permissionCount = 1;
        assertTrue(permissions.allowed(auth, "GAME_REVIEW"));
        db.permissionCount = 0;
        assertFalse(permissions.allowed(auth, "GAME_REVIEW"));
    }

    @Test
    public void ordinaryAdminMeContainsOnlyGrantedFunctions() {
        db.grants = List.of("GAME_REVIEW");
        assertEquals(List.of("GAME_REVIEW"), permissions.forActor(ordinary));
        assertEquals(4, permissions.forActor(top).size());
    }

    @Test
    public void staleSuperAdminCannotPassManagementGuard() {
        assertTrue(permissions.isSuperAdmin(top));
        assertFalse(permissions.isSuperAdmin(ordinary));
        db.callerActive = false;
        assertFalse(permissions.isSuperAdmin(top));
    }
}
