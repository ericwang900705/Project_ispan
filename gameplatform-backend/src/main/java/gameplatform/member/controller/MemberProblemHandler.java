package gameplatform.member.controller;
import gameplatform.support.model.Problem;
import gameplatform.member.exception.LoginFailedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;
@RestControllerAdvice(basePackages="gameplatform.member.controller")
public class MemberProblemHandler {
 @ExceptionHandler(Problem.class) public ResponseEntity<?> problem(Problem e){return ResponseEntity.status(e.httpStatus()).body(Map.of("message",e.getMessage()));}
 @ExceptionHandler(LoginFailedException.class) public ResponseEntity<?> login(LoginFailedException e){return ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));}
 @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) public ResponseEntity<?> conflict(Exception e){return ResponseEntity.status(409).body(Map.of("message","帳號資料狀態已更新，請重新登入或確認 Google 綁定"));}
 @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class) public ResponseEntity<?> invalid(Exception e){return ResponseEntity.badRequest().body(Map.of("message","請填寫完整且有效的資料"));}
}
