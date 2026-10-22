package gameplatform.admin.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import gameplatform.member.entity.Member;
import gameplatform.member.repository.MemberRepository;
import gameplatform.support.model.Problem;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class AdminMemberService {
    private final MemberRepository memberRepository;
    private final JdbcTemplate db;

    public record MemberPage(List<Map<String, Object>> content, long totalElements, int page, int size) {
    }

    // Explicit projection: never return the full entity (national ID / bank data).
    public MemberPage search(String keyword, String status, String type, int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 100)
            throw new Problem(400, "分頁參數不正確");
        keyword = keyword == null ? "" : keyword.trim();
        if (keyword.length() > 100)
            throw new Problem(400, "搜尋文字最多 100 字");
        if (!List.of("", "ACTIVE", "LOCKED").contains(status)
                || !List.of("", "MEMBER", "PUBLISHER").contains(type))
            throw new Problem(400, "篩選條件不正確");
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!keyword.isEmpty()) {
            where.append(
                    " AND (username LIKE ? ESCAPE '~' OR email LIKE ? ESCAPE '~' OR CONVERT(varchar(20),member_id)=?)");
            String term = "%" + keyword.replace("~", "~~").replace("%", "~%")
                    .replace("_", "~_").replace("[", "~[") + "%";
            args.add(term);
            args.add(term);
            args.add(keyword);
        }
        if (status.equals("LOCKED"))
            where.append(" AND locked_until > SYSDATETIME()");
        if (status.equals("ACTIVE"))
            where.append(" AND (locked_until IS NULL OR locked_until <= SYSDATETIME())");
        if (!type.isEmpty()) {
            where.append(" AND is_publisher=?");
            args.add(type.equals("PUBLISHER") ? 1 : 0);
        }
        Long count = db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.members" + where, Long.class, args.toArray());
        args.add(page * size);
        args.add(size);
        var rows = db.queryForList("""
                SELECT member_id AS memberId,username,email,is_publisher AS isPublisher,
                       company_name AS companyName,locked_until AS lockedUntil,created_at AS createdAt,
                       CASE WHEN locked_until > SYSDATETIME() THEN 'LOCKED' ELSE 'ACTIVE' END AS status
                FROM dbo.members
                """ + where + " ORDER BY member_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", args.toArray());
        return new MemberPage(rows, count == null ? 0 : count, page, size);
    }

    @Transactional
    public String suspendMember(Integer memberId, int hours) {
        if (hours < 1 || hours > 87600)
            throw new Problem(400, "停權時數須介於 1 至 87600 小時");
        Member member = member(memberId);
        LocalDateTime until = LocalDateTime.now().plusHours(hours);
        member.setLockedUntil(until);
        memberRepository.save(member);
        return "會員 " + member.getUsername() + " 已停權至 " + until + "。";
    }

    @Transactional
    public String reactivateMember(Integer memberId) {
        Member member = member(memberId);
        member.setLockedUntil(null);
        member.setFailedLoginCount(0);
        memberRepository.save(member);
        return "會員 " + member.getUsername() + " 已恢復使用。";
    }

    private Member member(Integer id) {
        return memberRepository.findById(id).orElseThrow(() -> new Problem(404, "找不到該會員"));
    }
}
