package gameplatform.friend.repository;

import gameplatform.friend.entity.FriendMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FriendMessageRepository extends JpaRepository<FriendMessage, Long> {

    // 某個聊天室的所有訊息，並依照建立時間由舊到新排序 (Asc)
    List<FriendMessage> findByFriendRoom_FriendRoomIdOrderByCreatedAtAsc(Integer friendRoomId);
}
