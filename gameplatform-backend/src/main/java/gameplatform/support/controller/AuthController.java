package gameplatform.support.controller;

import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import gameplatform.support.model.Actor;
import gameplatform.support.model.EventHub;
import gameplatform.support.service.JwtService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserDetailsService memberAccounts;
    private final UserDetailsService staffAccounts;
    private final BCryptPasswordEncoder passwords;
    private final JwtService tokens;
    private final EventHub events;
    private final String dummyHash;
    private final org.springframework.jdbc.core.JdbcTemplate memberDb;

    public AuthController(
            @org.springframework.beans.factory.annotation.Qualifier("memberAccounts") UserDetailsService memberAccounts,
            @org.springframework.beans.factory.annotation.Qualifier("staffAccounts") UserDetailsService staffAccounts,
            BCryptPasswordEncoder passwords, JwtService tokens,
            EventHub events, org.springframework.jdbc.core.JdbcTemplate memberDb) {
        this.memberAccounts = memberAccounts;
        this.staffAccounts = staffAccounts;
        this.passwords = passwords;
        this.tokens = tokens;
        this.events = events;
        this.memberDb = memberDb;
        dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
    }

    public record Login(@NotBlank @Size(max = 100) String username, @NotBlank @Size(max = 200) String password) {
    }

    // Legacy endpoint belongs to front-end members/publishers; it cannot bypass the staff login boundary.
    @PostMapping({"/login", "/member/login"})
    public Map<String, Object> memberLogin(@Valid @RequestBody Login input) {
        return authenticate(input, false);
    }

    @PostMapping("/staff/login")
    public Map<String, Object> staffLogin(@Valid @RequestBody Login input) {
        return authenticate(input, true);
    }

    private Map<String, Object> authenticate(Login input, boolean staff) {
        UserDetailsService accounts = staff ? staffAccounts : memberAccounts;
        UserDetails user = null;
        try {
            user = accounts.loadUserByUsername(input.username());
        } catch (UsernameNotFoundException ignored) {
        }
        boolean matches = passwords.matches(input.password(), user == null ? dummyHash : user.getPassword());
        boolean correctArea = user instanceof Actor actor && (staff
                ? java.util.Set.of("ADMIN", "REVIEWER", "SUPPORT").contains(actor.role()) : java.util.Set.of("MEMBER", "PUBLISHER").contains(actor.role()));
        if (!correctArea || !matches || !user.isEnabled() || !user.isAccountNonLocked() || !user.isAccountNonExpired()
                || !user.isCredentialsNonExpired()) {
            // Adopt the member/friend branch's temporary account lock policy.
            // Only for a real database member with a known user identity.
            if (!staff && correctArea && !matches && user instanceof Actor actor) {
                memberDb.update("UPDATE members SET failed_login_count=failed_login_count+1, "
                        + "locked_until=CASE WHEN failed_login_count+1>=3 "
                        + "THEN DATEADD(MINUTE,1,SYSDATETIME()) ELSE locked_until END "
                        + "WHERE member_id=?", actor.id());
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "帳號或密碼錯誤");
        }
        if (!staff) {
            memberDb.update("UPDATE members SET failed_login_count=0, locked_until=NULL WHERE member_id=?", ((Actor) user).id());
        }
        return tokens.issue((Actor) user);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication auth) {
        Jwt jwt = (Jwt) auth.getDetails();
        tokens.revoke(jwt);
        events.disconnect(jwt.getId());
    }
}
