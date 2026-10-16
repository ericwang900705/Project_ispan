package gameplatform.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import gameplatform.admin.service.AdminMemberService;

@RestController
@RequestMapping("/api/admin/members")
@PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    // 手動建立建構子
    public AdminMemberController(AdminMemberService adminMemberService) {
        this.adminMemberService = adminMemberService;
    }

    // 停權會員
    @PatchMapping("/{memberId}/suspend")
    public ResponseEntity<String> suspendMember(
            @PathVariable Integer memberId,
            @RequestParam(defaultValue = "3") int hours) {

        String result = adminMemberService.suspendMember(memberId, hours);
        return ResponseEntity.ok(result);
    }

    // 恢復會員權限
    @PatchMapping("/{memberId}/reactivate")
    public ResponseEntity<String> reactivateMember(
            @PathVariable Integer memberId) {

        String result = adminMemberService.reactivateMember(memberId);
        return ResponseEntity.ok(result);
    }
}