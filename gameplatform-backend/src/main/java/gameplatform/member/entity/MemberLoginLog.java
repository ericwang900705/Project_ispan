package gameplatform.member.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "member_login_logs", schema = "dbo")
@Getter
@Setter
public class MemberLoginLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "login_log_id")
    private Long loginLogId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;
    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;
    @Column(name = "login_status", nullable = false, length = 20)
    private String loginStatus;
    @Column(name = "login_at", nullable = false)
    private LocalDateTime loginAt;
}
