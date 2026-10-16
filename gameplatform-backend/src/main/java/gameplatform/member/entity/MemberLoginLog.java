package gameplatform.member.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "member_login_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    private String loginStatus; // 'SUCCESS', 'FAILED', 'LOCKED'

    @Column(name = "login_at", nullable = false)
    @Builder.Default
    private LocalDateTime loginAt = LocalDateTime.now();
}
