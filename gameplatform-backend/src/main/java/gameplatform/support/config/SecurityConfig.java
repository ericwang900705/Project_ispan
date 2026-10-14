package gameplatform.support.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import gameplatform.support.model.Actor;
import gameplatform.support.service.JwtService;

@Configuration
public class SecurityConfig {
    @Bean("memberAccounts")
    UserDetailsService memberAccounts(org.springframework.jdbc.core.JdbcTemplate db) {
        return new gameplatform.support.service.FrontAccountService(db);
    }

    @Bean("staffAccounts")
    UserDetailsService staffAccounts(org.springframework.jdbc.core.JdbcTemplate db) {
        return name -> {
            var staff = db.query("SELECT a.admin_id,a.admin_account,a.password_hash,r.role_code "
                    + "FROM admin a JOIN admin_roles r ON r.admin_role_id=a.admin_role_id "
                    + "WHERE a.admin_account=? AND a.status='ACTIVE'", (rs, n) -> new Actor(rs.getInt("admin_id"),
                        rs.getString("role_code"), rs.getString("admin_account"), rs.getString("password_hash")), name);
            if (staff.isEmpty() || !java.util.Set.of("ADMIN", "REVIEWER", "SUPPORT").contains(staff.get(0).role()))
                throw new UsernameNotFoundException("Unknown staff account");
            return staff.get(0);
        };
    }

    @Bean
    BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, JwtService tokens) throws Exception {
        return http.sessionManagement(
                s -> s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .csrf(c -> c.disable()).formLogin(f -> f.disable()).httpBasic(h -> h.disable()).logout(l -> l.disable())
                .authorizeHttpRequests(a -> a.requestMatchers("/api/auth/login", "/api/auth/member/login", "/api/auth/staff/login", "/api/auth/register", "/api/auth/verify", "/api/auth/verification-status", "/api/auth/resend-verification", "/api/auth/forgot-password", "/api/auth/reset-password", "/api/public/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/payment/notify", "/api/payment/info", "/api/payment/result", "/api/payment/info-redirect").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/reviews/game/**").permitAll()
                        .requestMatchers("/api/cart/**", "/api/cart-items/**", "/api/coupons/**", "/api/member-games/**", "/api/orders/**", "/api/payment/**", "/api/reviews/**").hasAnyRole("MEMBER", "PUBLISHER")
                        .requestMatchers("/api/auth/**", "/api/notifications/**", "/api/events").authenticated()
                        .requestMatchers("/api/review/**").hasRole("REVIEWER")
                        .requestMatchers("/api/publisher/**").hasRole("PUBLISHER")
                        .requestMatchers("/api/management/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("SUPPORT")
                        .requestMatchers("/api/members/**", "/api/member/**").hasAnyRole("MEMBER", "PUBLISHER").requestMatchers("/api/**").hasAnyRole("MEMBER", "PUBLISHER", "ADMIN", "SUPPORT")
                        .anyRequest().permitAll())
                .oauth2ResourceServer(
                        o -> o.jwt(jwt -> jwt.decoder(tokens.decoder()).jwtAuthenticationConverter(token -> {
                            Actor actor = tokens.actor(token);
                            var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                    actor, null, actor.getAuthorities());
                            auth.setDetails(token);
                            return auth;
                        })))
                .requestCache(c -> c.disable()).build();
    }
}
