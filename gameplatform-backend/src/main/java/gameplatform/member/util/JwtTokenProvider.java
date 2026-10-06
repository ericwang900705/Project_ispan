package gameplatform.member.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtTokenProvider {

    // 密鑰 (實務上應該放在 application.properties 中，這裡先寫死作為示範)
    // 注意：長度必須大於 256 bits (32 個字元)
    private final String SECRET_KEY = "GamePlatformSecretKeyForJwtAuthenticationVerySecure";

    // Token 有效期 (例如：一天)
    private final long EXPIRATION_TIME = 86400000;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes());
    }

    // 1. 產生 JWT Token
    public String generateToken(String username, Integer memberId) {
        return Jwts.builder()
                .setSubject(username) // 主題通常放帳號
                .claim("memberId", memberId) // 可以把 memberId 塞進去給隊友用
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // 2. 驗證 Token 是否合法
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            System.out.println("Invalid JWT token: " + e.getMessage());
            return false;
        }
    }

    // 3. 從 Token 提取帳號
    public String getUsernameFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }
}