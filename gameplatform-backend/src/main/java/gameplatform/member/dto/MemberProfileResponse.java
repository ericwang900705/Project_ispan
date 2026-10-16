package gameplatform.member.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class MemberProfileResponse {
    private Integer memberId;
    private String username;
    private String email;
    private String nationalId;
    private LocalDate birthDate;
    private String address;
    private String avatarUrl;

    // 遊戲資產狀態
    private Integer totalPoints;
    private Integer balance;
    private Boolean isPublisher;

    private LocalDateTime createdAt;

    private Boolean hasPassword;// 標記是否有密碼（用以限制前端顯示「修改密碼」）
}
