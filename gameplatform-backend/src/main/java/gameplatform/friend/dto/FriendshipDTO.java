package gameplatform.friend.dto;

import java.time.LocalDateTime;

// 紀錄要傳給前端的好友資訊，隱藏密碼與個資
public record FriendshipDTO(
        Integer friendshipId,
        Integer memberId, // 朋友或發送者的 ID
        String username, // 朋友或發送者的暱稱
        String avatarUrl, // 朋友或發送者的大頭貼
        String friendStatus,
        LocalDateTime createdAt,
        boolean isOnline // 好友目前是否在線上
) {
}
