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
                                                                "/api/admin/auth/login",
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

                                                // 6. 風哥客服遊戲搜尋：必須登入
                                                .requestMatchers(HttpMethod.GET, "/api/games")
                                                .authenticated()

                                                // 赫洛原本公開的遊戲、標籤、媒體與熱門推薦
                                                .requestMatchers(
                                                                HttpMethod.GET,
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
                                                // ==========================================
                                                // 風哥客服系統權限
                                                // ==========================================

                                                // 客服管理功能：客服人員、超級管理員
                                                .requestMatchers(
                                                                "/api/admin/workload",
                                                                "/api/admin/queue",
                                                                "/api/admin/rooms/*/claim",
                                                                "/api/admin/rooms/*/escalate",
                                                                "/api/admin/tickets/*/priority",
                                                                "/api/admin/tickets/*/claim",
                                                                "/api/admin/tickets/*/close")
                                                .hasAnyRole("SUPPORT", "ADMIN")

                                                // 會員客服聊天室：會員、發行商
                                                .requestMatchers(
                                                                "/api/member/rooms",
                                                                "/api/member/rooms/latest")
                                                .hasAnyRole("MEMBER", "PUBLISHER")

                                                // 聊天室與工單共用功能
                                                .requestMatchers(
                                                                "/api/rooms",
                                                                "/api/rooms/**",
                                                                "/api/tickets",
                                                                "/api/tickets/**")
                                                .hasAnyRole("MEMBER", "PUBLISHER", "SUPPORT", "ADMIN")

                                                // 客服分類
                                                .requestMatchers(HttpMethod.GET, "/api/categories")
                                                .authenticated()

                                                // 10. 管理員後台

                                                // 佟哥的會員審核與發行商審核
                                                // ADMIN、REVIEWER 可以使用
                                                .requestMatchers(
                                                                "/api/admin/members",
                                                                "/api/admin/members/**",
                                                                "/api/admin/publishers",
                                                                "/api/admin/publishers/**")
                                                .hasAnyRole("ADMIN", "REVIEWER")

                                                // 其他遊戲審核功能
                                                .requestMatchers("/api/review/**")
                                                .hasAnyRole("ADMIN", "REVIEWER")

                                                // 系統管理功能
                                                .requestMatchers("/api/management/**")
                                                .hasRole("ADMIN")

                                                // 其他管理員 API 暫時只開放給 ADMIN
                                                .requestMatchers("/api/admin/**")
                                                .hasRole("ADMIN")

                                                // 11. 其他需要登入的功能
                                                .requestMatchers(
                                                                "/api/auth/**",
                                                                "/api/notifications/**",
                                                                "/api/events")
                                                .authenticated()

                                                // 12. 其他 API
                                                .requestMatchers("/api/**")
                                                .hasAnyRole("MEMBER", "PUBLISHER", "ADMIN", "REVIEWER", "SUPPORT")

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
