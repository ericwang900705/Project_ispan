package gameplatform.support.controller;

import gameplatform.member.util.JwtTokenProvider;
import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/** 獨立客服版的登入轉接。API、JWT 與回傳格式對齊 member/friend；整合時沿用組員 Controller。 */
@RestController
@RequestMapping("/api")
public class AuthController {
    private final UserDetailsService members;
    private final UserDetailsService staff;
    private final PasswordEncoder passwords;
    private final JwtTokenProvider tokens;
    private final JdbcTemplate db;
    private final String staffRole;
    @Value("${app.support.admin-role-code:ROLE_SUPER_ADMIN}")
    private String adminRole = "ROLE_SUPER_ADMIN";
    private final String dummyHash;

    public AuthController(@Qualifier("memberAccounts") UserDetailsService members,
            @Qualifier("staffAccounts") UserDetailsService staff, PasswordEncoder passwords,
            JwtTokenProvider tokens, JdbcTemplate db,
            @Value("${app.support.staff-role-code:ROLE_SUPPORT}") String staffRole) {
        this.members=members; this.staff=staff; this.passwords=passwords; this.tokens=tokens;
        this.db=db; this.staffRole=staffRole;
        dummyHash=passwords.encode(java.util.UUID.randomUUID().toString());
    }
    public record MemberLogin(@NotBlank @Size(max=100) String username, @NotBlank @Size(max=200) String password) {}
    public record StaffLogin(@NotBlank @Size(max=100) String adminAccount, @NotBlank @Size(max=200) String password) {}

    @PostMapping("/auth/login")
    public String memberLogin(@Valid @RequestBody MemberLogin input) {
        Actor actor=authenticate(input.username(),input.password(),false);
        return tokens.generateToken(actor.username(),actor.id(),"PUBLISHER".equals(actor.role()));
    }

    @PostMapping("/admin/auth/login")
    public Map<String,Object> staffLogin(@Valid @RequestBody StaffLogin input) {
        Actor actor=authenticate(input.adminAccount(),input.password(),true);
        String name=db.queryForObject("SELECT admin_name FROM dbo.admin WHERE admin_id=?",String.class,actor.id());
        return Map.of("token",tokens.generateAdminToken(actor.username(),actor.id(),actor.role()),
            "adminName",name==null?actor.username():name,"roleCode",actor.role());
    }

    private Actor authenticate(String account,String password,boolean staffArea) {
        UserDetails user=null;
        try { user=(staffArea?staff:members).loadUserByUsername(account); }
        catch(UsernameNotFoundException ignored) {}
        boolean matches=passwords.matches(password,user==null?dummyHash:user.getPassword());
        boolean correct=user instanceof Actor actor && (staffArea
            ? (adminRole.equals(actor.role()) || staffRole.equals(actor.role()))
            : ("MEMBER".equals(actor.role()) || "PUBLISHER".equals(actor.role())));
        if(!correct || !matches || !user.isEnabled() || !user.isAccountNonLocked()
                || !user.isAccountNonExpired() || !user.isCredentialsNonExpired()) {
            if(!staffArea && correct && !matches) {
                db.update("UPDATE members SET failed_login_count=failed_login_count+1, "
                    + "locked_until=CASE WHEN failed_login_count+1>=3 THEN DATEADD(MINUTE,1,SYSDATETIME()) "
                    + "ELSE locked_until END WHERE member_id=?",((Actor)user).id());
            }
            throw new Problem(401,"帳號或密碼錯誤");
        }
        Actor actor=(Actor)user;
        if(!staffArea) db.update("UPDATE members SET failed_login_count=0,locked_until=NULL WHERE member_id=?",actor.id());
        else db.update("UPDATE dbo.admin SET last_login_at=SYSDATETIME() WHERE admin_id=?",actor.id());
        return actor;
    }
}
