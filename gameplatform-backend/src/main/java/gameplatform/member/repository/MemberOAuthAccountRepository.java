package gameplatform.member.repository;

import gameplatform.member.entity.MemberOAuthAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MemberOAuthAccountRepository extends JpaRepository<MemberOAuthAccount, Integer> {
    Optional<MemberOAuthAccount> findByProviderAndProviderUserId(String provider, String providerUserId);
}
