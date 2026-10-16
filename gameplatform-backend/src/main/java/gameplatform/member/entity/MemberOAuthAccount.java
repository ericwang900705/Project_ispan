package gameplatform.member.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "member_oauth_accounts", schema = "dbo",
       uniqueConstraints = @UniqueConstraint(name = "UQ_provider_user", columnNames = {"provider", "provider_user_id"}))
@Getter @Setter
public class MemberOAuthAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "oauth_id")
    private Integer oauthId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;
    @Column(name = "provider", nullable = false, length = 20)
    private String provider;
    @Column(name = "provider_user_id", nullable = false, length = 100)
    private String providerUserId;
    @Column(name = "email", length = 100)
    private String email;
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
