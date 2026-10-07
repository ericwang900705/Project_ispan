package gameplatform.member.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "members", schema = "dbo", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_members_username", columnNames = "username"),
        @UniqueConstraint(name = "UQ_members_email", columnNames = "email")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id", nullable = false)
    private Integer memberId;

    @Column(name = "username", length = 30, nullable = false)
    private String username;

    @Column(name = "national_id", length = 10)
    private String nationalId;

    @Column(name = "email", length = 100, nullable = false)
    private String email;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Builder.Default
    @Column(name = "failed_login_count", nullable = false)
    private Integer failedLoginCount = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Builder.Default
    @Column(name = "total_points", nullable = false)
    private Integer totalPoints = 0;

    @Builder.Default
    @Column(name = "balance", nullable = false)
    private Integer balance = 0;

    @Builder.Default
    @Column(name = "is_publisher", nullable = false)
    private Boolean isPublisher = false;

    @Column(name = "company_name", length = 100)
    private String companyName;

    @Column(name = "tax_id", length = 10)
    private String taxId;

    @Column(name = "website_url", length = 255)
    private String websiteUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "auth_provider", length = 20)
    @Builder.Default
    private String authProvider = "LOCAL"; // 預設為 "LOCAL"，第三方登入則為 "GOOGLE"

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.failedLoginCount == null) {
            this.failedLoginCount = 0;
        }
        if (this.totalPoints == null) {
            this.totalPoints = 0;
        }
        if (this.balance == null) {
            this.balance = 0;
        }
        if (this.isPublisher == null) {
            this.isPublisher = false;
        }
    }
}