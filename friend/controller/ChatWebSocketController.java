package gameplatform.friend.controller;

import gameplatform.friend.dto.ChatMessageDTO;
import gameplatform.friend.dto.ChatSendRequest;
import gameplatform.friend.service.ChatRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatRoomService chatRoomService;
    private final SimpMessagingTemplate messagingTemplate;

    // 前端發送目標 (Destination): /app/chat.send
    // 後端廣播目標 (Subscription): /topic/room/{roomId}

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload ChatSendRequest request) {
        // 1. 將訊息寫入資料庫，並取得封裝好的 DTO
        ChatMessageDTO savedMessageDto = chatRoomService.saveMessage(
                request.getRoomId(),
                request.getSenderId(),
                request.getContent(),
                request.getMessageType());

        // 2. 將訊息即時廣播「/topic/room/{roomId}」的所有在線成員
        String destination = "/topic/room/" + request.getRoomId();
        messagingTemplate.convertAndSend(destination, savedMessageDto);
    }
}
