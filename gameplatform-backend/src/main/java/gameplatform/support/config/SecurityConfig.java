package gameplatform.support.config;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.SecurityFilterChain;

import gameplatform.support.model.Actor;
import gameplatform.support.service.JwtService;

/**
 * Spring Security 安全設定。
 *
 * 1. 支援 ADMIN、REVIEWER、SUPPORT、STAFF 管理員登入。
 * 2. 保留原本會員與發行商的登入及授權機制。
 * 3. 每次使用管理員 JWT 時，確認帳號仍為 ACTIVE 且角色未變更。
 * 4. STAFF 的功能權限由資料庫動態取得。
 * 5. 啟用方法層級授權，讓 Controller 的權限註解生效。
 *
 * 注意：
 * STAFF 目前尚未開放所有管理功能，
 * 後續需依照 permission_code 逐一設定 API 權限。
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

        /**
         * 會員帳號驗證服務。
         *
         * 保留原本的會員登入邏輯。
         */
        @Bean("memberAccounts")
        UserDetailsService memberAccounts(JdbcTemplate db) {
                return new gameplatform.support.service.FrontAccountService(db);
        }

        /**
         * 管理員帳號驗證服務。
         *
         * 原本允許：
         * ADMIN、REVIEWER、SUPPORT
         *
         * 新增：
         * STAFF
         *
         * 只有 ACTIVE 狀態的管理員可以登入。
         */
        @Bean("staffAccounts")
        UserDetailsService staffAccounts(JdbcTemplate db) {

                return name -> {

                        var staff = db.query(
                                        "SELECT a.admin_id, a.admin_account, " +
                                                        "a.password_hash, r.role_code " +
                                                        "FROM dbo.admin a " +
                                                        "JOIN dbo.admin_roles r " +
                                                        "ON r.admin_role_id = a.admin_role_id " +
                                                        "WHERE a.admin_account = ? " +
                                                        "AND a.status = 'ACTIVE'",

                                        (rs, n) -> new Actor(
                                                        rs.getInt("admin_id"),
                                                        rs.getString("role_code"),
                                                        rs.getString("admin_account"),
                                                        rs.getString("password_hash")),

                                        name);

                        // 確認帳號存在，而且屬於允許登入的管理員角色。
                        if (staff.isEmpty()
                                        || !Set.of(
                                                        "ADMIN",
                                                        "REVIEWER",
                                                        "SUPPORT",
                                                        "STAFF").contains(staff.get(0).role())) {

                                throw new UsernameNotFoundException(
                                                "Unknown staff account");
                        }

                        return staff.get(0);
                };
        }

        /**
         * 密碼加密器。
         *
         * 保留原本 BCrypt 加密方式。
         */
        @Bean
        BCryptPasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        /**
         * Spring Security 主要安全設定。
         *
         * @param http   Spring Security 設定物件
         * @param tokens JWT 服務
         * @param db     資料庫操作工具
         */
        @Bean
        SecurityFilterChain security(
                        HttpSecurity http,
                        JwtService tokens,
                        JdbcTemplate db) throws Exception {

                return http

                                // 使用 JWT，不使用伺服器 Session。
                                .sessionManagement(s -> s.sessionCreationPolicy(
                                                SessionCreationPolicy.STATELESS))

                                .csrf(c -> c.disable())
                                .formLogin(f -> f.disable())
                                .httpBasic(h -> h.disable())
                                .logout(l -> l.disable())

                                .authorizeHttpRequests(a -> a

                                                // =====================================
                                                // 1. 公開的會員註冊與登入 API
                                                // =====================================

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

                                                // =====================================
                                                // 2. 綠界金流回呼
                                                // =====================================

                                                .requestMatchers(
                                                                HttpMethod.POST,
                                                                "/api/payment/notify",
                                                                "/api/payment/info",
                                                                "/api/payment/result",
                                                                "/api/payment/info-redirect")
                                                .permitAll()

                                                // =====================================
                                                // 3. 公開遊戲評論
                                                // =====================================

                                                .requestMatchers(
                                                                HttpMethod.GET,
                                                                "/api/reviews/game/**")
                                                .permitAll()

                                                // =====================================
                                                // 4. 會員瀏覽紀錄與標籤搜尋
                                                // =====================================

                                                .requestMatchers(
                                                                HttpMethod.POST,
                                                                "/api/games/*/views")
                                                .hasAnyRole("MEMBER", "PUBLISHER")

                                                .requestMatchers(
                                                                HttpMethod.POST,
                                                                "/api/tags/*/searches")
                                                .hasAnyRole("MEMBER", "PUBLISHER")

                                                // =====================================
                                                // 5. 個人 AI 推薦
                                                // =====================================

                                                .requestMatchers(
                                                                "/api/recommendations/home")
                                                .hasAnyRole("MEMBER", "PUBLISHER")

                                                // =====================================
                                                // 6. 遊戲搜尋與公開遊戲資料
                                                // =====================================

                                                .requestMatchers(
                                                                HttpMethod.GET,
                                                                "/api/games")
                                                .authenticated()

                                                .requestMatchers(
                                                                HttpMethod.GET,
                                                                "/api/games/**",
                                                                "/api/recommendations/popular/top5")
                                                .permitAll()

                                                // =====================================
                                                // 7. 會員商城
                                                // =====================================

                                                .requestMatchers(
                                                                "/api/cart/**",
                                                                "/api/cart-items/**",
                                                                "/api/coupons/**",
                                                                "/api/member-games/**",
                                                                "/api/orders/**",
                                                                "/api/payment/**",
                                                                "/api/reviews/**")
                                                .hasAnyRole("MEMBER", "PUBLISHER")

                                                // =====================================
                                                // 8. 會員功能
                                                // =====================================

                                                .requestMatchers(
                                                                "/api/members/**",
                                                                "/api/member/**")
                                                .hasAnyRole("MEMBER", "PUBLISHER")

                                                // =====================================
                                                // 9. 發行商功能
                                                // =====================================

                                                .requestMatchers(
                                                                "/api/publisher/**")
                                                .hasRole("PUBLISHER")

                                                // ==========================================
                                                // 客服管理員專用 API
                                                // ==========================================
                                                // ADMIN：所有權限
                                                // SUPPORT：保留原本客服權限
                                                // STAFF：需要 TICKET_HANDLE
                                                .requestMatchers(
                                                                "/api/admin/workload",
                                                                "/api/admin/queue",
                                                                "/api/admin/rooms/*/claim",
                                                                "/api/admin/rooms/*/escalate",
                                                                "/api/admin/tickets/*/priority",
                                                                "/api/admin/tickets/*/claim",
                                                                "/api/admin/tickets/*/close")
                                                .hasAnyAuthority(
                                                                "ROLE_ADMIN",
                                                                "ROLE_SUPPORT",
                                                                "TICKET_HANDLE")

                                                // =====================================
                                                // 11. 會員客服聊天室
                                                // =====================================

                                                .requestMatchers(
                                                                "/api/member/rooms",
                                                                "/api/member/rooms/latest")
                                                .hasAnyRole("MEMBER", "PUBLISHER")

                                                // ==========================================
                                                // 客服聊天室與工單共用 API
                                                // ==========================================
                                                .requestMatchers(
                                                                "/api/rooms",
                                                                "/api/rooms/**",
                                                                "/api/tickets",
                                                                "/api/tickets/**")
                                                .hasAnyAuthority(
                                                                "ROLE_MEMBER",
                                                                "ROLE_PUBLISHER",
                                                                "ROLE_SUPPORT",
                                                                "ROLE_ADMIN",
                                                                "TICKET_HANDLE")

                                                // =====================================
                                                // 13. 客服分類
                                                // =====================================

                                                .requestMatchers(
                                                                HttpMethod.GET,
                                                                "/api/categories")
                                                .authenticated()

                                                // ==========================================
                                                // 14-1. 會員停權與恢復
                                                // ==========================================
                                                // ADMIN：超級管理員
                                                // REVIEWER：保留原本權限
                                                // STAFF：必須取得 MEMBER_SUSPEND 權限
                                                .requestMatchers(
                                                                "/api/admin/members/*/suspend",
                                                                "/api/admin/members/*/reactivate")
                                                .hasAnyAuthority(
                                                                "ROLE_ADMIN",
                                                                "ROLE_REVIEWER",
                                                                "MEMBER_SUSPEND")

                                                // ==========================================
                                                // 14-2. 發行商審核
                                                // ==========================================
                                                // ADMIN：超級管理員
                                                // REVIEWER：原本的審核員
                                                // STAFF：必須取得 PUBLISHER_REVIEW 權限
                                                .requestMatchers(
                                                                "/api/admin/publishers",
                                                                "/api/admin/publishers/**")
                                                .hasAnyAuthority(
                                                                "ROLE_ADMIN",
                                                                "ROLE_REVIEWER",
                                                                "PUBLISHER_REVIEW")

                                                // ==========================================
                                                // 15. 遊戲審核管理員 API
                                                // ==========================================
                                                // ADMIN：超級管理員
                                                // REVIEWER：原本的審核員
                                                // STAFF：必須取得 GAME_REVIEW 權限
                                                .requestMatchers(
                                                                "/api/admin/game-reviews",
                                                                "/api/admin/game-reviews/**")
                                                .hasAnyAuthority(
                                                                "ROLE_ADMIN",
                                                                "ROLE_REVIEWER",
                                                                "GAME_REVIEW")

                                                // ==========================================
                                                // 16. 系統管理功能
                                                // ==========================================
                                                .requestMatchers("/api/management/**")
                                                .hasRole("ADMIN")

                                                // ==========================================
                                                // 17. 其他管理員 API
                                                // ==========================================
                                                .requestMatchers("/api/admin/**")
                                                .hasRole("ADMIN")

                                                // =====================================
                                                // 18. 其他需要登入的功能
                                                // =====================================

                                                .requestMatchers(
                                                                "/api/auth/**",
                                                                "/api/notifications/**",
                                                                "/api/events")
                                                .authenticated()

                                                // =====================================
                                                // 19. 其他 API
                                                // =====================================
                                                // 保留原本角色清單。
                                                // STAFF 尚未全面開放操作其他 API。

                                                .requestMatchers("/api/**")
                                                .hasAnyRole(
                                                                "MEMBER",
                                                                "PUBLISHER",
                                                                "ADMIN",
                                                                "REVIEWER",
                                                                "SUPPORT")

                                                // =====================================
                                                // 20. 預設規則
                                                // =====================================

                                                .anyRequest().permitAll())

                                // =============================================
                                // JWT 驗證與動態權限載入
                                // =============================================

                                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt

                                                .decoder(tokens.decoder())

                                                .jwtAuthenticationConverter(token -> {

                                                        // ==========================================
                                                        // 1. 從 JWT 取得登入者身分
                                                        // ==========================================
                                                        Actor actor = tokens.actor(token);

                                                        // 保留原本 Actor 的角色權限
                                                        List<GrantedAuthority> authorities = new ArrayList<>(
                                                                        actor.getAuthorities());

                                                        // ==========================================
                                                        // 會員與發行商 JWT 停權檢查
                                                        // ==========================================
                                                        // 每次使用 JWT 呼叫 API 時，重新確認帳號未被停權。
                                                        // 避免會員在登入後遭停權，仍持續使用舊 Token。

                                                        if (actor.isMember()) {

                                                                var lockedUntilList = db.query(
                                                                                "SELECT locked_until, is_publisher " +
                                                                                                "FROM dbo.members " +
                                                                                                "WHERE member_id = ?",
                                                                                (rs, rowNum) -> {

                                                                                        var lockedUntil = rs
                                                                                                        .getTimestamp("locked_until");

                                                                                        boolean isPublisher = rs
                                                                                                        .getBoolean("is_publisher");

                                                                                        return new Object[] {
                                                                                                        lockedUntil == null
                                                                                                                        ? null
                                                                                                                        : lockedUntil.toLocalDateTime(),
                                                                                                        isPublisher
                                                                                        };
                                                                                },
                                                                                actor.id());

                                                                // 會員帳號不存在，拒絕舊 JWT
                                                                if (lockedUntilList.size() != 1) {
                                                                        throw new OAuth2AuthenticationException(
                                                                                        new OAuth2Error("invalid_token"),
                                                                                        "會員帳號不存在");
                                                                }

                                                                Object[] memberData = lockedUntilList.get(0);

                                                                LocalDateTime lockedUntil = (LocalDateTime) memberData[0];

                                                                boolean isPublisher = (Boolean) memberData[1];

                                                                // 會員仍在停權期間，拒絕舊 JWT
                                                                if (lockedUntil != null
                                                                                && lockedUntil.isAfter(
                                                                                                LocalDateTime.now())) {

                                                                        throw new OAuth2AuthenticationException(
                                                                                        new OAuth2Error("invalid_token"),
                                                                                        "會員帳號已被停權");
                                                                }

                                                                // 會員角色已變更，要求重新登入
                                                                String currentRole = isPublisher ? "PUBLISHER"
                                                                                : "MEMBER";

                                                                if (!currentRole.equals(actor.role())) {
                                                                        throw new OAuth2AuthenticationException(
                                                                                        new OAuth2Error("invalid_token"),
                                                                                        "會員角色已變更，請重新登入");
                                                                }
                                                        }

                                                        // ==========================================
                                                        // 2. 確認管理員帳號仍然有效
                                                        // ==========================================
                                                        // 會員與發行商不需要查詢 admin 資料表
                                                        if (!actor.isMember()) {

                                                                var roles = db.queryForList(
                                                                                "SELECT r.role_code " +
                                                                                                "FROM dbo.admin a " +
                                                                                                "JOIN dbo.admin_roles r "
                                                                                                +
                                                                                                "ON a.admin_role_id = r.admin_role_id "
                                                                                                +
                                                                                                "WHERE a.admin_id = ? "
                                                                                                +
                                                                                                "AND a.status = 'ACTIVE'",
                                                                                String.class,
                                                                                actor.id());

                                                                // 帳號不存在、停用或角色變更，拒絕使用舊 JWT
                                                                if (roles.size() != 1
                                                                                || !actor.role().equals(roles.get(0))) {

                                                                        throw new OAuth2AuthenticationException(
                                                                                        new OAuth2Error("invalid_token"),
                                                                                        "管理員帳號已停用或角色已變更");
                                                                }
                                                        }

                                                        // ==========================================
                                                        // 3. STAFF 動態權限
                                                        // ==========================================
                                                        // 從資料庫取得 Super Admin 分配的權限
                                                        if ("STAFF".equals(actor.role())) {

                                                                var codes = db.queryForList(
                                                                                "SELECT p.permission_code " +
                                                                                                "FROM dbo.admin_user_permissions up "
                                                                                                +
                                                                                                "JOIN dbo.admin_permissions p "
                                                                                                +
                                                                                                "ON p.permission_id = up.permission_id "
                                                                                                +
                                                                                                "WHERE up.admin_id = ?",
                                                                                String.class,
                                                                                actor.id());

                                                                // 將資料庫權限轉成 Spring Security 權限
                                                                for (String code : codes) {
                                                                        authorities.add(
                                                                                        new SimpleGrantedAuthority(
                                                                                                        code));
                                                                }
                                                        }

                                                        // ==========================================
                                                        // 4. 建立 Spring Security 登入身分
                                                        // ==========================================
                                                        var auth = new UsernamePasswordAuthenticationToken(
                                                                        actor,
                                                                        null,
                                                                        authorities);

                                                        auth.setDetails(token);

                                                        return auth;
                                                })))

                                .requestCache(c -> c.disable())
                                .build();
        }
}