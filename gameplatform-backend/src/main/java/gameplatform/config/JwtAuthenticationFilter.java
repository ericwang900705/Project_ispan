package gameplatform.config; // 依照你的資料夾結構調整

import gameplatform.member.util.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

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
            // 3. 從 Token 取得帳號
            String username = jwtTokenProvider.getUsernameFromToken(token);

            // 4. 建立 Spring Security 認識的 Authentication 物件 (目前先給空權限)
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    username, null, Collections.emptyList());

            // 5. 將驗證資訊放進 SecurityContext 中，Spring Security 就會知道這個請求是「已登入」狀態
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 6. 繼續執行下一個 Filter 或抵達 Controller
        filterChain.doFilter(request, response);
    }

    // 輔助方法：從 Authorization 標頭中萃取 Bearer Token
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // 切掉 "Bearer " 這 7 個字元，只保留 Token 本體
        }
        return null;
    }
}