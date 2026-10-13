package gameplatform.recommendation.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ai_recommendation_refresh_status", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
public class AiRecommendationRefreshStatus {

    // 會員 ID：主鍵
    @Id
    @Column(name = "member_id")
    private Integer memberId;

    // 更新狀態
    @Column(name = "refresh_status", nullable = false, length = 20)
    private String refreshStatus = "PENDING";

    // 上次嘗試更新時間
    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    // 上次成功更新時間
    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;

    // 下次允許更新時間
    @Column(name = "next_refresh_at")
    private LocalDateTime nextRefreshAt;

    // 最近一次錯誤訊息
    @Column(name = "last_error", length = 500)
    private String lastError;

    // AI 推薦更新任務識別碼
    @Column(name = "refresh_token")
    private UUID refreshToken;
}