package gameplatform.admin.controller;

import gameplatform.admin.service.AdminPublisherService;
import gameplatform.member.entity.Member;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/publishers")
@PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')") // 限制登入權限
public class AdminPublisherController {

    private final AdminPublisherService adminPublisherService;

    public AdminPublisherController(AdminPublisherService adminPublisherService) {
        this.adminPublisherService = adminPublisherService;
    }

    // 取得待審核清單
    @GetMapping("/pending")
    public ResponseEntity<List<Member>> getPendingApplications() {
        return ResponseEntity.ok(adminPublisherService.getPendingApplications());
    }

    // 審核發行商 (核准或拒絕)
    @PostMapping("/{memberId}/review")
    public ResponseEntity<String> reviewApplication(
            @PathVariable Integer memberId,
            @RequestParam boolean isApproved) {

        String result = adminPublisherService.reviewApplication(memberId, isApproved);
        return ResponseEntity.ok(result);
    }
}