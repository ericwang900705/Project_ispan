package gameplatform.admin.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gameplatform.member.entity.Member;
import gameplatform.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class AdminMemberService {

    private final MemberRepository memberRepository;

    // --- 停權 (鎖定會員) ---
    // days: 停權天數。若想永久停權，可傳入一個極大的數字，例如 3650 (十年)
    @Transactional
    public String suspendMember(Integer memberId, int hours) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("找不到該會員"));

        // 設定鎖定到期時間 (現在時間 + 停權時數)
        LocalDateTime lockEndTime = LocalDateTime.now().plusHours(hours);

        member.setLockedUntil(lockEndTime);
        memberRepository.save(member);

        return "會員 " + member.getUsername() + " 已被停權，直到 " + lockEndTime + "。";
    }

    // --- 復權 (解除鎖定) ---
    @Transactional
    public String reactivateMember(Integer memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("找不到該會員"));

        // 將 lockedUntil 設為 null，即代表無鎖定狀態
        member.setLockedUntil(null);
        // 如果你之前有做錯誤登入次數的機制，復權時一併將次數歸零會是個好習慣
        member.setFailedLoginCount(0);

        memberRepository.save(member);

        return "會員 " + member.getUsername() + " 已成功復權！";
    }
}
