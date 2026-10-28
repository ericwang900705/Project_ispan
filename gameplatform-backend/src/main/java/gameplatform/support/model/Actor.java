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
        return isHighestAdmin();
    }

    public boolean isHighestAdmin() {
        return role.equals("ADMIN");
    }

    public boolean isStaff() {
        return List.of("ADMIN", "STAFF", "REVIEWER", "SUPPORT").contains(role);
    }

    public boolean isMember() {
        return role.equals("MEMBER") || role.equals("PUBLISHER");
    }

    public boolean canSupport() {
        return isStaff();
    }

    public boolean canReview() {
        return isStaff();
    }

    // sender_type describes member/admin-table origin, not the user's permission
    // role.
    public String senderType() {
        return isMember() ? "MEMBER" : "ADMIN";
    }

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
        // Functional access is checked against fresh individual grants in
        // SecurityConfig.
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
