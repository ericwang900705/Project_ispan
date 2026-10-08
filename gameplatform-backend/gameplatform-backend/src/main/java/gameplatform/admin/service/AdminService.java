package gameplatform.admin.service;

import gameplatform.admin.dto.AdminLoginRequest;
import gameplatform.admin.dto.AdminLoginResponse;
import gameplatform.admin.entity.Admin;
import gameplatform.admin.repository.AdminRepository;
import gameplatform.member.util.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AdminService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AdminService(AdminRepository adminRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public AdminLoginResponse login(AdminLoginRequest request) {
        // 1. 透過帳號尋找管理員
        Admin admin = adminRepository.findByAdminAccount(request.getAdminAccount())
                .orElseThrow(() -> new RuntimeException("帳號或密碼錯誤"));

        // 2. 檢查帳號狀態是否被停用
        if ("SUSPENDED".equals(admin.getStatus())) {
            throw new RuntimeException("此帳號已被停用，請聯繫系統管理員");
        }

        // 3. 驗證密碼 (使用 BCrypt 比對明文與資料庫的 Hash)
        if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
            throw new RuntimeException("帳號或密碼錯誤");
        }

        // 4. 驗證成功，更新最後登入時間
        admin.setLastLoginAt(LocalDateTime.now());
        adminRepository.save(admin);

        // 5. 簽發管理員專屬 JWT，將 RoleCode 寫入 Token
        String roleCode = admin.getAdminRole().getRoleCode();
        String token = jwtTokenProvider.generateAdminToken(admin.getAdminAccount(), admin.getAdminId(), roleCode);

        // 6. 回傳包含 Token 與管理員基本資訊的 DTO
        return AdminLoginResponse.builder()
                .token(token)
                .adminName(admin.getAdminName())
                .roleCode(roleCode)
                .build();
    }
}
