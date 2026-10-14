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
    private org.springframework.jdbc.core.JdbcTemplate staffDb;
    @org.springframework.beans.factory.annotation.Autowired
    public JwtService(@Value("${app.jwt.secret:}") String secret,@Value("${app.jwt.issuer:gameplatform}") String issuer,@Value("${app.jwt.ttl-seconds:0}") long seconds,org.springframework.jdbc.core.JdbcTemplate db) {
        this(secret,issuer,seconds);this.staffDb=db;
    }
    private final JwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final String issuer;
    private final long seconds;
    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();

    public JwtService(@Value("${app.jwt.secret:}") String secret,
            @Value("${app.jwt.issuer:gameplatform}") String issuer,
            @Value("${app.jwt.ttl-seconds:0}") long seconds) {
        byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(secret);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("app.jwt.secret must be Base64", e);
            }
            if (bytes.length < 32)
                throw new IllegalStateException("app.jwt.secret must contain at least 32 random bytes (Base64 encoded)");
        if (seconds != 0 && (seconds < 60 || seconds > 3600))
            throw new IllegalStateException("JWT lifetime must be 0 (no expiry) or 60..3600 seconds");
        this.issuer = issuer;
        this.seconds = seconds;
        var key = new SecretKeySpec(bytes, "HmacSHA256");
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                token -> {
                    try {
                        actor(token);
                        if (!active(token) || !token.getAudience().contains("support-api")
                                || token.getIssuedAt() == null || !Objects.equals(token.getSubject(),
                                        token.getClaimAsString("role") + ":" + token.getClaimAsString("uid")))
                            throw new IllegalArgumentException();
                        return OAuth2TokenValidatorResult.success();
                    } catch (RuntimeException e) {
                        return OAuth2TokenValidatorResult
                                .failure(new OAuth2Error("invalid_token", "Token expired, revoked or invalid",
                                        null));
                    }
                }));
    }

    public JwtDecoder decoder() {
        return decoder;
    }

    public Actor actor(Jwt jwt) {
        String role = jwt.getClaimAsString("role"), name = jwt.getClaimAsString("username");
        int id = Integer.parseInt(jwt.getClaimAsString("uid"));
        if (id <= 0 || !Set.of("MEMBER", "PUBLISHER", "ADMIN", "REVIEWER", "SUPPORT").contains(role) || name == null || name.isBlank())
            throw new IllegalArgumentException("Invalid identity");
        return new Actor(id, role, name, "");
    }

    public Map<String, Object> issue(Actor actor) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of("support-api"))
                .subject(actor.role() + ":" + actor.id()).issuedAt(now)
                .id(UUID.randomUUID().toString()).claim("uid",
                        String.valueOf(actor.id()))
                .claim("role", actor.role()).claim("username", actor.username());
        if (!actor.isMember() && staffDb!=null) {
            String version=staffDb.queryForObject("SELECT COALESCE(CONVERT(VARCHAR(33),updated_at,126),'initial') FROM admin WHERE admin_id=?",String.class,actor.id());
            claims.claim("staffVersion",version);
        }
        if (seconds > 0) claims.expiresAt(now.plusSeconds(seconds));
        var token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()));
        return Map.of("accessToken", token.getTokenValue(), "tokenType", "Bearer", "expiresIn", seconds);
    }

    public boolean active(Jwt jwt) {
        return jwt.getId() != null && !jwt.getId().isBlank() && (jwt.getExpiresAt() == null ? seconds == 0 : Instant.now().isBefore(jwt.getExpiresAt())) && !revoked.containsKey(jwt.getId()) && staffCurrent(jwt);
    }

    private boolean staffCurrent(Jwt jwt) {
        if(staffDb==null)return true;
        try {
            var identity=actor(jwt);
            return identity.isMember() || staffDb.queryForObject("SELECT COUNT(*) FROM admin a JOIN admin_roles r ON r.admin_role_id=a.admin_role_id WHERE a.admin_id=? AND a.status='ACTIVE' AND r.role_code=? AND COALESCE(CONVERT(VARCHAR(33),a.updated_at,126),'initial')=?",Integer.class,identity.id(),identity.role(),jwt.getClaimAsString("staffVersion"))==1;
        } catch(RuntimeException e){return false;}
    }

    public void revoke(Jwt jwt) {
        revoked.put(jwt.getId(), jwt.getExpiresAt() == null ? Instant.MAX : jwt.getExpiresAt());
    }

    @Scheduled(fixedDelay = 60000)
    public void cleanup() {
        Instant now = Instant.now();
        revoked.entrySet().removeIf(e -> !now.isBefore(e.getValue()));
    }
}
