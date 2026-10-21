package gameplatform.support.config;

import gameplatform.support.model.Actor;
import java.util.Set;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Service;

/**
 * Decisions read JPA grants on every request: a revoked grant takes effect
 * without reissuing JWTs.
 */
@Service
public class StaffPermissionService {
    public static final Set<String> CODES = gameplatform.admin.service.AdminPermissionService.BUSINESS_CODES;
    public static final Set<String> RESERVED_CODES = gameplatform.admin.service.AdminPermissionService.RESERVED_CODES;
    public static final Set<String> ALL_CODES = gameplatform.admin.service.AdminPermissionService.ALL_CODES;
    private final gameplatform.admin.service.AdminPermissionService permissions;

    public StaffPermissionService(gameplatform.admin.service.AdminPermissionService permissions) {
        this.permissions = permissions;
    }

    public static Set<String> options(String role) {
        return gameplatform.admin.service.AdminPermissionService.fixedPermissions(role);
    }

    public boolean allowed(Authentication auth, String permission) {
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)
                && auth.getPrincipal() instanceof Actor actor && ALL_CODES.contains(permission)
                && permissions.forActor(actor).contains(permission);
    }

    public java.util.List<String> forActor(Actor actor) {
        return permissions.forActor(actor);
    }

    public boolean isHighestAdmin(Actor actor) {
        return permissions.isHighestAdmin(actor);
    }

    public AuthorizationManager<RequestAuthorizationContext> highestAdmin() {
        return (auth, ctx) -> {
            Authentication a = auth.get();
            return new AuthorizationDecision(a != null && a.isAuthenticated()
                    && a.getPrincipal() instanceof Actor actor && isHighestAdmin(actor));
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
            return new AuthorizationDecision(allowed(a, "TICKET_HANDLE"));
        };
    }
}
