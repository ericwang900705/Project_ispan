package gameplatform.admin.controller;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import gameplatform.admin.service.AdminMemberService;

@RestController
@RequestMapping("/api/admin/members")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'REVIEWER', 'SUPPORT')")
public class AdminMemberController {
    private final AdminMemberService members;
    public AdminMemberController(AdminMemberService members) { this.members = members; }

    @GetMapping
    public AdminMemberService.MemberPage search(@RequestParam(defaultValue="") String keyword,
            @RequestParam(defaultValue="") String status, @RequestParam(defaultValue="") String type,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size) {
        return members.search(keyword, status, type, page, size);
    }

    @PatchMapping("/{memberId}/suspend")
    public Map<String,String> suspend(@PathVariable Integer memberId, @RequestParam(defaultValue="3") int hours) {
        return Map.of("message", members.suspendMember(memberId, hours));
    }

    @PatchMapping("/{memberId}/reactivate")
    public Map<String,String> reactivate(@PathVariable Integer memberId) {
        return Map.of("message", members.reactivateMember(memberId));
    }
}
