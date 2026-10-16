package gameplatform.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "帳號不能為空")
    @Size(min = 4, max = 30, message = "帳號長度須介於 4 到 30 個字元")
    private String username;

    @NotBlank(message = "信箱不能為空")
    @Email(message = "信箱格式不正確")
    private String email;

    @NotBlank(message = "身分證字號不能為空")
    private String nationalId;

    @NotBlank(message = "密碼不能為空")
    @Size(min = 4, message = "密碼長度至少需要 4 個字元")
    private String rawPassword; // 前端傳來的明文密碼
}
