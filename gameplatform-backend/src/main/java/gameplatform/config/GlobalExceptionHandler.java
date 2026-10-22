
package gameplatform.config;

import gameplatform.member.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice(basePackages = "gameplatform.admin.controller")
public class GlobalExceptionHandler {

        @ExceptionHandler(gameplatform.support.model.Problem.class)
        public ResponseEntity<?> handleProblem(gameplatform.support.model.Problem ex) {
                return ResponseEntity.status(ex.httpStatus()).body(java.util.Map.of("message", ex.getMessage()));
        }

        // 處理管理員模組的權限不足
        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<ErrorResponse> handleAccessDeniedException(
                        AccessDeniedException ex) {

                ErrorResponse errorResponse = ErrorResponse.builder()
                                .status(HttpStatus.FORBIDDEN.value())
                                .message("權限不足 (Access Denied)")
                                .timestamp(LocalDateTime.now())
                                .build();

                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(errorResponse);
        }

        // 處理管理員模組的執行階段例外
        @ExceptionHandler(RuntimeException.class)
        public ResponseEntity<ErrorResponse> handleRuntimeException(
                        RuntimeException ex) {

                ErrorResponse errorResponse = ErrorResponse.builder()
                                .status(HttpStatus.BAD_REQUEST.value())
                                .message(ex.getMessage())
                                .timestamp(LocalDateTime.now())
                                .build();

                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(errorResponse);
        }
}
