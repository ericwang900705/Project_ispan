package gameplatform.staff;
import java.util.Map;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import gameplatform.support.model.Actor;
@RestController
public class StaffController {
 private final StaffService staff;public StaffController(StaffService staff){this.staff=staff;}
 public record Role(String role){}
 public record PermissionUpdate(List<String> permissions){}
 @PostMapping("/api/auth/staff/register") public Object register(@Valid @RequestBody StaffService.Register r){staff.register(r);return Map.of("message","後台帳號已建立，請等待最高管理員授權後再登入。");}
 @GetMapping("/api/management/staff") public Object list(Actor a){return staff.list(a);}
 @PostMapping("/api/management/staff") public Object create(Actor a,@Valid @RequestBody StaffService.Register r){if(a==null||!a.isSuperAdmin())throw new gameplatform.support.model.Problem(403,"只有最高管理員可以新增人員");staff.register(r);return Map.of("message","人員已建立，尚未授權");}
 @PatchMapping("/api/management/staff/{id}/role") public Object role(Actor a,@PathVariable int id,@RequestBody Role r){staff.change(a,id,r.role());return Map.of("message","角色已更新，原有功能權限已清除；請重新勾選授權，該人員需要重新登入");}
 @PatchMapping("/api/management/staff/{id}/permissions") public Object permissions(Actor a,@PathVariable int id,@RequestBody PermissionUpdate body){staff.permissions(a,id,body.permissions());return Map.of("message","個別權限已更新");}
}
