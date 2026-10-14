package gameplatform.friend.service;

import gameplatform.friend.entity.FriendRoom;
import gameplatform.friend.entity.FriendRoomMember;
import gameplatform.friend.entity.FriendRoomMemberId;
import gameplatform.friend.dto.ChatMessageDTO;
import gameplatform.friend.entity.FriendMessage;
import gameplatform.friend.repository.FriendMessageRepository;
import gameplatform.friend.repository.FriendRoomMemberRepository;
import gameplatform.friend.repository.FriendRoomRepository;
import gameplatform.member.entity.Member;
import gameplatform.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final FriendRoomRepository friendRoomRepository;
    private final FriendRoomMemberRepository friendRoomMemberRepository;
    private final FriendMessageRepository friendMessageRepository;
    private final MemberRepository memberRepository;

    // 1. 取得或建立一對一聊天室
    @Transactional
    public FriendRoom getOrCreateDirectRoom(Integer currentUserId, Integer targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new RuntimeException("不能跟自己建立聊天室");
        }

        // 先檢查兩人是否已經有一對一聊天室
        Optional<FriendRoom> existingRoom = friendRoomRepository.findDirectRoomBetween(currentUserId, targetUserId);
        if (existingRoom.isPresent()) {
            return existingRoom.get();
        }

        Member currentUser = memberRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("找不到發起人"));
        Member targetUser = memberRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("找不到目標使用者"));

        // 建立房間實體 (預設為 DIRECT)
        FriendRoom newRoom = FriendRoom.builder()
                .roomType("DIRECT")
                .creator(currentUser)
                .build();
        newRoom = friendRoomRepository.save(newRoom);

        // 將兩人加入聊天室成員名單
        FriendRoomMember member1 = FriendRoomMember.builder()
                .friendRoom(newRoom)
                .member(currentUser)
                .build();

        FriendRoomMember member2 = FriendRoomMember.builder()
                .friendRoom(newRoom)
                .member(targetUser)
                .build();

        friendRoomMemberRepository.saveAll(List.of(member1, member2));

        return newRoom;
    }

    // 2. 獲取該會員參與的所有聊天室清單
    public List<FriendRoomMember> getMyRooms(Integer memberId) {
        // 利用 Repository 找出所有 leftAt 為 null (仍在群組內) 的紀錄
        return friendRoomMemberRepository.findByMember_MemberIdAndLeftAtIsNull(memberId);
    }

    // 3. 獲取特定聊天室的歷史訊息
    public List<FriendMessage> getRoomHistory(Integer roomId, Integer currentUserId) {
        // 安全性檢查：確認當前使用者真的在這個房間裡，防止偷看別人對話
        FriendRoomMemberId id = new FriendRoomMemberId(roomId, currentUserId);

        // 💡 修正：改用 findById 把成員實體抓出來
        FriendRoomMember member = friendRoomMemberRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("您不是該聊天室的成員，無權查看歷史訊息"));

        // 💡 檢查該成員是否已經退群
        if (member.getLeftAt() != null) {
            throw new RuntimeException("您已離開該聊天室，無權查看歷史訊息");
        }

        // 撈出排序好的歷史訊息
        return friendMessageRepository.findByFriendRoom_FriendRoomIdOrderByCreatedAtAsc(roomId);
    }

    // 4. 建立多人聊天室 (群組)
    @Transactional
    public FriendRoom createGroupRoom(Integer currentUserId, String roomName, List<Integer> targetUserIds) {
        if (targetUserIds == null || targetUserIds.isEmpty()) {
            throw new RuntimeException("必須至少邀請一位好友加入群組");
        }

        Member currentUser = memberRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("找不到發起人"));

        // 建立群組房間實體 (GROUP)
        FriendRoom newRoom = FriendRoom.builder()
                .roomType("GROUP")
                .roomName(roomName != null ? roomName : "未命名群組")
                .creator(currentUser)
                .build();
        newRoom = friendRoomRepository.save(newRoom);

        // 將自己加入成員名單
        FriendRoomMember creatorMember = FriendRoomMember.builder()
                .friendRoom(newRoom)
                .member(currentUser)
                .build();
        friendRoomMemberRepository.save(creatorMember);

        // 將受邀者加入成員名單
        for (Integer targetUserId : targetUserIds) {
            Member targetUser = memberRepository.findById(targetUserId)
                    .orElseThrow(() -> new RuntimeException("找不到受邀的使用者 ID: " + targetUserId));

            FriendRoomMember groupMember = FriendRoomMember.builder()
                    .friendRoom(newRoom)
                    .member(targetUser)
                    .build();
            friendRoomMemberRepository.save(groupMember);
        }

        return newRoom;
    }

    // 5. 邀請成員加入已有的群組
    @Transactional
    public void inviteToGroup(Integer currentUserId, Integer roomId, List<Integer> newMemberIds) {
        // 確認房間存在，且是 GROUP 類型
        FriendRoom room = friendRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("找不到該聊天室"));

        if (!"GROUP".equals(room.getRoomType())) {
            throw new RuntimeException("只能邀請成員加入群組聊天室");
        }

        // 確認邀請人自己是否還在該群組內
        FriendRoomMemberId currentMemberId = new FriendRoomMemberId(roomId, currentUserId);
        FriendRoomMember currentMember = friendRoomMemberRepository.findById(currentMemberId)
                .orElseThrow(() -> new RuntimeException("您不在該群組中，無權邀請他人"));

        if (currentMember.getLeftAt() != null) {
            throw new RuntimeException("您已離開該群組，無權邀請他人");
        }

        for (Integer newMemberId : newMemberIds) {
            Member newMember = memberRepository.findById(newMemberId)
                    .orElseThrow(() -> new RuntimeException("找不到受邀的使用者 ID: " + newMemberId));

            // 檢查是否已經在群組內
            FriendRoomMemberId newId = new FriendRoomMemberId(roomId, newMemberId);
            if (!friendRoomMemberRepository.existsById(newId)) {
                FriendRoomMember groupMember = FriendRoomMember.builder()
                        .friendRoom(room)
                        .member(newMember)
                        .build();
                friendRoomMemberRepository.save(groupMember);
            }
        }
    }

    // 6. 儲存聊天訊息 (WebSocket)
    @Transactional
    public ChatMessageDTO saveMessage(Integer roomId, Integer senderId, String content, String messageType) {
        if (content == null || content.trim().isEmpty()) {
            throw new RuntimeException("訊息內容不能為空");
        }

        // 1. 檢查發送者是否為該聊天室的有效成員
        FriendRoomMemberId memberKey = new FriendRoomMemberId(roomId, senderId);
        FriendRoomMember roomMember = friendRoomMemberRepository.findById(memberKey)
                .orElseThrow(() -> new RuntimeException("您不是該聊天室的成員，無法發送訊息"));

        if (roomMember.getLeftAt() != null) {
            throw new RuntimeException("您已離開該聊天室，無法發送訊息");
        }

        FriendRoom room = roomMember.getFriendRoom();
        Member sender = roomMember.getMember();

        // 2. 建立並儲存訊息實體
        FriendMessage message = FriendMessage.builder()
                .friendRoom(room)
                .sender(sender)
                .messageType(messageType != null ? messageType : "TEXT")
                .content(content)
                .build();

        FriendMessage savedMessage = friendMessageRepository.save(message);

        // 3. 轉換成 DTO 回傳，供後續 WebSocket 廣播使用
        return ChatMessageDTO.builder()
                .messageId(savedMessage.getFriendMessageId())
                .senderId(sender.getMemberId())
                .senderName(sender.getUsername())
                .avatarUrl(sender.getAvatarUrl())
                .content(savedMessage.getContent())
                .messageType(savedMessage.getMessageType())
                .createdAt(savedMessage.getCreatedAt())
                .build();
    }

    // 7. 找出「一對一聊天室」裡的對方 Member 實體
    public Member getDirectRoomTargetMember(Integer roomId, Integer currentUserId) {
        List<FriendRoomMember> members = friendRoomMemberRepository
                .findByFriendRoom_FriendRoomIdAndLeftAtIsNull(roomId);
        return members.stream()
                .map(FriendRoomMember::getMember)
                .filter(m -> !m.getMemberId().equals(currentUserId)) // 過濾掉自己，剩下的就是對方
                .findFirst()
                .orElse(null);
    }

    // 8. 退出聊天室 (更新 left_at 時間)
    @Transactional
    public void leaveRoom(Integer roomId, Integer currentUserId) {
        FriendRoomMemberId memberKey = new FriendRoomMemberId(roomId, currentUserId);
        FriendRoomMember roomMember = friendRoomMemberRepository.findById(memberKey)
                .orElseThrow(() -> new RuntimeException("您不在該聊天室中"));

        if (roomMember.getLeftAt() != null) {
            throw new RuntimeException("您已經離開過該聊天室了");
        }

        // 紀錄離開時間 (軟刪除)
        roomMember.setLeftAt(java.time.LocalDateTime.now());
        friendRoomMemberRepository.save(roomMember);
    }
}