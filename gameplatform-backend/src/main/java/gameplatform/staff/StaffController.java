package gameplatform.staff;

import gameplatform.support.model.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 管理員帳號與權限管理 API。 */
@RestController
@RequestMapping("/api/management/staff")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class StaffController {

    private final StaffService staff;

    public record RoleRequest(@NotBlank String role) { }

    public record PermissionRequest(@NotNull List<@NotBlank String> permissions) { }

    // 查詢管理員清單及有效權限
    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal Actor actor) {
        return staff.list(actor);
    }

    // 由 ADMIN 建立後台人員
    @PostMapping
    public Map<String, String> create(
            @AuthenticationPrincipal Actor actor,
            @Valid @RequestBody StaffService.Register request) {
        staff.create(actor, request);
        return Map.of("message", "後台人員已建立，等待角色授權");
    }

    // 允許 STAFF、REVIEWER、SUPPORT 互相切換
    @PatchMapping("/{id}/role")
    public Map<String, String> changeRole(
            @AuthenticationPrincipal Actor actor,
            @PathVariable int id,
            @Valid @RequestBody RoleRequest request) {
        staff.change(actor, id, request.role());
        return Map.of("message", "角色已更新，請該管理員重新登入");
    }

    // 只有 STAFF 可以設定個別權限
    @PatchMapping("/{id}/permissions")
    public Map<String, String> updatePermissions(
            @AuthenticationPrincipal Actor actor,
            @PathVariable int id,
            @Valid @RequestBody PermissionRequest request) {
        staff.permissions(actor, id, request.permissions());
        return Map.of("message", "權限已更新");
    }
}
