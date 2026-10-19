package gameplatform.admin.controller;

import gameplatform.admin.service.AdminPublisherService;
import gameplatform.support.model.Actor;
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
    public ResponseEntity<List<java.util.Map<String,Object>>> getPendingApplications() {
        return ResponseEntity.ok(adminPublisherService.getPendingApplications());
    }

    @GetMapping("/history")
    public java.util.List<java.util.Map<String,Object>> history() {
        return adminPublisherService.history();
    }

    // 審核發行商 (核准或拒絕)
    @PostMapping("/{memberId}/review")
    public ResponseEntity<java.util.Map<String,String>> reviewApplication(
            @PathVariable Integer memberId,
            @RequestParam boolean isApproved,
            Actor actor) {

        String result = adminPublisherService.reviewApplication(memberId, isApproved, actor);
        return ResponseEntity.ok(java.util.Map.of("message", result));
    }
}