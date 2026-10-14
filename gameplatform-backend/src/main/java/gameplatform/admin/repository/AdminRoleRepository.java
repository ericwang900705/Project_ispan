package gameplatform.admin.repository;

import gameplatform.admin.entity.AdminRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminRoleRepository extends JpaRepository<AdminRole, Integer> {

    // 透過角色代碼尋找角色 (未來在新增管理員時，用來賦予特定權限)
    Optional<AdminRole> findByRoleCode(String roleCode);
}
