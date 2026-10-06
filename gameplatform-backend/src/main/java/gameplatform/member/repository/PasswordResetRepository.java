package gameplatform.member.repository;

import gameplatform.member.entity.Member;
import gameplatform.member.entity.PasswordReset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetRepository extends JpaRepository<PasswordReset, Integer> {

    // 透過 Token 尋找重設紀錄 (用於驗證信件連結有效性)
    Optional<PasswordReset> findByResetToken(String resetToken);

    // 尋找某會員最新的一筆未重設紀錄 (防止短時間內重複發送)
    Optional<PasswordReset> findFirstByMemberAndIsResetFalseOrderBySentAtDesc(Member member);
}