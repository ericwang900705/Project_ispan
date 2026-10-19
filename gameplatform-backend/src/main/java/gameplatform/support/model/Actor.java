package gameplatform.support.model;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Replace the standalone account loader when connecting your team's existing
 * login.
 */
public record Actor(int id, String role, String username, String password) implements UserDetails {
    public boolean isAdmin() {
        return isSuperAdmin() || role.equals("ADMIN");
    }

    public boolean isSuperAdmin() { return role.equals("SUPERADMIN"); }

    public boolean isMember() { return role.equals("MEMBER") || role.equals("PUBLISHER"); }
    public boolean canSupport() { return isAdmin() || role.equals("SUPPORT"); }
    public boolean canReview() { return isAdmin() || role.equals("REVIEWER"); }
    // sender_type describes member/admin-table origin, not the user's permission role.
    public String senderType() { return isMember() ? "MEMBER" : "ADMIN"; }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Functional access is checked against fresh individual grants in SecurityConfig.
        if (isSuperAdmin()) return List.of(new SimpleGrantedAuthority("ROLE_SUPERADMIN"),
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ROLE_REVIEWER"), new SimpleGrantedAuthority("ROLE_SUPPORT"));
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
