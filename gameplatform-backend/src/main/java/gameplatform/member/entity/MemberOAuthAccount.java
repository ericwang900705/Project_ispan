package gameplatform.member.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "member_oauth_accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberOAuthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "oauth_id")
    private Integer oauthId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider; // 'GOOGLE'

    @Column(name = "provider_user_id", nullable = false, length = 100)
    private String providerUserId; // Google 的 sub

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
