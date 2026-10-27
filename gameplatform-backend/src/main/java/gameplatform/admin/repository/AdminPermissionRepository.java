package gameplatform.admin.repository;

import gameplatform.admin.entity.AdminPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AdminPermissionRepository extends JpaRepository<AdminPermission, Integer> {
    Optional<AdminPermission> findByPermissionCode(String permissionCode);
}
