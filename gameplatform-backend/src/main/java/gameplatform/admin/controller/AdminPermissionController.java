package gameplatform.admin.controller;

import gameplatform.admin.dto.*;
import gameplatform.admin.service.AdminPermissionService;
import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin/permissions")
public class AdminPermissionController {
    private final AdminPermissionService permissions;

    public AdminPermissionController(AdminPermissionService permissions) {
        this.permissions = permissions;
    }

    @GetMapping
    public List<PermissionResponse> list(Actor actor) {
        permissions.requireHighestAdmin(actor);
        return permissions.listAvailablePermissions().stream().map(PermissionResponse::from).toList();
    }

    @GetMapping("/staff/{id}")
    public Set<String> get(Actor actor, @PathVariable int id) {
        permissions.requireHighestAdmin(actor);
        return permissions.getStaffPermissionCodes(id);
    }

    @PutMapping("/staff/{id}")
    public Set<String> replace(Actor actor, @PathVariable int id,
            @RequestBody ReplacePermissionsRequest request) {
        if (request == null || request.permissionCodes() == null)
            throw new Problem(400, "permissionCodes 必填；清空請傳 []");
        permissions.replaceStaffPermissions(actor, id, request.permissionCodes());
        return permissions.getStaffPermissionCodes(id);
    }
}
