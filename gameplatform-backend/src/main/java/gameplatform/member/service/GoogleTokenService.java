package gameplatform.member.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import gameplatform.member.dto.GoogleLoginRequest;
import gameplatform.support.model.Problem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class GoogleTokenService {
    public record Identity(String subject, String email, String name) {
    }

    private final String clientId;
    private final GoogleIdTokenVerifier verifier;

    @org.springframework.beans.factory.annotation.Autowired
    public GoogleTokenService(@Value("${google.client.id:}") String clientId) {
        this(clientId, new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(List.of(clientId.trim())).build());
    }

    GoogleTokenService(String clientId, GoogleIdTokenVerifier verifier) {
        this.clientId = clientId.trim();
        this.verifier = verifier;
    }

    public String clientId() {
        return clientId;
    }

    public boolean enabled() {
        return !clientId.isBlank();
    }

    public Identity verify(GoogleLoginRequest input) {
        if (!enabled())
            throw new Problem(503, "Google 登入暫未開放");
        if (input == null || input.getIdToken() == null || input.getIdToken().isBlank()
                || input.getIdToken().length() > 12000 || input.getNonce() == null || input.getNonce().length() < 16
                || input.getNonce().length() > 128)
            throw new Problem(400, "Google 登入資料不完整");
        try {
            var token = verifier.verify(input.getIdToken());
            if (token == null)
                throw new Problem(401, "Google 驗證失敗，請重新選擇帳號");
            return identity(token.getPayload(), input.getNonce());
        } catch (Problem e) {
            throw e;
        } catch (java.io.IOException e) {
            throw new Problem(503, "Google 驗證服務暫時無法連線，請稍後再試");
        } catch (Exception e) {
            throw new Problem(401, "Google 驗證失敗，請重新選擇帳號");
        }
    }

    static Identity identity(GoogleIdToken.Payload p, String nonce) {
        if (p.getSubject() == null || p.getSubject().isBlank() || p.getSubject().length() > 100 || p.getEmail() == null
                || p.getEmail().isBlank() || p.getEmail().length() > 100 || !Boolean.TRUE.equals(p.getEmailVerified())
                || !(p.get("nonce") instanceof String actual) || !MessageDigest
                        .isEqual(actual.getBytes(StandardCharsets.UTF_8), nonce.getBytes(StandardCharsets.UTF_8)))
            throw new Problem(401, "Google 帳號驗證資料不符");
        return new Identity(p.getSubject(), p.getEmail(), String.valueOf(p.getOrDefault("name", "Player")));
    }
}
