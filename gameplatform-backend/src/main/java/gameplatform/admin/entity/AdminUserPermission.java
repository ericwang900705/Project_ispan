package gameplatform.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_user_permissions", schema = "dbo")
@IdClass(AdminUserPermissionId.class)
public class AdminUserPermission {
    @Id
    @Column(name = "admin_id", nullable = false)
    private Integer adminId;

    @Id
    @Column(name = "permission_id", nullable = false)
    private Integer permissionId;

    // 與第一階段增量 SQL 一致：允許 NULL，保留未來資料匯入彈性。
    @Column(name = "granted_by_admin_id")
    private Integer grantedByAdminId;

    @Column(name = "granted_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime grantedAt;

    public Integer getAdminId() { return adminId; }
    public void setAdminId(Integer adminId) { this.adminId = adminId; }
    public Integer getPermissionId() { return permissionId; }
    public void setPermissionId(Integer permissionId) { this.permissionId = permissionId; }
    public Integer getGrantedByAdminId() { return grantedByAdminId; }
    public void setGrantedByAdminId(Integer grantedByAdminId) { this.grantedByAdminId = grantedByAdminId; }
    public LocalDateTime getGrantedAt() { return grantedAt; }
}
