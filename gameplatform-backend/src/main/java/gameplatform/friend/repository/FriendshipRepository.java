package gameplatform.friend.repository;

import gameplatform.friend.entity.Friendship;
import gameplatform.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Integer> {

        // 1. 檢查兩人之間是否已經存在任何關係紀錄 (防止重複發送邀請 我邀請他 OR 他邀請我)
        @Query("SELECT f FROM Friendship f WHERE (f.requester = :member1 AND f.receiver = :member2) " +
                        "OR (f.requester = :member2 AND f.receiver = :member1)")
        Optional<Friendship> findFriendshipBetween(@Param("member1") Member member1, @Param("member2") Member member2);

        // 2. 查詢「別人發給我的待確認邀請」(我是 receiver，且狀態為 PENDING)
        List<Friendship> findByReceiverAndFriendStatus(Member receiver, String friendStatus);

        // 3. 取得「我的所有正式好友名單」(我是 requester 或 receiver，且狀態為 ACCEPTED)
        @Query("SELECT f FROM Friendship f WHERE (f.requester = :member OR f.receiver = :member) " +
                        "AND f.friendStatus = 'ACCEPTED'")
        List<Friendship> findAllAcceptedFriends(@Param("member") Member member);
}