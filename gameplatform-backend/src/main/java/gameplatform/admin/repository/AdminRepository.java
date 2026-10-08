package gameplatform.admin.repository;

import gameplatform.admin.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminRepository extends JpaRepository<Admin, Integer> {

    // 透過帳號尋找管理員 (這是後台登入 API 最核心的方法)
    Optional<Admin> findByAdminAccount(String adminAccount);

    // 檢查該帳號是否已經存在 (未來新增管理員時用來防呆)
    boolean existsByAdminAccount(String adminAccount);
}
