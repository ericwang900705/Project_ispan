package gameplatform.admin.entity;

import java.io.Serializable;
import java.util.Objects;

public class AdminUserPermissionId implements Serializable {
    private Integer adminId;
    private Integer permissionId;

    public AdminUserPermissionId() {
    }

    public AdminUserPermissionId(Integer adminId, Integer permissionId) {
        this.adminId = adminId;
        this.permissionId = permissionId;
    }

    public Integer getAdminId() {
        return adminId;
    }

    public void setAdminId(Integer adminId) {
        this.adminId = adminId;
    }

    public Integer getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(Integer permissionId) {
        this.permissionId = permissionId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (!(other instanceof AdminUserPermissionId that))
            return false;
        return Objects.equals(adminId, that.adminId) && Objects.equals(permissionId, that.permissionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(adminId, permissionId);
    }
}
