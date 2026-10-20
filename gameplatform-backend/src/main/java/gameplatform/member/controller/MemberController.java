package gameplatform.member.controller;

import gameplatform.member.dto.LoginRequest;

import gameplatform.member.dto.RegisterRequest;
import gameplatform.member.dto.ResendVerificationRequest;
import gameplatform.member.service.MemberService;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import gameplatform.member.dto.ForgotPasswordRequest;
import gameplatform.member.dto.ResetPasswordRequest;

import gameplatform.member.dto.GoogleLoginRequest;

@RestController
@RequestMapping("/api/auth")
public class MemberController {

    private final MemberService memberService;
    private final gameplatform.member.service.VerificationWatchService verificationWatch;

    public MemberController(MemberService memberService, gameplatform.member.service.VerificationWatchService verificationWatch) {
        this.memberService = memberService;
        this.verificationWatch = verificationWatch;
    }

    // 註冊API
    @PostMapping("/register")
    public ResponseEntity<java.util.Map<String,String>> register(@Valid @RequestBody RegisterRequest request) {
        memberService.register(request);

        // 執行到這裡代表 service 沒有拋出任何例外，直接回傳成功訊息
        return ResponseEntity.ok(java.util.Map.of("message", "註冊成功！驗證信已寄至您的信箱，請前往點擊連結完成驗證。", "watchTicket", verificationWatch.issue(request.getEmail())));
    }

    // 登入 API
    @PostMapping("/login")
    public ResponseEntity<String> login(@Valid @RequestBody LoginRequest request) {
        String token = memberService.login(request);
        return ResponseEntity.ok(token); // 登入成功，回傳 JWT Token
    }

    // 信箱啟用驗證：
    @GetMapping("/verify")
    public ResponseEntity<java.util.Map<String,String>> verifyEmail(@RequestParam String token) {
        String result = memberService.verifyEmail(token);
        return ResponseEntity.ok(java.util.Map.of("message", result));
    }

    // 申請忘記密碼 API
    @PostMapping("/forgot-password")
    public ResponseEntity<java.util.Map<String,String>> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        String result = memberService.forgotPassword(request);
        return ResponseEntity.ok(java.util.Map.of("message", result));
    }

    // 執行重設密碼 API
    @PostMapping("/reset-password")
    public ResponseEntity<java.util.Map<String,String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        String result = memberService.resetPassword(request);
        return ResponseEntity.ok(java.util.Map.of("message", result));
    }

    // Google登入API
    @PostMapping("/google-login")
    public ResponseEntity<String> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        String token = memberService.googleLogin(request);
        return ResponseEntity.ok(token);
    }

    // 重發驗證信
    @PostMapping("/resend-verification")
    public ResponseEntity<java.util.Map<String,String>> resendVerification(@RequestBody ResendVerificationRequest request) {
        String result = memberService.resendVerificationEmail(request);
        return ResponseEntity.ok(java.util.Map.of("message", result, "watchTicket", verificationWatch.issue(request.getEmail())));
    }

    // 只回傳此追蹤票據對應的信箱驗證狀態，不洩漏正式驗證 Token。
    @GetMapping("/verification-status")
    public ResponseEntity<java.util.Map<String,Boolean>> verificationStatus(@RequestParam String ticket) {
        return ResponseEntity.ok(java.util.Map.of("verified", verificationWatch.verified(ticket)));
    }

    // // 測試
    // @GetMapping("/publisher-only")
    // @PreAuthorize("hasRole('PUBLISHER')") // Spring 會自動略去 ROLE_ 前綴來比對
    // public ResponseEntity<String> testPublisherApi() {
    // return ResponseEntity.ok("歡迎進入發行商專屬後台！");
    // }
}