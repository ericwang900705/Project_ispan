package gameplatform.member.service;

import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import gameplatform.support.model.Actor;

/**
 * Adapted from the Project_ispan member/friend branch to HERO's existing SQL
 * tables and single support JWT identity. There must be only one JWT decoder.
 * No new login provider or second members table is introduced.
 */
@Service
public class MemberAccountService {
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final ObjectProvider<JavaMailSender> mailProvider;
    private final String from;
    private final String externalBase;
    private static final SecureRandom RANDOM = new SecureRandom();

    public MemberAccountService(JdbcTemplate db, PasswordEncoder passwords,
            ObjectProvider<JavaMailSender> mailProvider,
            @Value("${app.member.mail.from:}") String from,
            @Value("${app.member.public-url:http://localhost:5173}") String externalBase) {
        this.db = db;
        this.passwords = passwords;
        this.mailProvider = mailProvider;
        this.from = from;
        this.externalBase = externalBase.replaceAll("/+$", "");
    }

    public record Register(String username, String email, String nationalId, String rawPassword) {}
    public record EmailRequest(String email) {}
    public record Reset(String token, String newPassword) {}
    public record ChangePassword(String oldPassword, String newPassword) {}
    public record ProfileUpdate(LocalDate birthDate, String address, String avatarUrl) {}

    private ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
    private JavaMailSender requireMail() {
        JavaMailSender mail = mailProvider.getIfAvailable();
        if (mail == null || from.isBlank()) throw error(HttpStatus.SERVICE_UNAVAILABLE,
                "尚未設定 SMTP 及 APP_MEMBER_MAIL_FROM，無法寄送會員信件");
        return mail;
    }
    private void send(JavaMailSender sender, String to, String title, String message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from); mail.setTo(to); mail.setSubject(title); mail.setText(message);
        sender.send(mail); // propagate failures; do not claim mail was sent
    }
    private String token() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private static String clean(String s) { return s == null ? "" : s.trim(); }
    private static <T> T required(T value, String msg) {
        if (value == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
        return value;
    }
    private static void validPassword(String pw) {
        if (pw == null || pw.length() < 8 || pw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"密碼至少 8 個字元，UTF-8 編碼不可超過 72 位元組");
    }
    // Taiwan identity checksum; same validation requirement as the teammate's register API.
    private static boolean validNationalId(String id) {
        if (id == null || !id.matches("[A-Z][12][0-9]{8}")) return false;
        String alphabet = "ABCDEFGHJKLMNPQRSTUVXYWZIO";
        int location = alphabet.indexOf(id.charAt(0)) + 10;
        if (location < 10) return false;
        int sum = location / 10 + (location % 10) * 9;
        int[] weights = {8,7,6,5,4,3,2,1,1};
        for (int i=1; i<10; i++) sum += (id.charAt(i)-'0')*weights[i-1];
        return sum % 10 == 0;
    }
    public record MailResult(String message, String watchTicket) {}
    @Transactional
    public MailResult register(Register input) {
         JavaMailSender mail = requireMail();
        if (input == null) throw error(HttpStatus.BAD_REQUEST,"缺少註冊資料");
        String user = clean(input.username()), email = clean(input.email()).toLowerCase(java.util.Locale.ROOT), id = clean(input.nationalId()).toUpperCase(java.util.Locale.ROOT);
        if (!user.matches("[A-Za-z0-9_]{4,30}")) throw error(HttpStatus.BAD_REQUEST,"帳號限 4～30 位英數字或底線");
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || email.length()>100)
            throw error(HttpStatus.BAD_REQUEST,"電子郵件格式不正確");
        if (!validNationalId(id)) throw error(HttpStatus.BAD_REQUEST,"身分證字號格式錯誤");
        validPassword(input.rawPassword());
        if (db.queryForObject("SELECT COUNT(*) FROM members WHERE username=? OR email=?",Integer.class,user,email)>0)
            throw error(HttpStatus.CONFLICT,"帳號或電子郵件已被註冊");
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        db.update(c -> {
            PreparedStatement ps=c.prepareStatement("INSERT INTO members(username,email,national_id) VALUES(?,?,?)",new String[]{"member_id"});
            ps.setString(1,user);ps.setString(2,email);ps.setString(3,id);return ps;
        },keys);
        Number key=keys.getKey();
        if (key==null) throw error(HttpStatus.INTERNAL_SERVER_ERROR,"無法取得新會員編號");
        db.update("INSERT INTO password_history(member_id,password_hash) VALUES(?,?)",key.intValue(),passwords.encode(input.rawPassword()));
        String code=token(), watch=token();
        db.update("INSERT INTO member_verifications(email,verification_token,expires_at,is_verified,watch_token_hash) VALUES(?,?,?,0,?)",
                email,code,Timestamp.valueOf(LocalDateTime.now().plusHours(1)),watchHash(watch));
        send(mail,email,"【HERO】請驗證會員電子郵件","請在 1 小時內點擊此連結完成驗證：\n"+externalBase+"/#/member/verify?token="+code+"\n如果不是您本人註冊，請忽略本郵件。");
        return new MailResult("註冊成功！請開啟驗證信完成驗證後登入。",watch);
    }
    private static String watchHash(String ticket) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(ticket.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public Map<String,Boolean> verificationStatus(String ticket) {
        if(ticket==null||!ticket.matches("[A-Za-z0-9_-]{43}"))throw error(HttpStatus.BAD_REQUEST,"驗證等待憑證無效，請重新寄送驗證信");
        var emails=db.queryForList("SELECT email FROM member_verifications WHERE watch_token_hash=? AND expires_at>CURRENT_TIMESTAMP",String.class,watchHash(ticket));
        if(emails.size()!=1)throw error(HttpStatus.GONE,"驗證等待已到期，請重新寄送驗證信");
        boolean verified=db.queryForObject("SELECT COUNT(*) FROM member_verifications WHERE email=? AND is_verified=1",Integer.class,emails.getFirst())>0;
        return Map.of("verified",verified);
    }
    @Transactional
    public String verify(String code) {
        
        if (clean(code).isEmpty()) throw error(HttpStatus.BAD_REQUEST,"缺少驗證碼");
        int n=db.update("UPDATE member_verifications SET is_verified=1,verified_at=CURRENT_TIMESTAMP WHERE verification_token=? AND is_verified=0 AND expires_at>CURRENT_TIMESTAMP",code);
        if (n!=1) throw error(HttpStatus.BAD_REQUEST,"驗證連結無效、已使用或已過期");
        return "電子郵件驗證成功，可以登入了。";
    }
    @Transactional
    public MailResult resend(EmailRequest input) {
         JavaMailSender mail=requireMail();
        required(input,"缺少信箱資料");
        String email=clean(input.email()).toLowerCase(java.util.Locale.ROOT);
        if (db.queryForObject("SELECT COUNT(*) FROM members WHERE email=?",Integer.class,email)!=1)
            throw error(HttpStatus.BAD_REQUEST,"找不到會員信箱");
        if (db.queryForObject("SELECT COUNT(*) FROM member_verifications WHERE email=? AND is_verified=1",Integer.class,email)>0)
            throw error(HttpStatus.BAD_REQUEST,"此信箱已完成驗證");
        String code=token(), watch=token();
        db.update("INSERT INTO member_verifications(email,verification_token,expires_at,is_verified,watch_token_hash) VALUES(?,?,?,0,?)",email,code,Timestamp.valueOf(LocalDateTime.now().plusHours(1)),watchHash(watch));
        send(mail,email,"【HERO】重新發送驗證信","請在 1 小時內點擊：\n"+externalBase+"/#/member/verify?token="+code);
        return new MailResult("已寄出新的驗證信。",watch);
    }
    @Transactional
    public String forgot(EmailRequest input) {
         JavaMailSender mail=requireMail();
        required(input,"缺少信箱資料");
        String email=clean(input.email()).toLowerCase(java.util.Locale.ROOT);
        List<Integer> ids=db.query("SELECT member_id FROM members WHERE email=?",(rs,n)->rs.getInt(1),email);
        if (!ids.isEmpty()) {
            String code=token();
            db.update("INSERT INTO password_resets(member_id,reset_token,expires_at,is_reset) VALUES(?,?,?,0)",ids.getFirst(),code,Timestamp.valueOf(LocalDateTime.now().plusHours(1)));
            send(mail,email,"【HERO】重設密碼","請在 1 小時內開啟此網址設定新密碼：\n"+externalBase+"/#/member/reset-password?token="+code);
        }
        return "如果這個信箱有註冊帳號，您會收到重設密碼信。";
    }
    @Transactional
    public String reset(Reset input) {
         required(input, "缺少密碼重設資料"); validPassword(input.newPassword());
        List<Integer> members=db.query("SELECT member_id FROM password_resets WHERE reset_token=? AND is_reset=0 AND expires_at>CURRENT_TIMESTAMP",(rs,n)->rs.getInt(1),clean(input.token()));
        if(members.size()!=1) throw error(HttpStatus.BAD_REQUEST,"連結無效或已過期");
        int id=members.getFirst();
        String last=lastPassword(id);
        if (passwords.matches(input.newPassword(),last)) throw error(HttpStatus.BAD_REQUEST,"新密碼不能與目前密碼相同");
        int changed=db.update("UPDATE password_resets SET is_reset=1,reset_at=CURRENT_TIMESTAMP WHERE reset_token=? AND is_reset=0 AND expires_at>CURRENT_TIMESTAMP",clean(input.token()));
        if(changed!=1) throw error(HttpStatus.BAD_REQUEST,"連結無效或已過期");
        db.update("UPDATE password_resets SET is_reset=1,reset_at=CURRENT_TIMESTAMP WHERE member_id=? AND is_reset=0",id);
        db.update("INSERT INTO password_history(member_id,password_hash) VALUES(?,?)",id,passwords.encode(input.newPassword()));
        db.update("UPDATE members SET failed_login_count=0,locked_until=NULL WHERE member_id=?",id);
        return "密碼已重設，請重新登入。";
    }
    private String lastPassword(int id) {
        List<String> hashes=db.query("SELECT TOP 1 password_hash FROM password_history WHERE member_id=? ORDER BY created_at DESC,password_history_id DESC",(rs,n)->rs.getString(1),id);
        if(hashes.isEmpty()) throw error(HttpStatus.BAD_REQUEST,"找不到密碼紀錄");
        return hashes.getFirst();
    }
    private void requireMember(Actor actor) {
        if(actor==null || !actor.isMember()) throw error(HttpStatus.FORBIDDEN,"僅會員與發行商可使用");
    }
    public Map<String,Object> profile(Actor actor) {
        requireMember(actor);
        List<Map<String,Object>> rows=db.queryForList("SELECT member_id,username,email,national_id,birth_date,address,avatar_url,total_points,balance,is_publisher,created_at FROM members WHERE member_id=?",actor.id());
        if(rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"找不到會員");
        Map<String,Object> v=rows.getFirst(),result=new LinkedHashMap<>();
        for(String name:List.of("memberId","username","email","nationalId","birthDate","address","avatarUrl","totalPoints","balance","isPublisher","createdAt")){
            String dbName=name.replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase();
            Object value=v.get(dbName);
            if(value instanceof java.sql.Date date)value=date.toLocalDate().toString();
            result.put(name,value);
        }
        return result;
    }
    @Transactional
    public Map<String,Object> update(Actor actor,ProfileUpdate input) {
        requireMember(actor);required(input,"缺少個人資料");
        if(input.birthDate()!=null){if(!input.birthDate().isBefore(LocalDate.now())) throw error(HttpStatus.BAD_REQUEST,"出生日期必須早於今天");int changed=db.update("UPDATE members SET birth_date=? WHERE member_id=? AND birth_date IS NULL",input.birthDate(),actor.id());
            if(changed==0){var dates=db.queryForList("SELECT birth_date FROM members WHERE member_id=?",java.sql.Date.class,actor.id());if(dates.isEmpty())throw error(HttpStatus.NOT_FOUND,"找不到會員");if(!input.birthDate().equals(dates.getFirst().toLocalDate()))throw error(HttpStatus.CONFLICT,"生日設定後不能再次修改");}}
        if(input.address()!=null){if(input.address().length()>500) throw error(HttpStatus.BAD_REQUEST,"地址超過長度限制");db.update("UPDATE members SET address=? WHERE member_id=?",input.address(),actor.id());}
        if(input.avatarUrl()!=null){String url=input.avatarUrl().trim();if(url.length()>500 || (!url.isEmpty() && !url.matches("https?://[^\\s]+")))throw error(HttpStatus.BAD_REQUEST,"頭像網址必須為有效的 HTTP(S) 網址");db.update("UPDATE members SET avatar_url=? WHERE member_id=?",url.isEmpty()?null:url,actor.id());}
        return profile(actor);
    }
    @Transactional
    public String changePassword(Actor actor,ChangePassword input){
        requireMember(actor);required(input,"缺少密碼修改資料");validPassword(input.newPassword());
        if(!passwords.matches(Objects.toString(input.oldPassword(),""),lastPassword(actor.id())))throw error(HttpStatus.BAD_REQUEST,"原密碼不正確");
        if(passwords.matches(input.newPassword(),lastPassword(actor.id())))throw error(HttpStatus.BAD_REQUEST,"新密碼不能與原密碼相同");
        db.update("INSERT INTO password_history(member_id,password_hash) VALUES(?,?)",actor.id(),passwords.encode(input.newPassword()));
        return "密碼修改成功，下次請使用新密碼登入。";
    }
}
