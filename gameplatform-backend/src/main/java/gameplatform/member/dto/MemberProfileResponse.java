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
    private Boolean hasPassword;

    // 當google登入修改帳號時，回傳新的 JWT Token
    private String accessToken;
}
