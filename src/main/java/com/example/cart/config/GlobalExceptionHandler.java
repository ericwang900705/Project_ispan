package com.example.cart.config;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 統一處理 Service 丟出的例外
// 回傳 HTTP 400 與 { "message": "..." }，前端用 e.response.data.message 就能顯示原因（例如「你已經擁有這款遊戲」）
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handle(Exception e) {
        return new ResponseEntity<>(Map.of("message", String.valueOf(e.getMessage())), HttpStatus.BAD_REQUEST);
    }
}
