package gameplatform.member.service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import gameplatform.mail.service.MailService;
import gameplatform.member.dto.ChangePasswordRequest;
import gameplatform.member.dto.ForgotPasswordRequest;
import gameplatform.member.dto.GoogleLoginRequest;
import gameplatform.member.dto.LoginRequest;
import gameplatform.member.dto.MemberProfileResponse;
import gameplatform.member.dto.PublisherApplicationRequest;
import gameplatform.member.dto.RegisterRequest;
import gameplatform.member.dto.ResendVerificationRequest;
import gameplatform.member.dto.ResetPasswordRequest;
import gameplatform.member.dto.UpdateProfileRequest;
import gameplatform.member.entity.Member;
import gameplatform.member.entity.MemberVerification;
import gameplatform.member.entity.PasswordHistory;
import gameplatform.member.entity.PasswordReset;
import gameplatform.member.repository.MemberRepository;
import gameplatform.member.repository.MemberVerificationRepository;
import gameplatform.member.repository.PasswordHistoryRepository;
import gameplatform.member.repository.PasswordResetRepository;
import gameplatform.support.service.JwtService;
import lombok.RequiredArgsConstructor;
import gameplatform.support.model.Actor;
import java.util.Map;
import gameplatform.member.util.NationalIdValidator;
import gameplatform.member.exception.LoginFailedException;

// 建構子注入 JwtTokenProvider
@RequiredArgsConstructor
@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService; // 1. 宣告 JwtService
    private final MemberVerificationRepository memberVerificationRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final MailService mailService;
    private final JdbcTemplate db;

    // ---1.註冊
    @Transactional
    public void register(RegisterRequest request) {
        // 檢查身份證字號
        if (!NationalIdValidator.isValid(request.getNationalId())) {
            throw new RuntimeException("身分證字號格式錯誤或無效");
        }
        // 1. 檢查帳號與信箱是否已存在
        if (memberRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("帳號已被使用");
        }
        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("信箱已被註冊");
        }

        // 2. 建立並儲存會員
        Member member = new Member();
        member.setUsername(request.getUsername());
        member.setEmail(request.getEmail());
        member.setNationalId(request.getNationalId());
        memberRepository.save(member);

        // 3. 建立密碼歷史紀錄，儲存雜湊後的密碼
        PasswordHistory passwordHistory = new PasswordHistory();
        passwordHistory.setMember(member); // 關聯到剛剛建立的會員
        passwordHistory.setPasswordHash(passwordEncoder.encode(request.getRawPassword()));

        // 儲存密碼紀錄 (你已經在最上面注入了 passwordHistoryRepository)
        passwordHistoryRepository.save(passwordHistory);

        // 4. 產生 UUID 驗證碼
        String token = UUID.randomUUID().toString();

        // 5. 儲存驗證紀錄至資料庫 (設定 1 小時後過期)
        MemberVerification verification = new MemberVerification();
        verification.setEmail(member.getEmail());
        verification.setVerificationToken(token);
        verification.setExpiresAt(LocalDateTime.now().plusHours(1));
        verification.setIsVerified(false);
        memberVerificationRepository.save(verification);

        // 6. 組合驗證信 HTML 內容並寄出
        // 注意：這裡的 localhost:8080 未來上線要換成你的正式網域或前端的 URL
        String verifyLink = "http://localhost:8080/api/auth/verify?token=" + token;
        String emailContent = "<div style='font-family: Arial, sans-serif; padding: 20px;'>" +
                "<h2>歡迎加入遊戲平台！</h2>" +
                "<p>親愛的玩家您好，請點擊下方按鈕完成信箱驗證，啟用您的帳號：</p>" +
                "<a href='" + verifyLink
                + "' style='display: inline-block; padding: 10px 20px; color: white; background-color: #007BFF; text-decoration: none; border-radius: 5px;'>完成信箱驗證</a>"
                +
                "<p>如果您並未註冊本平台，請忽略此信件。</p>" +
                "<p>本連結將於 1 小時後失效。</p>" +
                "</div>";

        mailService.sendHtmlMail(member.getEmail(), "【遊戲平台】請驗證您的註冊信箱", emailContent);
    }

    // ---2.登入(1013新增交易回滾邏輯)
    @Transactional(noRollbackFor = LoginFailedException.class)
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
            // 連續錯誤 3 次鎖定 1 分鐘
            if (member.getFailedLoginCount() >= 3) {
                member.setLockedUntil(LocalDateTime.now().plusMinutes(1));
            }
            memberRepository.save(member);
            throw new LoginFailedException("帳號或密碼錯誤");
        }
        // 5. 登入成功：重置失敗次數與鎖定時間
        member.setFailedLoginCount(0);
        member.setLockedUntil(null);
        memberRepository.save(member);
        // 6. 簽發 JWT Token
        return generateMemberToken(member);
    }

    // ---3.取得個人資料

    public MemberProfileResponse getMemberProfile(String username) {

        // 1. 根據 JWT 中的帳號查詢會員
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("找不到該會員"));

        // 2. 將 Member Entity 轉換成 DTO
        return MemberProfileResponse.builder()
                .memberId(member.getMemberId())
                .username(member.getUsername())
                .email(member.getEmail())
                .nationalId(member.getNationalId())
                .birthDate(member.getBirthDate())
                .address(member.getAddress())
                .avatarUrl(member.getAvatarUrl())
                .totalPoints(member.getTotalPoints())
                .balance(member.getBalance())
                .isPublisher(member.getIsPublisher())
                .createdAt(member.getCreatedAt())
                .build();
    }

    // --- 4. 完善/修改個人資料
    @Transactional
    public MemberProfileResponse updateProfile(String username, UpdateProfileRequest request) {
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("找不到該會員"));

        // 只更新有傳遞過來的欄位
        if (request.getBirthDate() != null) {
            member.setBirthDate(request.getBirthDate());
        }
        if (request.getAddress() != null) {
            member.setAddress(request.getAddress());
        }
        if (request.getAvatarUrl() != null) {
            member.setAvatarUrl(request.getAvatarUrl());
        }

        memberRepository.save(member);

        // 回傳更新後的最新狀態
        return buildProfileResponse(member);
    }

    // 5. 上傳大頭貼實體檔案並更新會員資料
    @Transactional
    public MemberProfileResponse uploadAvatar(String username, org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("請選擇要上傳的圖片檔案");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new RuntimeException("只能上傳圖片格式 (JPG, PNG 等)");
        }

        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("找不到該會員"));

        try {
            // 1. 建立儲存目錄 (專案根目錄下的 uploads/avatars)
            java.nio.file.Path uploadDir = java.nio.file.Paths.get("uploads/avatars");
            if (!java.nio.file.Files.exists(uploadDir)) {
                java.nio.file.Files.createDirectories(uploadDir);
            }

            // 2. 產生不重複的檔名 (UUID + 原副檔名)
            String originalFilename = file.getOriginalFilename();
            String ext = (originalFilename != null && originalFilename.contains("."))
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : ".jpg";
            String newFileName = java.util.UUID.randomUUID().toString() + ext;

            // 3. 將檔案寫入硬碟
            java.nio.file.Path filePath = uploadDir.resolve(newFileName);
            java.nio.file.Files.copy(file.getInputStream(), filePath,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // 4. 更新會員的 avatarUrl 欄位並存檔
            String avatarUrl = "/uploads/avatars/" + newFileName;
            member.setAvatarUrl(avatarUrl);
            memberRepository.save(member);

            return buildProfileResponse(member);
        } catch (java.io.IOException e) {
            throw new RuntimeException("圖片上傳失敗：" + e.getMessage());
        }
    }

    // --- 將 Entity 轉為 Response 的共用小工具 ---
    private MemberProfileResponse buildProfileResponse(Member member) {
        return MemberProfileResponse.builder()
                .memberId(member.getMemberId())
                .username(member.getUsername())
                .email(member.getEmail())
                .nationalId(member.getNationalId())
                .birthDate(member.getBirthDate())
                .address(member.getAddress())
                .avatarUrl(member.getAvatarUrl())
                .totalPoints(member.getTotalPoints())
                .balance(member.getBalance())
                .isPublisher(member.getIsPublisher())
                .createdAt(member.getCreatedAt())
                .build();
    }

    // ---6.驗證信箱的方法
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

    // --- 7. 登入狀態下修改密碼
    @Transactional
    public String changePassword(String username, ChangePasswordRequest request) {
        // 1. 取得目前登入的會員
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("找不到該會員"));

        // 2. 取得他最新的密碼紀錄
        PasswordHistory latestPassword = passwordHistoryRepository.findFirstByMemberOrderByCreatedAtDesc(member)
                .orElseThrow(() -> new RuntimeException("無法取得密碼紀錄"));

        // 3. 驗證舊密碼是否正確
        if (!passwordEncoder.matches(request.getOldPassword(), latestPassword.getPasswordHash())) {
            throw new RuntimeException("舊密碼不正確");
        }

        // 檢查新密碼是否和舊密碼一樣
        if (passwordEncoder.matches(request.getNewPassword(), latestPassword.getPasswordHash())) {
            throw new RuntimeException("新密碼不能與舊密碼相同");
        }

        // 4. 將新密碼加密，並新增一筆到密碼歷史紀錄表
        PasswordHistory newPasswordHistory = PasswordHistory.builder()
                .member(member)
                .passwordHash(passwordEncoder.encode(request.getNewPassword()))
                .build();

        passwordHistoryRepository.save(newPasswordHistory);

        return "密碼修改成功！下次請使用新密碼登入。";
    }

    // ---8.申請忘記密碼
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

        // 4. 真實寄出重設密碼信
        // 目前先導向後端網址方便測試。實務上有了前端後，這裡要改成前端的忘記密碼頁面網址 (例如
        // http://localhost:5173/reset-password?token=)
        String resetLink = "http://localhost:8080/api/auth/reset-password?token=" + token;

        String subject = "【遊戲平台】密碼重設要求";
        String content = "<h3>親愛的會員您好：</h3>"
                + "<p>我們收到了您重設密碼的要求。請點擊下方連結來設定您的新密碼：</p>"
                + "<a href=\"" + resetLink
                + "\" style=\"display: inline-block; padding: 10px 20px; color: white; background-color: #007bff; text-decoration: none; border-radius: 5px;\">設定新密碼</a>"
                + "<p>此連結將在 1 小時後失效。</p>"
                + "<p>如果您沒有提出此要求，請直接忽略這封信，您的密碼將不會更改。</p>";

        // 呼叫你的 mailService 寄信 (假設你的寄信方法叫做 sendHtmlMail，請依你實際的命名調整)
        mailService.sendHtmlMail(member.getEmail(), subject, content);
        return "密碼重設信已寄出，請至信箱查收。";
    }

    // ---9.執行重設密碼
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

    // ---10.重發驗證信
    @Transactional
    public String resendVerificationEmail(ResendVerificationRequest request) {
        // 1. 確認該信箱是否真的有註冊
        Member member = memberRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("找不到此信箱對應的帳號"));

        // 2. 檢查是否已經驗證過了
        List<MemberVerification> verifications = memberVerificationRepository.findByEmail(request.getEmail());
        boolean isAlreadyVerified = verifications.stream().anyMatch(MemberVerification::getIsVerified);

        if (isAlreadyVerified) {
            throw new RuntimeException("該信箱已經驗證過了，請直接登入");
        }

        // 3. 產生全新的 UUID 驗證碼並存入資料庫
        String token = UUID.randomUUID().toString();
        MemberVerification newVerification = new MemberVerification();
        newVerification.setEmail(member.getEmail());
        newVerification.setVerificationToken(token);
        newVerification.setExpiresAt(LocalDateTime.now().plusHours(1));
        newVerification.setIsVerified(false);
        memberVerificationRepository.save(newVerification);

        // 4. 寄出驗證信
        String verifyLink = "http://localhost:8080/api/auth/verify?token=" + token;
        String emailContent = "<div style='font-family: Arial, sans-serif; padding: 20px;'>" +
                "<h2>【遊戲平台】重發驗證信</h2>" +
                "<p>親愛的玩家您好，我們收到了您重新發送驗證信的要求。請點擊下方按鈕完成信箱驗證：</p>" +
                "<a href='" + verifyLink
                + "' style='display: inline-block; padding: 10px 20px; color: white; background-color: #28a745; text-decoration: none; border-radius: 5px;'>完成信箱驗證</a>"
                + "<p>本連結將於 1 小時後失效。</p>" +
                "</div>";

        mailService.sendHtmlMail(member.getEmail(), "【遊戲平台】請驗證您的註冊信箱", emailContent);

        return "驗證信已重新寄出，請至信箱查收。";
    }

    // ---11.申請成為發行商
    @Transactional
    public String applyToBePublisher(String username, PublisherApplicationRequest request) {

        // Lock the member row to serialize simultaneous applications from this account.
        var ids = db.queryForList(
                "SELECT member_id FROM dbo.members WITH (UPDLOCK,HOLDLOCK) WHERE username=?",
                Integer.class, username);
        if (ids.isEmpty()) throw new RuntimeException("找不到該會員");
        // 1. 取得當前登入的會員
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("找不到該會員"));

        // 2. 狀態檢查
        if (member.getIsPublisher() != null && member.getIsPublisher()) {
            throw new RuntimeException("您已經是發行商了，無需重複申請");
        }

        if (db.queryForObject("SELECT COUNT(*) FROM dbo.publisher_applications WHERE member_id=? AND approval_status='PENDING'", Integer.class, member.getMemberId()) > 0
                || member.getCompanyName() != null) {
            throw new RuntimeException("您的發行商申請正在審核中，請耐心等候");
        }

        // 3. 寫入公司資料，進入待審核狀態
        member.setCompanyName(request.getCompanyName());
        member.setTaxId(request.getTaxId());
        member.setWebsiteUrl(request.getWebsiteUrl());
        member.setBankName(request.getBankName());

        memberRepository.save(member);
        // Current application form has no document uploader; keep SQL document_path nullable.
        db.update("INSERT INTO dbo.publisher_applications(member_id,company_name,tax_id,document_path,approval_status) VALUES(?,?,?,NULL,'PENDING')",
                member.getMemberId(), request.getCompanyName(), request.getTaxId());

        return "發行商申請已送出，請等候管理員審核！";
    }

    // ---12.google登入
    // 1. 從 application.properties 動態讀取 Client ID
    @Value("${google.client.id}")
    private String googleClientId;

    @Transactional
    public String googleLogin(GoogleLoginRequest request) {
        String email = "";
        String username = "";

        try {
            // 2. 建立 Google 官方的驗證器
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(),
                    new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            // 3. 向 Google 伺服器驗證這串 Token 的真偽
            GoogleIdToken idToken = verifier.verify(request.getIdToken());

            if (idToken != null) {
                // 驗證成功，提取 Google 帳號資訊
                GoogleIdToken.Payload payload = idToken.getPayload();
                email = payload.getEmail();

                // 從 Google 取得使用者名稱 (去掉空白避免格式問題，並加上 UUID 確保遊戲帳號不重複)
                String googleName = (String) payload.get("name");
                if (googleName == null)
                    googleName = "Player";
                username = googleName.replaceAll("\\s+", "") + "_" + UUID.randomUUID().toString().substring(0, 4);

            } else {
                throw new RuntimeException("無效的 Google Token，驗證失敗");
            }
        } catch (Exception e) {
            throw new RuntimeException("Google 驗證過程發生錯誤: " + e.getMessage());
        }

        // --- 4. 系統內部登入/註冊邏輯 ---
        Member member = memberRepository.findByEmail(email).orElse(null);

        if (member == null) {
            // 資料庫沒這個信箱，自動註冊為會員
            member = Member.builder()
                    .username(username)
                    .email(email)
                    .build();
            member = memberRepository.save(member);
        }

        // 5. 簽發並回傳我們系統專屬的 JWT Token
        return generateMemberToken(member);
    }

    // ---13. 產生會員 JWT Token(新增共用方法1015)
    private String generateMemberToken(Member member) {
        String role = Boolean.TRUE.equals(member.getIsPublisher())
                ? "PUBLISHER"
                : "MEMBER";

        Actor actor = new Actor(
                member.getMemberId(),
                role,
                member.getUsername(),
                "");

        return (String) jwtService.issue(actor).get("accessToken");
    }
}
