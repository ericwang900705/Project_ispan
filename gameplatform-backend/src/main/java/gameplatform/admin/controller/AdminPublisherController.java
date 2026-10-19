
package gameplatform.admin.controller;

import gameplatform.admin.service.AdminPublisherService;
import gameplatform.member.entity.Member;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 發行商申請審核 Controller。
 *
 * 權限：
 * 1. ADMIN：超級管理員，可以操作。
 * 2. REVIEWER：原本的審核員，可以操作。
 * 3. STAFF：必須由 ADMIN 授予 PUBLISHER_REVIEW 權限。
 * 4. SUPPORT、MEMBER、PUBLISHER：不可操作。
 *
 * 此 Controller 的 memberId 是被審核的會員 ID，
 * 並不是目前登入的管理員 ID，因此保留 PathVariable。
 */
@RestController
@RequestMapping("/api/admin/publishers")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_REVIEWER', 'PUBLISHER_REVIEW')")
public class AdminPublisherController {

    private final AdminPublisherService adminPublisherService;

    public AdminPublisherController(
            AdminPublisherService adminPublisherService) {

        this.adminPublisherService = adminPublisherService;
    }

    /**
     * 查詢待審核的發行商申請。
     *
     * GET /api/admin/publishers/pending
     */
    @GetMapping("/pending")
    public ResponseEntity<List<Member>> getPendingApplications() {

        return ResponseEntity.ok(
                adminPublisherService.getPendingApplications());
    }

    /**
     * 核准或拒絕發行商申請。
     *
     * POST /api/admin/publishers/{memberId}/review
     *
     * memberId：被審核的會員 ID。
     * isApproved：true 為核准，false 為拒絕。
     *
     * 操作者的權限由 Spring Security 驗證。
     */
    @PostMapping("/{memberId}/review")
    public ResponseEntity<String> reviewApplication(
            @PathVariable Integer memberId,
            @RequestParam boolean isApproved) {

        String result = adminPublisherService.reviewApplication(
                memberId,
                isApproved);

        return ResponseEntity.ok(result);
    }
}
