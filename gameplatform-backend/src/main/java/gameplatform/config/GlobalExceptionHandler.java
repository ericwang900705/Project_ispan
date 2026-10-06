package gameplatform.config;

import gameplatform.member.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice // 告訴 Spring Boot：這是一個全局的攔截器，專門處理例外
public class GlobalExceptionHandler {

    // 只要系統中拋出 RuntimeException (包含我們在 Service 寫的 throw new
    // RuntimeException)，都會被這裡抓到
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {

        // 把錯誤包裝成我們剛剛定義的 ErrorResponse 格式
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value()) // 400 Bad Request
                .message(ex.getMessage()) // 抓取 Service 丟出來的訊息
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    // 未來還可以繼續加其他 Exception 的處理
}