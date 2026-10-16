package gameplatform.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gameplatform.admin.service.AdminMemberService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_MEMBER_ADMIN')") // 依你的權限設計調整
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    // 停權 API
    @PatchMapping("/{memberId}/suspend")
    public ResponseEntity<String> suspendMember(
            @PathVariable Integer memberId,
            @RequestParam(defaultValue = "3") int hours) {

        String result = adminMemberService.suspendMember(memberId, hours);
        return ResponseEntity.ok(result);
    }

    // 復權 API
    @PatchMapping("/{memberId}/reactivate")
    public ResponseEntity<String> reactivateMember(
            @PathVariable Integer memberId) {

        String result = adminMemberService.reactivateMember(memberId);
        return ResponseEntity.ok(result);
    }
}