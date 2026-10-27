package gameplatform.staff;

import gameplatform.support.model.Problem;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Return predictable JSON error details for staff-management operations. */
@RestControllerAdvice(assignableTypes = StaffController.class)
public class StaffProblemHandler {
    @ExceptionHandler(Problem.class)
    ResponseEntity<Map<String, String>> handle(Problem ex) {
        return ResponseEntity.status(ex.httpStatus()).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> status(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("message", ex.getReason() == null ? "操作失敗" : ex.getReason()));
    }
}
