package gameplatform.member.service;

import gameplatform.member.dto.LoginRequest;
import gameplatform.member.dto.RegisterRequest;
import gameplatform.member.entity.Member;
import gameplatform.member.entity.MemberVerification;
import gameplatform.member.entity.PasswordHistory;
import gameplatform.member.repository.MemberRepository;
import gameplatform.member.repository.MemberVerificationRepository;
import gameplatform.member.repository.PasswordHistoryRepository;
import gameplatform.member.repository.PasswordResetRepository;
import gameplatform.member.util.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import gameplatform.member.dto.MemberProfileResponse;
import gameplatform.member.repository.PasswordResetRepository;
import gameplatform.member.entity.PasswordReset;
import gameplatform.member.dto.ForgotPasswordRequest;
import gameplatform.member.dto.ResetPasswordRequest;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider; // 1. 宣告 JwtTokenProvider
    private MemberVerificationRepository memberVerificationRepository;
    private final PasswordResetRepository passwordResetRepository;

    // 2. 建構子注入 JwtTokenProvider
    public MemberService(MemberRepository memberRepository,
            PasswordHistoryRepository passwordHistoryRepository,
            MemberVerificationRepository memberVerificationRepository,
            PasswordResetRepository passwordResetRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider) {
        this.memberRepository = memberRepository;
        this.passwordHistoryRepository = passwordHistoryRepository;
        this.memberVerificationRepository = memberVerificationRepository;
        this.passwordResetRepository = passwordResetRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public String registerTest(RegisterRequest request) {
        if (memberRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("此帳號已被使用");
        }
        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("此信箱已被註冊");
        }

        Member newMember = Member.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .build();
        Member savedMember = memberRepository.save(newMember);

        PasswordHistory passwordHistory = PasswordHistory.builder()
                .member(savedMember)
                .passwordHash(passwordEncoder.encode(request.getRawPassword()))
                .build();
        passwordHistoryRepository.save(passwordHistory);

        // --- 以下為新增的信箱驗證邏輯 ---

        // 1. 產生一組隨機且唯一的 UUID 作為驗證碼
        String token = UUID.randomUUID().toString();

        // 2. 建立驗證紀錄 (設定 24 小時後過期)
        MemberVerification verification = MemberVerification.builder()
                .email(savedMember.getEmail())
                .verificationToken(token)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        memberVerificationRepository.save(verification);

        // 3. 模擬寄信：在終端機印出驗證連結
        String verifyLink = "http://localhost:8080/api/auth/verify-email?token=" + token;
        System.out.println("=================================================");
        System.out.println("模擬寄信給: " + savedMember.getEmail());
        System.out.println("請點擊以下連結完成驗證:");
        System.out.println(verifyLink);
        System.out.println("=================================================");

        return "註冊成功！請至信箱點擊驗證連結。會員 ID 為: " + savedMember.getMemberId();
    }

    @Transactional
    public String login(LoginRequest request) {
        // 1. 尋找帳號
        Member member = memberRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("帳號或密碼錯誤"));

        // 2. 檢查帳號是否被鎖定
        if (member.getLockedUntil() != null && member.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("帳號已被鎖定，請稍後再試");
        }

        // 3. 取得最新密碼紀錄
        PasswordHistory latestPassword = passwordHistoryRepository.findFirstByMemberOrderByCreatedAtDesc(member)
                .orElseThrow(() -> new RuntimeException("帳號或密碼錯誤"));

        // 4. 驗證密碼
        if (!passwordEncoder.matches(request.getPassword(), latestPassword.getPasswordHash())) {
            // 密碼錯誤：增加失敗次數
            member.setFailedLoginCount(member.getFailedLoginCount() + 1);
            if (member.getFailedLoginCount() >= 3) { // 連續錯誤 3 次鎖定 1 分鐘
                member.setLockedUntil(LocalDateTime.now().plusMinutes(1));
            }
            memberRepository.save(member);
            throw new RuntimeException("帳號或密碼錯誤");
        }

        // 5. 登入成功：重置失敗次數與鎖定時間
        member.setFailedLoginCount(0);
        member.setLockedUntil(null);
        memberRepository.save(member);

        // 6. 簽發 JWT Token
        return jwtTokenProvider.generateToken(member.getUsername(), member.getMemberId());
    }

    public MemberProfileResponse getMemberProfile(String username) {
        // 透過帳號去資料庫把完整的 Member Entity 撈出來
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("找不到該會員"));

        // 將 Entity 轉換成前端需要的 DTO 格式
        return MemberProfileResponse.builder()
                .memberId(member.getMemberId())
                .username(member.getUsername())
                .email(member.getEmail())
                .createdAt(member.getCreatedAt())
                .build();
    }

    // --- 驗證信箱的方法 ---
    @Transactional
    public String verifyEmail(String token) {
        // 1. 去資料庫找這組 token
        MemberVerification verification = memberVerificationRepository.findByVerificationToken(token)
                .orElseThrow(() -> new RuntimeException("無效的驗證碼"));

        // 2. 檢查是否已經驗證過
        if (verification.getIsVerified()) {
            throw new RuntimeException("此信箱已經驗證過了");
        }

        // 3. 檢查是否過期
        if (verification.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("驗證連結已過期，請重新註冊或申請重發");
        }

        // 4. 驗證成功，更新狀態
        verification.setIsVerified(true);
        verification.setVerifiedAt(LocalDateTime.now());
        memberVerificationRepository.save(verification);

        return "信箱驗證成功！";
    }

    // --- 申請忘記密碼 ---
    @Transactional
    public String forgotPassword(ForgotPasswordRequest request) {
        // 1. 確認信箱是否存在
        Member member = memberRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("找不到此信箱對應的帳號"));

        // 2. 產生重設 Token
        String token = UUID.randomUUID().toString();

        // 3. 儲存重設紀錄 (設定 1 小時後過期)
        PasswordReset passwordReset = PasswordReset.builder()
                .member(member)
                .resetToken(token)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        passwordResetRepository.save(passwordReset);

        // 4. 模擬寄信
        String resetLink = "http://localhost:8080/api/auth/reset-password?token=" + token;
        System.out.println("=================================================");
        System.out.println("模擬寄出密碼重設信給: " + member.getEmail());
        System.out.println("請點擊以下連結重設密碼 (實務上前端會做一個重設密碼的網頁):");
        System.out.println(resetLink);
        System.out.println("=================================================");

        return "密碼重設信已寄出，請至信箱查收。";
    }

    // --- 執行重設密碼 ---
    @Transactional
    public String resetPassword(ResetPasswordRequest request) {
        // 1. 驗證 Token 是否存在且有效
        PasswordReset passwordReset = passwordResetRepository.findByResetToken(request.getToken())
                .orElseThrow(() -> new RuntimeException("無效的重設連結"));

        if (passwordReset.getIsReset()) {
            throw new RuntimeException("此連結已經使用過了");
        }
        if (passwordReset.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("重設連結已過期，請重新申請");
        }

        // 2. 取得該會員
        Member member = passwordReset.getMember();

        // 3. 密碼加密並新增一筆到歷史紀錄表
        PasswordHistory newPasswordHistory = PasswordHistory.builder()
                .member(member)
                .passwordHash(passwordEncoder.encode(request.getNewPassword()))
                .build();
        passwordHistoryRepository.save(newPasswordHistory);

        // 4. 更新 Token 狀態為已使用
        passwordReset.setIsReset(true);
        passwordReset.setResetAt(LocalDateTime.now());
        passwordResetRepository.save(passwordReset);

        // 5. 解除帳號鎖定 (如果他之前因為密碼錯太多次被鎖定，重設密碼後直接幫他解鎖)
        member.setFailedLoginCount(0);
        member.setLockedUntil(null);
        memberRepository.save(member);

        return "密碼重設成功！請使用新密碼登入。";
    }

}