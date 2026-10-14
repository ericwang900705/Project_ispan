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
        if (id <= 0 || !Set.of("MEMBER", "PUBLISHER", "ADMIN", "REVIEWER", "SUPPORT").contains(role) || name == null
                || name.isBlank())
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
        if (seconds > 0)
            claims.expiresAt(now.plusSeconds(seconds));
        var token = encoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()));
        return Map.of("accessToken", token.getTokenValue(), "tokenType", "Bearer", "expiresIn", seconds);
    }

    public boolean active(Jwt jwt) {
        return jwt.getId() != null && !jwt.getId().isBlank()
                && (jwt.getExpiresAt() == null ? seconds == 0 : Instant.now().isBefore(jwt.getExpiresAt()))
                && !revoked.containsKey(jwt.getId());
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
