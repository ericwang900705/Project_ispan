package gameplatform.member.controller;

import gameplatform.member.dto.LoginRequest;
import gameplatform.member.dto.RegisterRequest;
import gameplatform.member.service.MemberService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import gameplatform.member.dto.ForgotPasswordRequest;
import gameplatform.member.dto.ResetPasswordRequest;

@RestController
@RequestMapping("/api/auth")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest request) {
        String result = memberService.registerTest(request);
        return ResponseEntity.ok(result);
    }

    // 登入 API
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody LoginRequest request) {
        String token = memberService.login(request);
        return ResponseEntity.ok(token); // 登入成功，回傳 JWT Token
    }

    // 接收驗證請求的GET 端點：
    @GetMapping("/verify-email")
    public ResponseEntity<String> verifyEmail(@RequestParam String token) {
        String result = memberService.verifyEmail(token);
        return ResponseEntity.ok(result);
    }

    // --- 申請忘記密碼 API ---
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        String result = memberService.forgotPassword(request);
        return ResponseEntity.ok(result);
    }

    // --- 執行重設密碼 API ---
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequest request) {
        String result = memberService.resetPassword(request);
        return ResponseEntity.ok(result);
    }

}