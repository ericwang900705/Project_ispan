package gameplatform.member.repository;

import gameplatform.member.entity.MemberVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberVerificationRepository extends JpaRepository<MemberVerification, Integer> {

    // 透過 Token 尋找驗證紀錄 (用於使用者點擊信件連結時的驗證)
    Optional<MemberVerification> findByVerificationToken(String verificationToken);

    // 尋找某個 Email 最新發送的一筆未驗證紀錄
    Optional<MemberVerification> findFirstByEmailAndIsVerifiedFalseOrderBySentAtDesc(String email);
}
