
package gameplatform.recommendation.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "member_game_view_stats", schema = "dbo")
@IdClass(MemberGameViewStatId.class)
@Getter
@Setter
@NoArgsConstructor
public class MemberGameViewStat {

    @Id
    @Column(name = "member_id")
    private Integer memberId;

    @Id
    @Column(name = "game_id")
    private Integer gameId;

    @Column(name = "view_count", nullable = false)
    private Integer viewCount = 1;

    @Column(name = "first_viewed_at", nullable = false)
    private LocalDateTime firstViewedAt;

    @Column(name = "last_viewed_at", nullable = false)
    private LocalDateTime lastViewedAt;
}
