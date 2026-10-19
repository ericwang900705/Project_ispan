package gameplatform.admin.service;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gameplatform.member.entity.Member;
import gameplatform.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import gameplatform.mail.service.MailService;

@RequiredArgsConstructor
@Service
public class AdminPublisherService {

    private final MemberRepository memberRepository;
    private final MailService mailService;
    private final JdbcTemplate db;

    // 只回傳審核畫面需要的欄位，避免公開完整 Member 物件及敏感資料。
    public List<java.util.Map<String, Object>> getPendingApplications() {
        return db.queryForList("""
                SELECT p.member_id AS memberId,
                       m.username,
                       p.company_name AS companyName,
                       p.tax_id AS taxId,
                       p.applied_at AS appliedAt
                FROM dbo.publisher_applications p
                JOIN dbo.members m ON m.member_id=p.member_id
                WHERE p.approval_status='PENDING' AND m.is_publisher=0
                ORDER BY p.applied_at DESC, p.application_id DESC
                """);
    }

    // 只公開申請歷程需要的欄位，不回傳會員密碼、身分證或銀行資訊。
    public List<java.util.Map<String, Object>> history() {
        return db.queryForList("""
                SELECT p.application_id AS applicationId,p.member_id AS memberId,
                       m.username,p.company_name AS companyName,p.tax_id AS taxId,
                       p.approval_status AS status,p.applied_at AS appliedAt,
                       p.reviewed_at AS reviewedAt,p.review_comment AS reviewComment,
                       a.admin_name AS reviewerName
                FROM dbo.publisher_applications p
                JOIN dbo.members m ON m.member_id=p.member_id
                LEFT JOIN dbo.admin a ON a.admin_id=p.admin_id
                WHERE p.approval_status <> 'PENDING'
                ORDER BY p.application_id DESC
                """);
    }

    // 執行審核 (核准或退回)
    @Transactional
    public String reviewApplication(Integer memberId, boolean isApproved, Actor actor) {
        if (actor == null || !actor.canReview())
            throw new Problem(403, "沒有發行商審核權限");
        var pending = db.queryForList(
                "SELECT application_id FROM dbo.publisher_applications WITH (UPDLOCK,HOLDLOCK) WHERE member_id=? AND approval_status='PENDING' ORDER BY applied_at DESC,application_id DESC",
                Integer.class, memberId);
        if (pending.isEmpty())
            throw new Problem(409, "目前沒有待審核的發行商申請");
        Integer applicationId = pending.getFirst();
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("找不到該會員"));

        if (member.getIsPublisher()) {
            throw new RuntimeException("該會員已經是發行商了");
        }

        if (isApproved) {
            // 核准申請：將 isPublisher 設為 true
            member.setIsPublisher(true);
            memberRepository.save(member);
            db.update(
                    "UPDATE dbo.publisher_applications SET approval_status='APPROVED',admin_id=?,reviewed_at=SYSDATETIME(),review_comment=? WHERE application_id=? AND approval_status='PENDING'",
                    actor.id(), "已核准", applicationId);
            // 使用現有的 sendHtmlMail 發送核准信
            String subject = "【GamePlatform】發行商申請核准通知";
            String content = "<h3>親愛的 " + member.getUsername() + " 您好：</h3>"
                    + "<p>您為「<b>" + member.getCompanyName() + "</b>」提交的發行商申請已通過審核！</p>"
                    + "<p>現在您可以登入平台，進入發行商專屬後台開始上架您的遊戲。</p>"
                    + "<br><p>GamePlatform 團隊 敬上</p>";

            sendAfterCommit(member.getEmail(), subject, content);
            return "已核准該會員的發行商申請！";
        } else {
            String rejectCompanyName = member.getCompanyName();
            // 退回申請：清空公司相關資料，讓玩家可以重新提交
            member.setCompanyName(null);
            member.setTaxId(null);
            member.setWebsiteUrl(null);
            member.setBankName(null);
            memberRepository.save(member);
            db.update(
                    "UPDATE dbo.publisher_applications SET approval_status='REJECTED',admin_id=?,reviewed_at=SYSDATETIME(),review_comment=? WHERE application_id=? AND approval_status='PENDING'",
                    actor.id(), "未通過", applicationId);
            // 使用現有的 sendHtmlMail 發送退回信
            String subject = "【GamePlatform】發行商申請未通過通知";
            String content = "<h3>親愛的 " + member.getUsername() + " 您好：</h3>"
                    + "<p>很遺憾，您為「<b>" + rejectCompanyName + "</b>」提交的發行商申請，經審核後未能通過。</p>"
                    + "<p>我們已清除您的公司資料，您可以隨時在個人資料頁面重新提交申請。</p>"
                    + "<br><p>GamePlatform 團隊 敬上</p>";
            sendAfterCommit(member.getEmail(), subject, content);
            return "已退回該申請，並清空其公司資料。";
        }
    }

    private void sendAfterCommit(String to, String subject, String html) {
        TransactionSynchronizationManager.registerSynchronization(
                new MailAfterCommit(mailService, to, subject, html));
    }

    private static final class MailAfterCommit implements TransactionSynchronization {
        private final MailService mailer;
        private final String to;
        private final String subject;
        private final String html;

        private MailAfterCommit(MailService mailer, String to, String subject, String html) {
            this.mailer = mailer;
            this.to = to;
            this.subject = subject;
            this.html = html;
        }

        @Override
        public void afterCommit() {
            try {
                mailer.sendHtmlMail(to, subject, html);
            } catch (Exception ex) {
                System.err.println("發行商審核已儲存，但通知信寄送失敗: " + ex.getMessage());
            }
        }
    }
}
