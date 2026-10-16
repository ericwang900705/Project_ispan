package gameplatform.admin.controller;

import gameplatform.admin.dto.AdminLoginRequest;
import gameplatform.admin.dto.AdminLoginResponse;
import gameplatform.admin.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminService adminService;

    public AdminAuthController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/login")
    public ResponseEntity<AdminLoginResponse> login(@RequestBody AdminLoginRequest request) {
        AdminLoginResponse response = adminService.login(request);
        return ResponseEntity.ok(response);
    }
}
