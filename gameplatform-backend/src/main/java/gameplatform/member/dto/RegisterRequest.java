package gameplatform.member.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private String email;
    private String rawPassword; // 前端傳來的明文密碼
}
