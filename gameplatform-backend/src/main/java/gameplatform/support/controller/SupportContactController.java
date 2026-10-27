package gameplatform.support.controller;

import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 公開帳號求助資訊；僅提供客服收件地址，不回傳任何寄信帳密。 */
@RestController
public class SupportContactController {
    private final String email;

    public SupportContactController(
            @Value("${app.support.contact-email:${spring.mail.username:}}") String configuredEmail) {
        String value = Objects.toString(configuredEmail, "").strip();
        email = value.length() <= 254 && value.matches("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,63}")
                ? value : "";
    }

    @GetMapping("/api/public/support-contact")
    public Map<String, Object> contact() {
        return Map.of("email", email, "available", !email.isEmpty());
    }
}
