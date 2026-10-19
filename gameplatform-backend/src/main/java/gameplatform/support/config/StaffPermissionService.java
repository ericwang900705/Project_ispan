package gameplatform.support.config;

import gameplatform.support.model.Actor;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Service;

/**
 * Decisions use fresh SQL rows: a revoked grant takes effect without reissuing
 * JWTs.
 */
@Service
public class StaffPermissionService {
    public static final Set<String> CODES = Set.of(
            "GAME_REVIEW", "PUBLISHER_REVIEW", "MEMBER_MANAGE", "SUPPORT_CASES");
    private final JdbcTemplate db;

    public StaffPermissionService(JdbcTemplate db) {
        this.db = db;
    }

    public static Set<String> options(String role) {
        return switch (role) {
            case "ADMIN", "SUPERADMIN" -> CODES;
            case "REVIEWER" -> Set.of("GAME_REVIEW", "PUBLISHER_REVIEW", "MEMBER_MANAGE");
            case "SUPPORT" -> Set.of("SUPPORT_CASES");
            default -> Set.of(); // PENDING has no grants
        };
    }

    public boolean allowed(Authentication auth, String permission) {
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken
                || !(auth.getPrincipal() instanceof Actor actor)
                || !CODES.contains(permission)
                || !Set.of("SUPERADMIN", "ADMIN", "REVIEWER", "SUPPORT").contains(actor.role())
                || (!actor.isSuperAdmin() && !options(actor.role()).contains(permission)))
            return false;
        Integer count = db.queryForObject("""
                SELECT COUNT(*) FROM dbo.admin a
                JOIN dbo.admin_roles r ON r.admin_role_id=a.admin_role_id
                LEFT JOIN dbo.admin_permissions p ON p.permission_code=?
                LEFT JOIN dbo.admin_user_permissions up
                  ON up.admin_id=a.admin_id AND up.permission_id=p.permission_id
                WHERE a.admin_id=? AND a.admin_account=? AND a.status='ACTIVE'
                  AND r.role_code=? AND (r.role_code='SUPERADMIN' OR up.permission_id IS NOT NULL)
                """, Integer.class, permission, actor.id(), actor.username(), actor.role());
        return count != null && count > 0;
    }

    public java.util.List<String> forActor(Actor actor) {
        if (actor == null || !Set.of("SUPERADMIN", "ADMIN", "REVIEWER", "SUPPORT").contains(actor.role()))
            return java.util.List.of();
        Integer valid = db.queryForObject(
                "SELECT COUNT(*) FROM dbo.admin a JOIN dbo.admin_roles r ON r.admin_role_id=a.admin_role_id WHERE a.admin_id=? AND a.admin_account=? AND a.status='ACTIVE' AND r.role_code=?",
                Integer.class, actor.id(), actor.username(), actor.role());
        if (valid == null || valid != 1)
            return java.util.List.of();
        if (actor.isSuperAdmin())
            return CODES.stream().sorted().toList();
        return db.queryForList(
                "SELECT p.permission_code FROM dbo.admin_user_permissions u JOIN dbo.admin_permissions p ON p.permission_id=u.permission_id WHERE u.admin_id=? ORDER BY p.permission_code",
                String.class, actor.id()).stream().filter(options(actor.role())::contains).toList();
    }

    public boolean isSuperAdmin(Actor actor) {
        if (actor == null || !actor.isSuperAdmin())
            return false;
        Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM dbo.admin a JOIN dbo.admin_roles r ON r.admin_role_id=a.admin_role_id WHERE a.admin_id=? AND a.admin_account=? AND a.status='ACTIVE' AND r.role_code='SUPERADMIN'",
                Integer.class, actor.id(), actor.username());
        return count != null && count == 1;
    }

    public AuthorizationManager<RequestAuthorizationContext> superAdmin() {
        return (auth, ctx) -> {
            Authentication a = auth.get();
            return new AuthorizationDecision(a != null && a.isAuthenticated()
                    && a.getPrincipal() instanceof Actor actor && isSuperAdmin(actor));
        };
    }

    public AuthorizationManager<RequestAuthorizationContext> require(String permission) {
        return (auth, ctx) -> new AuthorizationDecision(allowed(auth.get(), permission));
    }

    public AuthorizationManager<RequestAuthorizationContext> memberOrSupport() {
        return (auth, ctx) -> {
            Authentication a = auth.get();
            if (a != null && a.isAuthenticated() && a.getPrincipal() instanceof Actor actor
                    && actor.isMember())
                return new AuthorizationDecision(true);
            return new AuthorizationDecision(allowed(a, "SUPPORT_CASES"));
        };
    }
}
