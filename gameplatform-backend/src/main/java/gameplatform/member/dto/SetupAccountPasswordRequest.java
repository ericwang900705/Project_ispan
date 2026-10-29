package gameplatform.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SetupAccountPasswordRequest {
    @NotBlank(message = "請填寫登入帳號")
    @Pattern(regexp = "[\\p{IsHan}A-Za-z0-9_]{3,20}", message = "帳號名稱需為 3～20 個中文、英數字或底線")
    private String username;

    @NotBlank(message = "請填寫密碼")
    @Size(min = 8, max = 72, message = "密碼長度需為 8～72 位")
    private String newPassword;
}
