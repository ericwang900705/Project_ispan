package gameplatform.recommendation.entity;

import java.math.BigDecimal;
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
@Table(name = "ai_game_recommendations", schema = "dbo")
@Getter
@Setter
public class AiGameRecommendation {

    // 推薦紀錄 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_id")
    private Integer recommendationId;

    // 被推薦的會員 ID
    @Column(name = "member_id", nullable = false)
    private Integer memberId;

    // 推薦的遊戲 ID
    @Column(name = "game_id", nullable = false)
    private Integer gameId;

    // AI 推薦分數 (0 ~ 1)
    @Column(name = "recommendation_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal recommendationScore;

    // AI 推薦原因
    @Column(name = "reason", length = 500)
    private String reason;

    // AI 模型版本
    @Column(name = "model_version", length = 50)
    private String modelVersion;

    // 推薦建立時間
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;
}