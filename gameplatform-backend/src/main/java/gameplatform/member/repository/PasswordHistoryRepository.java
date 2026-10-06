package gameplatform.member.repository;

import gameplatform.member.entity.Member;
import gameplatform.member.entity.PasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Integer> {

    // 找出該會員最新的一筆密碼紀錄
    Optional<PasswordHistory> findFirstByMemberOrderByCreatedAtDesc(Member member);
}
