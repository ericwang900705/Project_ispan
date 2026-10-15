package gameplatform.support.config;

import gameplatform.support.model.Actor;
import gameplatform.support.service.FrontAccountService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/** 僅供這份客服專案獨立測試登入；合併小組既有登入時移除此類別。 */
@Configuration
public class SupportAccountConfig {
    @Bean("memberAccounts")
    UserDetailsService memberAccounts(JdbcTemplate db) { return new FrontAccountService(db); }

    @Bean("staffAccounts")
    UserDetailsService staffAccounts(JdbcTemplate db,
            @Value("${app.support.staff-role-code:ROLE_SUPPORT}") String staffRole,
            @Value("${app.support.admin-role-code:ROLE_SUPER_ADMIN}") String adminRole) {
        return name -> {
            var users = db.query("SELECT a.admin_id,a.admin_account,a.password_hash,r.role_code "
                + "FROM dbo.admin a JOIN dbo.admin_roles r ON r.admin_role_id=a.admin_role_id "
                + "WHERE a.admin_account=? AND a.status='ACTIVE' AND r.role_code IN (?, ?)",
                (rs,n) -> new Actor(rs.getInt("admin_id"),rs.getString("role_code"),
                    rs.getString("admin_account"),rs.getString("password_hash")),name,staffRole,adminRole);
            if (users.size()!=1) throw new UsernameNotFoundException("Unknown staff account");
            return users.get(0);
        };
    }
}
