package gameplatform.member.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import gameplatform.member.dto.ChangePasswordRequest;
import gameplatform.member.dto.MemberProfileResponse;
import gameplatform.member.dto.ResendVerificationRequest;
import gameplatform.member.dto.UpdateProfileRequest;
import gameplatform.member.service.MemberService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/members")
public class MemberProfileController {

    private final MemberService memberService;

    public MemberProfileController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/me")
    public ResponseEntity<MemberProfileResponse> getProfile() {
        // 1. 從 JWT 攔截器中取得目前登入的帳號
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUsername = authentication.getName();

        // 2. 呼叫 Service 取得詳細資料
        MemberProfileResponse profile = memberService.getMemberProfile(currentUsername);

        // 3. 回傳 JSON 格式的會員資料
        return ResponseEntity.ok(profile);
    }

    // 登入狀態下修改密碼
    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(@RequestBody ChangePasswordRequest request,
            Authentication authentication) {

        String currentUsername = authentication.getName();// 直接從 Token 中取得目前登入的帳號，安全又精簡

        // 呼叫 Service 執行修改
        String result = memberService.changePassword(currentUsername, request);

        return ResponseEntity.ok(result);
    }

    // 修改/完善個人資料 (使用 PATCH 進行部分更新)
    @PatchMapping("/profile")
    public ResponseEntity<MemberProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication) {

        String currentUsername = authentication.getName();// 直接從 Token 中取得目前登入的帳號，安全又精簡

        // 呼叫 Service 執行更新，並取得更新後的資料
        MemberProfileResponse updatedProfile = memberService.updateProfile(currentUsername, request);

        return ResponseEntity.ok(updatedProfile);
    }

}