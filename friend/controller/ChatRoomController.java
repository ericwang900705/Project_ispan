package gameplatform.friend.controller;

import gameplatform.friend.dto.ChatMessageDTO;
import gameplatform.friend.dto.ChatRoomDTO;
import gameplatform.friend.entity.FriendMessage;
import gameplatform.friend.entity.FriendRoom;
import gameplatform.friend.entity.FriendRoomMember;
import gameplatform.friend.service.ChatRoomService;
import gameplatform.member.entity.Member;
import gameplatform.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final MemberRepository memberRepository;

    // 取得當前會員 ID 的輔助方法
    private Integer getCurrentMemberId(Authentication authentication) {
        String username = authentication.getName();
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("找不到當前登入的帳號: " + username));
        return member.getMemberId();
    }

    // 1. 取得或建立一對一聊天室
    @PostMapping("/direct/{targetUserId}")
    public ResponseEntity<ChatRoomDTO> getOrCreateDirectRoom(
            @PathVariable Integer targetUserId,
            Authentication authentication) {

        Integer currentUserId = getCurrentMemberId(authentication);
        FriendRoom room = chatRoomService.getOrCreateDirectRoom(currentUserId, targetUserId);

        // 目標會員的暱稱當作房間名稱
        Member targetUser = memberRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("找不到目標使用者"));

        ChatRoomDTO dto = ChatRoomDTO.builder()
                .roomId(room.getFriendRoomId())
                .roomType(room.getRoomType())
                .roomName(targetUser.getUsername()) // 顯示對方的名字
                .avatarUrl(targetUser.getAvatarUrl())// 顯示對方的大頭貼
                .build();

        return ResponseEntity.ok(dto);
    }

    // 2. 取得我的聊天室列表
    @GetMapping("/rooms")
    public ResponseEntity<List<ChatRoomDTO>> getMyRooms(Authentication authentication) {
        Integer currentUserId = getCurrentMemberId(authentication);
        List<FriendRoomMember> myRooms = chatRoomService.getMyRooms(currentUserId);

        List<ChatRoomDTO> dtoList = myRooms.stream().map(frm -> {
            FriendRoom room = frm.getFriendRoom();
            String displayRoomName = room.getRoomName();
            String displayAvatar = null;

            // 一對一聊天，找出對方的資料來當房間名稱跟大頭貼
            if ("DIRECT".equals(room.getRoomType())) {
                displayRoomName = "與好友的對話 (Room " + room.getFriendRoomId() + ")";
            }

            return ChatRoomDTO.builder()
                    .roomId(room.getFriendRoomId())
                    .roomType(room.getRoomType())
                    .roomName(displayRoomName)
                    .avatarUrl(displayAvatar)
                    .joinedAt(frm.getJoinedAt())
                    .build();
        }).collect(Collectors.toList());

        return ResponseEntity.ok(dtoList);
    }

    // 3. 取得聊天室歷史對話
    // 呼叫範例: GET /api/chat/rooms/1/history
    @GetMapping("/rooms/{roomId}/history")
    public ResponseEntity<List<ChatMessageDTO>> getRoomHistory(
            @PathVariable Integer roomId,
            Authentication authentication) {

        Integer currentUserId = getCurrentMemberId(authentication);
        List<FriendMessage> history = chatRoomService.getRoomHistory(roomId, currentUserId);

        List<ChatMessageDTO> dtoList = history.stream().map(msg -> ChatMessageDTO.builder()
                .messageId(msg.getFriendMessageId())
                .senderId(msg.getSender().getMemberId())
                .senderName(msg.getSender().getUsername())
                .avatarUrl(msg.getSender().getAvatarUrl())
                .content(msg.getContent())
                .messageType(msg.getMessageType())
                .createdAt(msg.getCreatedAt())
                .build()).collect(Collectors.toList());

        return ResponseEntity.ok(dtoList);
    }

    // 4. 建立多人聊天室
    @PostMapping("/group")
    public ResponseEntity<ChatRoomDTO> createGroupRoom(
            @RequestParam(required = false) String roomName,
            @RequestBody List<Integer> targetUserIds,
            Authentication authentication) {

        Integer currentUserId = getCurrentMemberId(authentication);
        FriendRoom room = chatRoomService.createGroupRoom(currentUserId, roomName, targetUserIds);

        ChatRoomDTO dto = ChatRoomDTO.builder()
                .roomId(room.getFriendRoomId())
                .roomType(room.getRoomType())
                .roomName(room.getRoomName())
                .build();

        return ResponseEntity.ok(dto);
    }

    // 5. 邀請好友加入群組
    @PostMapping("/rooms/{roomId}/invite")
    public ResponseEntity<String> inviteToGroup(
            @PathVariable Integer roomId,
            @RequestBody List<Integer> newMemberIds,
            Authentication authentication) {

        Integer currentUserId = getCurrentMemberId(authentication);
        chatRoomService.inviteToGroup(currentUserId, roomId, newMemberIds);
        return ResponseEntity.ok("已成功邀請成員加入群組");
    }

    // 6. 退出聊天室
    @PatchMapping("/rooms/{roomId}/leave")
    public ResponseEntity<String> leaveRoom(
            @PathVariable Integer roomId,
            Authentication authentication) {

        Integer currentUserId = getCurrentMemberId(authentication);
        chatRoomService.leaveRoom(roomId, currentUserId);
        return ResponseEntity.ok("已成功退出聊天室");
    }
}