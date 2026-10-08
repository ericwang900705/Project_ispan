package gameplatform.mail.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private final JavaMailSender mailSender;

    // 注入 Spring Boot 的 Mail 套件
    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // 寄送純文字信件
    public void sendSimpleTextMail(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("tony567710@gmail.com");
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);

        mailSender.send(message);
    }

    // 寄送 HTML 格式信件 (適合包含按鈕或超連結的驗證信)
    public void sendHtmlMail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            // 第二個參數 true 代表這是一封 HTML 信件
            helper.setText(htmlContent, true);

            mailSender.send(message);

        } catch (MessagingException e) {
            // 在實務上，寄信失敗應該要記錄 Log 或是丟出自訂例外
            throw new RuntimeException("HTML 信件發送失敗: " + e.getMessage());
        }
    }
}