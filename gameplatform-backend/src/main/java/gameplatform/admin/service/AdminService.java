
package gameplatform.admin.service;

import gameplatform.admin.dto.AdminLoginRequest;
import gameplatform.admin.dto.AdminLoginResponse;
import gameplatform.admin.entity.Admin;
import gameplatform.admin.repository.AdminRepository;
import gameplatform.support.service.JwtService;
import gameplatform.support.model.Actor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AdminService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AdminService(
            AdminRepository adminRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AdminLoginResponse login(AdminLoginRequest request) {

        // 1. 查詢管理員
        Admin admin = adminRepository
                .findByAdminAccount(request.getAdminAccount())
                .orElseThrow(() -> new RuntimeException("帳號或密碼錯誤"));

        // 2. 檢查是否停用
        if ("SUSPENDED".equals(admin.getStatus())) {
            throw new RuntimeException("此帳號已被停用，請聯繫系統管理員");
        }

        // 3. 驗證密碼
        if (!passwordEncoder.matches(
                request.getPassword(),
                admin.getPasswordHash())) {
            throw new RuntimeException("帳號或密碼錯誤");
        }

        // 4. 更新最後登入時間
        admin.setLastLoginAt(LocalDateTime.now());
        adminRepository.save(admin);

        // 5. 取得管理員角色
        String roleCode = admin.getAdminRole().getRoleCode();

        // 6. 建立管理員身分
        Actor actor = new Actor(
                admin.getAdminId(),
                roleCode,
                admin.getAdminAccount(),
                "");

        // 7. 使用原本 JwtService 簽發 Token
        String token = (String) jwtService.issue(actor).get("accessToken");

        // 8. 回傳登入結果
        return AdminLoginResponse.builder()
                .token(token)
                .adminName(admin.getAdminName())
                .roleCode(roleCode)
                .build();
    }
}
