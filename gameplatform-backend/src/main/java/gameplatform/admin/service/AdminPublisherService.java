package gameplatform.admin.service;

import java.util.List;

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

    // 取得所有待審核的發行商申請
    public List<Member> getPendingApplications() {
        return memberRepository.findByIsPublisherFalseAndCompanyNameIsNotNull();
    }

    // 執行審核 (核准或退回)
    @Transactional
    public String reviewApplication(Integer memberId, boolean isApproved) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("找不到該會員"));

        if (member.getIsPublisher()) {
            throw new RuntimeException("該會員已經是發行商了");
        }

        if (member.getCompanyName() == null) {
            throw new RuntimeException("該會員尚未提交發行商申請 (缺少公司資料)");
        }

        if (isApproved) {
            // 核准申請：將 isPublisher 設為 true
            member.setIsPublisher(true);
            memberRepository.save(member);
            // 使用現有的 sendHtmlMail 發送核准信
            String subject = "【GamePlatform】發行商申請核准通知";
            String content = "<h3>親愛的 " + member.getUsername() + " 您好：</h3>"
                    + "<p>您為「<b>" + member.getCompanyName() + "</b>」提交的發行商申請已通過審核！</p>"
                    + "<p>現在您可以登入平台，進入發行商專屬後台開始上架您的遊戲。</p>"
                    + "<br><p>GamePlatform 團隊 敬上</p>";

            mailService.sendHtmlMail(member.getEmail(), subject, content);
            return "已核准該會員的發行商申請！";
        } else {
            String rejectCompanyName = member.getCompanyName();
            // 退回申請：清空公司相關資料，讓玩家可以重新提交
            member.setCompanyName(null);
            member.setTaxId(null);
            member.setWebsiteUrl(null);
            member.setBankName(null);
            memberRepository.save(member);
            // 使用現有的 sendHtmlMail 發送退回信
            String subject = "【GamePlatform】發行商申請未通過通知";
            String content = "<h3>親愛的 " + member.getUsername() + " 您好：</h3>"
                    + "<p>很遺憾，您為「<b>" + rejectCompanyName + "</b>」提交的發行商申請，經審核後未能通過。</p>"
                    + "<p>我們已清除您的公司資料，您可以隨時在個人資料頁面重新提交申請。</p>"
                    + "<br><p>GamePlatform 團隊 敬上</p>";
            mailService.sendHtmlMail(member.getEmail(), subject, content);
            return "已退回該申請，並清空其公司資料。";
        }
    }
}
