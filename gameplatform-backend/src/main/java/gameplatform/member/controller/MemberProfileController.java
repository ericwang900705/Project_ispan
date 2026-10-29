package gameplatform.member.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gameplatform.member.dto.ChangePasswordRequest;
import gameplatform.member.dto.MemberProfileResponse;
import gameplatform.member.dto.PublisherApplicationRequest;
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

    // 取得目前登入狀態
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

    // Google 首次開啟帳號密碼登入，使用原本的 JWT 登入狀態進行驗證。
    @PostMapping("/setup-account-password")
    public ResponseEntity<MemberProfileResponse> setupAccountPassword(
            @Valid @RequestBody gameplatform.member.dto.SetupAccountPasswordRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(memberService.setupAccountPassword(
                authentication.getName(), request.getUsername(), request.getNewPassword()));
    }

    // 登入狀態下修改密碼
    @PostMapping("/change-password")
    public ResponseEntity<java.util.Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {

        String currentUsername = authentication.getName();// 直接從 Token 中取得目前登入的帳號，安全又精簡

        // 呼叫 Service 執行修改
        String result = memberService.changePassword(currentUsername, request);

        return ResponseEntity.ok(java.util.Map.of("message", result));
    }

    // 修改/完善個人資料 (使用 PATCH 進行部分更新)
    @PatchMapping("/profile")
    public ResponseEntity<MemberProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication) {

        String currentUsername = authentication.getName();

        // 呼叫 Service 執行更新，並取得更新後的資料
        MemberProfileResponse updatedProfile = memberService.updateProfile(currentUsername, request);

        return ResponseEntity.ok(updatedProfile);
    }

    // 上傳大頭貼檔案
    @PostMapping("/me/avatar")
    public ResponseEntity<MemberProfileResponse> uploadAvatar(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            Authentication authentication) {

        String currentUsername = authentication.getName();
        MemberProfileResponse updatedProfile = memberService.uploadAvatar(currentUsername, file);
        return ResponseEntity.ok(updatedProfile);
    }

    @GetMapping("/publisher-application")
    public Object publisherApplicationStatus(Authentication authentication) {
        return memberService.publisherApplicationStatus(authentication.getName());
    }

    // 申請成為發行商
    @PostMapping("/apply-publisher")
    public ResponseEntity<java.util.Map<String, String>> applyToBePublisher(
            @Valid @RequestBody PublisherApplicationRequest request,
            Authentication authentication) {

        String currentUsername = authentication.getName();// 直接從 Token 中取得目前登入的帳號，安全又精簡

        String result = memberService.applyToBePublisher(currentUsername, request);
        return ResponseEntity.ok(java.util.Map.of("message", result));
    }

}