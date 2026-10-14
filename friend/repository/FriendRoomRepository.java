package gameplatform.friend.repository;

import gameplatform.friend.entity.FriendRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FriendRoomRepository extends JpaRepository<FriendRoom, Integer> {

    // 尋找兩個會員之間的「一對一」聊天室
    // 透過 JOIN friend_room_members 兩次，來確保這個房間同時包含了 memberId1 與 memberId2
    @Query("SELECT r FROM FriendRoom r " +
            "JOIN FriendRoomMember frm1 ON r = frm1.friendRoom " +
            "JOIN FriendRoomMember frm2 ON r = frm2.friendRoom " +
            "WHERE r.roomType = 'DIRECT' " +
            "AND frm1.member.memberId = :memberId1 " +
            "AND frm2.member.memberId = :memberId2")
    Optional<FriendRoom> findDirectRoomBetween(@Param("memberId1") Integer memberId1,
            @Param("memberId2") Integer memberId2);
}
