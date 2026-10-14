package gameplatform.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {
    @NotBlank(message = "密碼不能為空")
    @Size(min = 4, message = "密碼長度至少需要 4 個字元")
    private String oldPassword;

    @NotBlank(message = "密碼不能為空")
    @Size(min = 4, message = "密碼長度至少需要 4 個字元")
    private String newPassword;
}
