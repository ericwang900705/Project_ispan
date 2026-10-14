package gameplatform.support.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
                                        + "WHERE a.admin_account=? AND a.status='ACTIVE'",
                                        (rs, n) -> new Actor(rs.getInt("admin_id"),
                                                        rs.getString("role_code"), rs.getString("admin_account"),
                                                        rs.getString("password_hash")),
                                        name);
                        if (staff.isEmpty() || !java.util.Set.of("ADMIN", "REVIEWER", "SUPPORT")
                                        .contains(staff.get(0).role()))
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
                                s -> s.sessionCreationPolicy(
                                                org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                                .csrf(c -> c.disable()).formLogin(f -> f.disable()).httpBasic(h -> h.disable())
                                .logout(l -> l.disable())

                                .authorizeHttpRequests(a -> a

                                                // 1. 公開的會員註冊與登入 API
                                                .requestMatchers(
                                                                "/api/auth/login",
                                                                "/api/auth/member/login",
                                                                "/api/auth/staff/login",
                                                                "/api/auth/register",
                                                                "/api/auth/verify",
                                                                "/api/auth/verification-status",
                                                                "/api/auth/resend-verification",
                                                                "/api/auth/forgot-password",
                                                                "/api/auth/reset-password",
                                                                "/api/public/**")
                                                .permitAll()

                                                // 2. 綠界金流回呼
                                                .requestMatchers(
                                                                HttpMethod.POST,
                                                                "/api/payment/notify",
                                                                "/api/payment/info",
                                                                "/api/payment/result",
                                                                "/api/payment/info-redirect")
                                                .permitAll()

                                                // 3. 公開遊戲評論
                                                .requestMatchers(
                                                                HttpMethod.GET,
                                                                "/api/reviews/game/**")
                                                .permitAll()

                                                // 4. 記錄會員瀏覽遊戲（必須登入）
                                                .requestMatchers(
                                                                HttpMethod.POST,
                                                                "/api/games/*/views")
                                                .hasAnyRole("MEMBER", "PUBLISHER")
                                                // 記錄會員標籤搜尋行為
                                                .requestMatchers(
                                                                HttpMethod.POST,
                                                                "/api/tags/*/searches")
                                                .hasAnyRole("MEMBER", "PUBLISHER")

                                                // 5. 首頁個人 AI 推薦
                                                .requestMatchers(
                                                                "/api/recommendations/home")
                                                .hasAnyRole("MEMBER", "PUBLISHER")

                                                // 6. 公開遊戲與熱門推薦查詢
                                                .requestMatchers(
                                                                HttpMethod.GET,
                                                                "/api/games",
                                                                "/api/games/**",
                                                                "/api/recommendations/popular/top5")
                                                .permitAll()

                                                // 7. 會員商城功能
                                                .requestMatchers(
                                                                "/api/cart/**",
                                                                "/api/cart-items/**",
                                                                "/api/coupons/**",
                                                                "/api/member-games/**",
                                                                "/api/orders/**",
                                                                "/api/payment/**",
                                                                "/api/reviews/**")
                                                .hasAnyRole("MEMBER", "PUBLISHER")

                                                // 8. 其他會員功能
                                                .requestMatchers(
                                                                "/api/members/**",
                                                                "/api/member/**")
                                                .hasAnyRole("MEMBER", "PUBLISHER")

                                                // 9. 發行商功能
                                                .requestMatchers(
                                                                "/api/publisher/**")
                                                .hasRole("PUBLISHER")

                                                // 10. 管理員後台
                                                .requestMatchers(
                                                                "/api/review/**")
                                                .hasRole("REVIEWER")

                                                .requestMatchers(
                                                                "/api/management/**")
                                                .hasRole("ADMIN")

                                                .requestMatchers(
                                                                "/api/admin/**")
                                                .hasRole("SUPPORT")

                                                // 11. 其他需要登入的功能
                                                .requestMatchers(
                                                                "/api/auth/**",
                                                                "/api/notifications/**",
                                                                "/api/events")
                                                .authenticated()

                                                // 12. 其他 API
                                                .requestMatchers("/api/**")
                                                .hasAnyRole("MEMBER", "PUBLISHER", "ADMIN", "SUPPORT")

                                                // 13. 最後才是預設規則
                                                .anyRequest().permitAll())
                                .oauth2ResourceServer(
                                                o -> o.jwt(jwt -> jwt.decoder(tokens.decoder())
                                                                .jwtAuthenticationConverter(token -> {
                                                                        Actor actor = tokens.actor(token);
                                                                        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                                                                        actor, null,
                                                                                        actor.getAuthorities());
                                                                        auth.setDetails(token);
                                                                        return auth;
                                                                })))
                                .requestCache(c -> c.disable()).build();
        }

}
