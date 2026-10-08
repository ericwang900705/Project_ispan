package gameplatform.config;

import gameplatform.member.util.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 從 Request Header 取得 Token
        String token = getJwtFromRequest(request);

        // 2. 驗證 Token 是否存在且合法
        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {

            // 取出 Token 中所有的 Payload (需確保 JwtTokenProvider 中已新增並存檔 getClaimsFromToken 方法)
            Claims claims = jwtTokenProvider.getClaimsFromToken(token);

            // 3. 從 Token 取得帳號(會員或admin)
            String account = claims.getSubject();

            // 取出在 generateAdminToken 塞入的 "type"
            String type = claims.get("type", String.class);

            // 4. 動態建立 Spring Security 權限清單
            List<GrantedAuthority> authorities = new ArrayList<>();

            if ("ADMIN".equals(type)) {
                // 是管理員：取出 "role" (例如 ROLE_SUPER_ADMIN) 並賦予
                String role = claims.get("role", String.class);
                authorities.add(new SimpleGrantedAuthority(role));
            } else {
                // 一般會員：預設賦予 ROLE_MEMBER
                authorities.add(new SimpleGrantedAuthority("ROLE_MEMBER"));
            }

            // 5. 建立 Spring Security 認識的 Authentication 物件 (帶入動態生成的 authorities)
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(account, null,
                    authorities);

            // 6. 將驗證資訊放進 SecurityContext 中，Spring Security 就會知道這個請求是「已登入」狀態
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (org.springframework.util.StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // 切掉 "Bearer " 這 7 個字元，只保留 Token 本體
        }
        return null;
    }
}