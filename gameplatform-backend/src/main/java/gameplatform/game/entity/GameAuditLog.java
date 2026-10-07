package gameplatform.game.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "game_audit_logs")
@Getter
@Setter
public class GameAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "game_audit_id")
    private Integer gameAuditId;

    @Column(name = "game_id", nullable = false)
    private Integer gameId;

    @Column(name = "admin_id", nullable = false)
    private Integer adminId;

    @Column(name = "review_type", nullable = false, length = 20)
    private String reviewType;

    @Column(name = "decision", nullable = false, length = 20)
    private String decision;

    @Column(name = "comment", length = 500)
    private String comment;

    @Column(name = "reviewed_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime reviewedAt;
}