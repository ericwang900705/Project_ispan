package gameplatform.admin.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminLoginResponse {
    private String token;
    private String adminName;
    private String roleCode; // 讓前端知道登入者的身分，以決定顯示哪些左側選單
}