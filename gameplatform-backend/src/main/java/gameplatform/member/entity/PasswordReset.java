package gameplatform.member.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "password_resets", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordReset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reset_id", nullable = false)
    private Integer resetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "FK_password_resets_members"))
    private Member member;

    @Column(name = "reset_token", length = 200, nullable = false)
    private String resetToken;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Builder.Default
    @Column(name = "is_reset", nullable = false)
    private Boolean isReset = false;

    @Column(name = "reset_at")
    private LocalDateTime resetAt;

    @PrePersist
    protected void onCreate() {
        if (this.sentAt == null) {
            this.sentAt = LocalDateTime.now();
        }
        if (this.isReset == null) {
            this.isReset = false;
        }
    }
}
