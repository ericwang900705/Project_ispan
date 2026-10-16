package gameplatform.member.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "member_verifications", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "verification_id", nullable = false)
    private Integer verificationId;

    @Column(name = "email", length = 100, nullable = false)
    private String email;

    @Column(name = "verification_token", length = 200, nullable = false)
    private String verificationToken;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Builder.Default
    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified = false;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @PrePersist
    protected void onCreate() {
        if (this.sentAt == null) {
            this.sentAt = LocalDateTime.now();
        }
        if (this.isVerified == null) {
            this.isVerified = false;
        }
    }
}
