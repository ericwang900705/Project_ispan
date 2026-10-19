package gameplatform.admin.repository;

import gameplatform.admin.entity.AdminUserPermission;
import gameplatform.admin.entity.AdminUserPermissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AdminUserPermissionRepository extends JpaRepository<AdminUserPermission, AdminUserPermissionId> {
    List<AdminUserPermission> findByAdminId(Integer adminId);

    void deleteByAdminIdAndPermissionId(Integer adminId, Integer permissionId);
}
