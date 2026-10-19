package gameplatform.friend.repository;

import gameplatform.friend.entity.FriendRoomMember;
import gameplatform.friend.entity.FriendRoomMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FriendRoomMemberRepository extends JpaRepository<FriendRoomMember, FriendRoomMemberId> {

    // 找出該會員參與的所有聊天室， leftAt 必須為 null
    List<FriendRoomMember> findByMember_MemberIdAndLeftAtIsNull(Integer memberId);

    // 找出聊天室裡面的所有成員 (用來在一對一聊天室中找出「對方」是誰)
    List<FriendRoomMember> findByFriendRoom_FriendRoomIdAndLeftAtIsNull(Integer friendRoomId);
}
