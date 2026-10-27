package gameplatform.member.controller;

import gameplatform.member.dto.GoogleLoginRequest;
import gameplatform.member.service.*;
import gameplatform.support.model.Actor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class GoogleAccountController {
    private final GoogleAccountService accounts;
    private final GoogleTokenService google;

    @GetMapping("/api/public/google-login")
    public Object config() {
        return Map.of("enabled", google.enabled(), "clientId", google.clientId());
    }

    @GetMapping("/api/members/google-link")
    public Object status(Actor actor) {
        return accounts.status(actor);
    }

    @PostMapping("/api/members/google-link")
    public Object link(Actor actor, @Valid @RequestBody GoogleLoginRequest input) {
        return accounts.link(actor, input);
    }
}
