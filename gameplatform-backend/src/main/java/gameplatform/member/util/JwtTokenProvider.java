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
                .setSubject(username)
                .claim("memberId", memberId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // 1.1 專屬管理員的 JWT 簽發方法
    public String generateAdminToken(String adminAccount, Integer adminId, String roleCode) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + EXPIRATION_TIME);

        return Jwts.builder()
                .setSubject(adminAccount)
                .claim("adminId", adminId)
                .claim("type", "ADMIN")
                .claim("role", roleCode)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256) // 沿用密鑰
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

    // 解析並取得 Token 內所有的 Payload (Claims)
    public Claims getClaimsFromToken(String token) {
        return Jwts.parserBuilder() // 1. 改用 parserBuilder()
                .setSigningKey(getSigningKey()) // 2. 沿用原本的密鑰設定
                .build() // 3. 呼叫 build() 正式建立解析器
                .parseClaimsJws(token)
                .getBody();
    }

}