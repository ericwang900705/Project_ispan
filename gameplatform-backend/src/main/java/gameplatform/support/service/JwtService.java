package gameplatform.support.service;

import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import gameplatform.support.model.Actor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

@Service
public class JwtService {

    // JWT 編碼器：負責產生並簽署 JWT Token
    private final JwtEncoder encoder;

    // JWT 解碼器：負責驗證 JWT 簽章及相關條件
    private final NimbusJwtDecoder decoder;

    // JWT 簽發者名稱，用於確認 Token 來源
    private final String issuer;

    // JWT 有效期限，單位為秒；0 代表不設定過期時間
    private final long seconds;

    // 儲存已撤銷的 Token
    // Key：JWT ID（jti）
    // Value：Token 過期時間
    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();

    /**
     * 初始化 JWT 服務
     *
     * 功能：
     * 1. 從 application.properties 讀取 JWT 設定
     * 2. 檢查密鑰長度與 Token 有效期限
     * 3. 建立 JWT 編碼器與解碼器
     * 4. 設定 JWT 驗證規則
     *
     * @param secret  JWT 簽章密鑰（Base64 格式）
     * @param issuer  JWT 簽發者
     * @param seconds JWT 有效期限（秒）
     */
    public JwtService(
            @Value("${app.jwt.secret:}") String secret,
            @Value("${app.jwt.issuer:gameplatform}") String issuer,
            @Value("${app.jwt.ttl-seconds:0}") long seconds) {

        byte[] bytes;

        // 將 Base64 格式的密鑰轉換成位元組陣列
        try {
            bytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "app.jwt.secret must be Base64", e);
        }

        // 確保密鑰至少有 32 bytes，符合 HS256 的安全需求
        if (bytes.length < 32)
            throw new IllegalStateException(
                    "app.jwt.secret must contain at least 32 random bytes (Base64 encoded)");

        // 限制 Token 有效期限為 0 或 60～3600 秒
        if (seconds != 0 && (seconds < 60 || seconds > 3600))
            throw new IllegalStateException(
                    "JWT lifetime must be 0 (no expiry) or 60..3600 seconds");

        this.issuer = issuer;
        this.seconds = seconds;

        // 建立 HS256 對稱式簽章密鑰
        var key = new SecretKeySpec(bytes, "HmacSHA256");

        // 建立 JWT 編碼器
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));

        // 建立 JWT 解碼器，指定使用 HS256 演算法
        decoder = NimbusJwtDecoder
                .withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        // 設定 JWT 驗證規則
        decoder.setJwtValidator(
                new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(

                        // 驗證 JWT 的標準時間欄位與簽發者
                        JwtValidators.createDefaultWithIssuer(issuer),

                        // 自訂 JWT 驗證條件
                        token -> {
                            try {

                                // 檢查 Token 中的使用者身分資訊
                                actor(token);

                                // 驗證 Token：
                                // 1. 是否仍有效且未被撤銷
                                // 2. Audience 是否包含 support-api
                                // 3. 是否具有簽發時間
                                // 4. Subject 是否符合 role:uid 格式
                                if (!active(token)
                                        || !token.getAudience().contains("support-api")
                                        || token.getIssuedAt() == null
                                        || !Objects.equals(
                                                token.getSubject(),
                                                token.getClaimAsString("role")
                                                        + ":"
                                                        + token.getClaimAsString("uid"))) {

                                    throw new IllegalArgumentException();
                                }

                                // 所有驗證條件通過
                                return OAuth2TokenValidatorResult.success();

                            } catch (RuntimeException e) {

                                // 驗證失敗，回傳無效 Token 錯誤
                                return OAuth2TokenValidatorResult.failure(
                                        new OAuth2Error(
                                                "invalid_token",
                                                "Token expired, revoked or invalid",
                                                null));
                            }
                        }));
    }

    /**
     * 取得 JWT 解碼器
     *
     * 功能：
     * 提供 Spring Security 使用 JWT 解碼器，
     * 驗證前端請求攜帶的 Bearer Token。
     *
     * @return JwtDecoder JWT 解碼器
     */
    public JwtDecoder decoder() {
        return decoder;
    }

    /**
     * 從 JWT Token 取得登入者身分
     *
     * 功能：
     * 1. 讀取 Token 中的角色、帳號及使用者 ID
     * 2. 檢查使用者 ID 是否有效
     * 3. 確認角色是否為系統允許的角色
     * 4. 將 JWT 資訊轉換成 Actor 物件
     *
     * @param jwt 已解碼的 JWT Token
     * @return Actor 登入者身分資訊
     */
    public Actor actor(Jwt jwt) {

        // 從 JWT Claims 取得角色與帳號
        String role = jwt.getClaimAsString("role");
        String name = jwt.getClaimAsString("username");

        // 從 JWT Claims 取得使用者 ID
        int id = Integer.parseInt(jwt.getClaimAsString("uid"));

        // 驗證使用者 ID、角色及帳號是否合法
        if (id <= 0
                || !Set.of(
                        "MEMBER",
                        "PUBLISHER",
                        "SUPERADMIN",
                        "ADMIN",
                        "REVIEWER",
                        "SUPPORT").contains(role)
                || name == null
                || name.isBlank()) {

            throw new IllegalArgumentException("Invalid identity");
        }

        // 建立登入者身分物件
        return new Actor(id, role, name, "");
    }

    /**
     * 產生 JWT Token
     *
     * 功能：
     * 1. 根據 Actor 建立 JWT Claims
     * 2. 設定使用者 ID、角色及帳號
     * 3. 設定簽發時間與過期時間
     * 4. 使用 HS256 演算法簽署 Token
     * 5. 回傳前端需要的 Token 資訊
     *
     * @param actor 登入成功的使用者資訊
     * @return Map 包含 accessToken、tokenType、expiresIn
     */
    public Map<String, Object> issue(Actor actor) {

        // 取得目前時間
        Instant now = Instant.now();

        // 建立 JWT Claims
        var claims = JwtClaimsSet.builder()

                // JWT 簽發者
                .issuer(issuer)

                // JWT 使用對象
                .audience(List.of("support-api"))

                // Token 主體，格式為「角色:使用者ID」
                .subject(actor.role() + ":" + actor.id())

                // Token 簽發時間
                .issuedAt(now)

                // 每個 Token 都有獨立的 JWT ID
                .id(UUID.randomUUID().toString())

                // 儲存使用者 ID
                .claim("uid", String.valueOf(actor.id()))

                // 儲存使用者角色
                .claim("role", actor.role())

                // 儲存使用者帳號
                .claim("username", actor.username());

        // 如果有效期限大於 0，設定 Token 過期時間
        if (seconds > 0) {
            claims.expiresAt(now.plusSeconds(seconds));
        }

        // 使用 HS256 演算法產生並簽署 JWT Token
        var token = encoder.encode(
                JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(),
                        claims.build()));

        // 回傳前端使用的 Token 資訊
        return Map.of(
                "accessToken", token.getTokenValue(),
                "tokenType", "Bearer",
                "expiresIn", seconds);
    }

    /**
     * 檢查 JWT Token 是否仍然有效
     *
     * 功能：
     * 1. 確認 JWT ID 存在
     * 2. 確認 Token 尚未過期
     * 3. 確認 Token 不在撤銷清單中
     *
     * 注意：
     * 此方法不負責驗證 JWT 簽章，
     * 簽章驗證由 JWT 解碼器處理。
     *
     * @param jwt JWT Token
     * @return true 代表有效，false 代表無效
     */
    public boolean active(Jwt jwt) {

        return jwt.getId() != null
                && !jwt.getId().isBlank()

                // 判斷 Token 是否過期
                && (jwt.getExpiresAt() == null
                        ? seconds == 0
                        : Instant.now().isBefore(jwt.getExpiresAt()))

                // 確認 Token 尚未被撤銷
                && !revoked.containsKey(jwt.getId());
    }

    /**
     * 撤銷 JWT Token
     *
     * 功能：
     * 將 Token 的 JWT ID 加入撤銷清單，
     * 讓該 Token 後續無法再通過驗證。
     *
     * 常見用途：
     * 使用者登出時，使目前 Token 失效。
     *
     * 注意：
     * 撤銷清單目前只儲存在記憶體，
     * Spring Boot 重新啟動後會清空。
     *
     * @param jwt 要撤銷的 JWT Token
     */
    public void revoke(Jwt jwt) {

        // 將 Token ID 與過期時間加入撤銷清單
        revoked.put(
                jwt.getId(),
                jwt.getExpiresAt() == null
                        ? Instant.MAX
                        : jwt.getExpiresAt());
    }

    /**
     * 定期清除已過期的 JWT 撤銷紀錄
     *
     * 功能：
     * 每 60 秒檢查一次撤銷清單，
     * 將已經過期的 Token 紀錄移除，
     * 避免記憶體持續累積無用資料。
     *
     * @Scheduled(fixedDelay = 60000)
     *                       代表前一次執行結束後，等待 60 秒再執行。
     *
     *                       注意：
     *                       需要啟用 Spring Scheduling，
     *                       例如在設定類別使用 @EnableScheduling。
     */
    @Scheduled(fixedDelay = 60000)
    public void cleanup() {

        // 取得目前時間
        Instant now = Instant.now();

        // 移除已經過期的撤銷紀錄
        revoked.entrySet().removeIf(
                e -> !now.isBefore(e.getValue()));
    }
}