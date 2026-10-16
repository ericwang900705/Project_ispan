package gameplatform.member.repository;

import gameplatform.member.entity.MemberLoginLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberLoginLogRepository extends JpaRepository<MemberLoginLog, Long> {
}
