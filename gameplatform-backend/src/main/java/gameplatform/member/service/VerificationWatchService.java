package gameplatform.member.service;

import gameplatform.support.model.Problem;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Short-lived, signed read-only ticket for tracking email verification.
 * Never returns the actual email verification token or password hash.
 */
@Service
public class VerificationWatchService {
    private static final long TTL_SECONDS = 3600;
    private final JdbcTemplate db;
    private final byte[] signingKey;
    private final SecureRandom random = new SecureRandom();

    public VerificationWatchService(JdbcTemplate db, @Value("${app.jwt.secret}") String secret) {
        this.db = db;
        this.signingKey = Base64.getDecoder().decode(secret);
        if (signingKey.length < 32)
            throw new IllegalStateException("JWT secret must contain >=32 bytes");
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(("verification-watch:v1:" + payload).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Cannot sign verification ticket", ex);
        }
    }

    public String issue(String email) {
        if (email == null || email.isBlank())
            throw new Problem(400, "信箱不可為空");
        byte[] nonce = new byte[24];
        random.nextBytes(nonce);
        String body = email.trim().toLowerCase(java.util.Locale.ROOT) + "\n" +
                (Instant.now().getEpochSecond() + TTL_SECONDS) + "\n" +
                Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(body.getBytes(StandardCharsets.UTF_8));
        return payload + "." + sign(payload);
    }

    public boolean verified(String ticket) {
        if (ticket == null || ticket.length() > 2048 || !ticket.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"))
            throw new Problem(400, "驗證追蹤票據無效");
        String[] parts = ticket.split("\\.", -1);
        if (!MessageDigest.isEqual(parts[1].getBytes(StandardCharsets.US_ASCII),
                sign(parts[0]).getBytes(StandardCharsets.US_ASCII)))
            throw new Problem(400, "驗證追蹤票據無效");
        try {
            String[] fields = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).split("\n",
                    -1);
            if (fields.length != 3 || fields[0].isBlank() || fields[0].length() > 254 || fields[2].isBlank())
                throw new IllegalArgumentException();
            long expires = Long.parseLong(fields[1]);
            if (expires <= Instant.now().getEpochSecond() || expires > Instant.now().getEpochSecond() + TTL_SECONDS)
                throw new Problem(410, "追蹤期限已過，請重新寄送驗證信");
            Integer count = db.queryForObject(
                    "SELECT COUNT(*) FROM dbo.member_verifications WHERE email=? AND is_verified=1",
                    Integer.class, fields[0]);
            return count != null && count > 0;
        } catch (Problem ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new Problem(400, "驗證追蹤票據無效");
        }
    }
}
