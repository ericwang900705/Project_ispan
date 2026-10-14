package gameplatform.member.controller;

import java.util.Map;
import gameplatform.member.service.MemberAccountService;
import gameplatform.support.model.Actor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Existing HERO /api/auth/member/login + JWT retained; only add the member/friend endpoints. */
@RestController
public class MemberAccountController {
    private final MemberAccountService service;
    public MemberAccountController(MemberAccountService service) { this.service=service; }
    @PostMapping("/api/auth/register")
    public MemberAccountService.MailResult register(@RequestBody MemberAccountService.Register form) { return service.register(form); }
    @GetMapping("/api/auth/verification-status")
    public Map<String,Boolean> status(@RequestParam String ticket) { return service.verificationStatus(ticket); }
    @GetMapping("/api/auth/verify")
    public Map<String,String> verify(@RequestParam String token) { return Map.of("message",service.verify(token)); }
    @PostMapping("/api/auth/resend-verification")
    public MemberAccountService.MailResult resend(@RequestBody MemberAccountService.EmailRequest form) { return service.resend(form); }
    @PostMapping("/api/auth/forgot-password")
    public Map<String,String> forgot(@RequestBody MemberAccountService.EmailRequest form) { return Map.of("message",service.forgot(form)); }
    @PostMapping("/api/auth/reset-password")
    public Map<String,String> reset(@RequestBody MemberAccountService.Reset form) { return Map.of("message",service.reset(form)); }
    @GetMapping("/api/members/me")
    public Map<String,Object> me(@AuthenticationPrincipal Actor actor) { return service.profile(actor); }
    @PatchMapping("/api/members/profile")
    public Map<String,Object> update(@AuthenticationPrincipal Actor actor,@RequestBody MemberAccountService.ProfileUpdate form) { return service.update(actor,form); }
    @PostMapping("/api/members/change-password")
    public Map<String,String> change(@AuthenticationPrincipal Actor actor,@RequestBody MemberAccountService.ChangePassword form) { return Map.of("message",service.changePassword(actor,form)); }
}
