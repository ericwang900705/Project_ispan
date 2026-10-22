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
                        if (staff.isEmpty() || !java.util.Set.of("ADMIN", "STAFF", "REVIEWER", "SUPPORT")
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
        SecurityFilterChain security(HttpSecurity http, JwtService tokens, StaffPermissionService permissions,
                        SupportActorResolver actors) throws Exception {
                return http.sessionManagement(
                                s -> s.sessionCreationPolicy(
                                                org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                                .csrf(c -> c.disable()).formLogin(f -> f.disable()).httpBasic(h -> h.disable())
                                .logout(l -> l.disable())

                                .authorizeHttpRequests(a -> a

                                                // 1. 公開的會員註冊與登入 API
                                                .requestMatchers(
                                                                "/api/auth/login",
                                                                "/api/auth/google-login",
                                                                "/api/auth/member/login",
                                                                "/api/auth/staff/login",
                                                                "/api/admin/auth/login",
                                                                "/api/auth/register",
                                                                "/api/auth/staff/register",
                                                                "/api/auth/verify",
                                                                "/api/auth/verification-status",
                                                                "/api/auth/resend-verification",
                                                                "/api/auth/forgot-password",
                                                                "/api/auth/reset-password",
                                                                "/api/public/**",
                                                                "/api/payment/config")
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
                                                .requestMatchers(HttpMethod.GET, "/api/support/games")
                                                .authenticated()

                                                // 商城遊戲清單：不與 /api/support/games 混用
                                                .requestMatchers(HttpMethod.GET, "/api/games")
                                                .permitAll()
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

                                                // 整合前端新模組：明確限制角色，避免落入最後的寬鬆 /api/** 預設規則。
                                                .requestMatchers("/api/workspace/publisher/**")
                                                .hasRole("PUBLISHER")
                                                .requestMatchers("/api/friends/**", "/api/chat/**")
                                                .hasAnyRole("MEMBER", "PUBLISHER")

                                                // 舊版發行商遊戲操作會繞過審核／使用用戶傳入的 memberId，全部封閉。
                                                // 正式操作只走 /api/workspace/publisher/**。
                                                .requestMatchers("/api/publisher/games", "/api/publisher/games/**")
                                                .denyAll()

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
                                                .access(permissions.require("TICKET_HANDLE"))

                                                // 會員客服聊天室：會員、發行商
                                                .requestMatchers(
                                                                "/api/member/rooms",
                                                                "/api/member/rooms/latest")
                                                .hasAnyRole("MEMBER", "PUBLISHER")

                                                // 聊天室與工單共用功能
                                                .requestMatchers(
                                                                "/api/rooms", "/api/rooms/**",
                                                                "/api/tickets", "/api/tickets/**")
                                                .access(permissions.memberOrSupport())

                                                // 客服分類
                                                .requestMatchers(HttpMethod.GET, "/api/categories")
                                                .authenticated()

                                                // 10. 管理員後台

                                                // 佟哥的會員審核與發行商審核
                                                // STAFF、REVIEWER 可以使用
                                                .requestMatchers("/api/admin/members", "/api/admin/members/**")
                                                .access(permissions.require("MEMBER_SUSPEND"))
                                                .requestMatchers("/api/admin/publishers", "/api/admin/publishers/**")
                                                .access(permissions.require("PUBLISHER_REVIEW"))

                                                // 其他遊戲審核功能
                                                .requestMatchers("/api/review/**")
                                                .access(permissions.require("GAME_REVIEW"))

                                                // 平台概況及人員管理依個別授權；角色指定僅限最高管理員。
                                                .requestMatchers("/api/management/dashboard")
                                                .access(permissions.require("DASHBOARD_VIEW"))
                                                .requestMatchers("/api/management/**")
                                                .access(permissions.highestAdmin())

                                                // 審核人員可查遊戲及處理遊戲審核，不可管理人員或變更系統設定
                                                .requestMatchers("/api/admin/games", "/api/admin/games/**",
                                                                "/api/admin/game-reviews", "/api/admin/game-reviews/**")
                                                .access(permissions.require("GAME_REVIEW"))

                                                // 未指定功能權限的管理 API 僅開放最高管理員
                                                .requestMatchers("/api/admin/**")
                                                .access(permissions.highestAdmin())

                                                // 11. 其他需要登入的功能
                                                .requestMatchers(
                                                                "/api/auth/**",
                                                                "/api/notifications/**",
                                                                "/api/events")
                                                .authenticated()

                                                .requestMatchers("/api/backoffice/orders", "/api/backoffice/orders/**")
                                                .access(permissions.require("ORDER_VIEW"))
                                                .requestMatchers("/api/backoffice/refunds",
                                                                "/api/backoffice/refunds/**")
                                                .access(permissions.require("REFUND_MANAGE"))
                                                .requestMatchers("/api/backoffice/reviews",
                                                                "/api/backoffice/reviews/**",
                                                                "/api/backoffice/review-reports",
                                                                "/api/backoffice/review-reports/**")
                                                .access(permissions.require("COMMENT_REVIEW"))
                                                .requestMatchers("/api/backoffice/tags", "/api/backoffice/tags/**")
                                                .access(permissions.require("TAG_MANAGE"))
                                                .requestMatchers("/api/backoffice/coupons",
                                                                "/api/backoffice/coupons/**", "/api/backoffice/coupon-options")
                                                .access(permissions.require("COUPON_MANAGE"))
                                                .requestMatchers("/api/backoffice/login-logs")
                                                .access(permissions.require("LOGIN_LOG_VIEW"))
                                                .requestMatchers("/api/backoffice/operations")
                                                .access(permissions.require("OPERATION_LOG_VIEW"))
                                                .requestMatchers("/api/backoffice/**")
                                                .access(permissions.highestAdmin())

                                                // 12. 其他 API
                                                .requestMatchers("/api/**")
                                                .hasAnyRole("MEMBER", "PUBLISHER", "ADMIN", "STAFF", "REVIEWER",
                                                                "SUPPORT")

                                                // 13. 最後才是預設規則
                                                .anyRequest().permitAll())
                                .oauth2ResourceServer(
                                                o -> o.jwt(jwt -> jwt.decoder(tokens.decoder())
                                                                .jwtAuthenticationConverter(token -> {
                                                                        Actor actor;
                                                                        try {
                                                                                actor = actors.verifyActor(
                                                                                                tokens.actor(token));
                                                                        } catch (gameplatform.support.model.Problem e) {
                                                                                throw new org.springframework.security.authentication.BadCredentialsException(
                                                                                                "登入已失效，請重新登入", e);
                                                                        }
                                                                        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                                                                        actor, null,
                                                                                        actor.getAuthorities());
                                                                        auth.setDetails(token);
                                                                        return auth;
                                                                })))
                                .requestCache(c -> c.disable()).build();
        }

}
