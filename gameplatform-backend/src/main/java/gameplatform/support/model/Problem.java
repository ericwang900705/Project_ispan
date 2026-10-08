package gameplatform.support.model;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

public class Problem extends RuntimeException {
    final int status;

    public Problem(int status, String message) {
        super(message);
        this.status = status;
    }

    public static Problem denied() {
        return new Problem(403, "你無法操作此對話");
    }

    public static Problem missing() {
        return new Problem(404, "找不到此對話或案件");
    }

    @RestControllerAdvice(basePackages="gameplatform.support.controller")
    static class Handler {
        @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
        ResponseEntity<?> tooLarge(Exception e) {
            return ResponseEntity.status(413).body(Map.of("message", "每張圖片最多 5 MB，每則訊息最多 3 張。"));
        }

        @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
        ResponseEntity<?> missingPart(Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "請選擇要上傳的圖片。"));
        }

        @ExceptionHandler(Problem.class)
        ResponseEntity<?> problem(Problem e) {
            return ResponseEntity.status(e.status).body(Map.of("message", e.getMessage()));
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        ResponseEntity<?> invalid(MethodArgumentNotValidException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "請填寫必填欄位，並確認字數限制"));
        }

        @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
        ResponseEntity<?> conflict(Exception e) {
            return ResponseEntity.status(409).body(Map.of("message", "資料狀態已改變或關聯資料不存在，請重新整理後重試"));
        }
    }
}
