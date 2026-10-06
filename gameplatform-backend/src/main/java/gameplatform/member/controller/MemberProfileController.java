package gameplatform.member.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import gameplatform.member.dto.MemberProfileResponse;
import gameplatform.member.service.MemberService;

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
}