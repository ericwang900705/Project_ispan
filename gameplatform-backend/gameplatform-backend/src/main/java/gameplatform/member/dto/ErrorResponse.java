package gameplatform.member.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private int status; // HTTP 狀態碼 (例如 400)
    private String message; // 錯誤訊息 (例如 "此帳號已被使用")
    private LocalDateTime timestamp; // 發生時間
}
