package gameplatform.config;

import gameplatform.friend.service.OnlinePresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final OnlinePresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    public record PresenceMessage(Integer memberId, boolean isOnline) {
    }

    // 監聽 STOMP CONNECT 事件 (玩家上線)
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        // 從前端 STOMP 連線的 connectHeaders 中讀取 "memberId"
        String memberIdStr = accessor.getFirstNativeHeader("memberId");
        if (memberIdStr != null) {
            try {
                Integer memberId = Integer.parseInt(memberIdStr);
                boolean justCameOnline = presenceService.userConnected(memberId, sessionId);

                if (justCameOnline) {
                    log.info("會員上線: memberId={}", memberId);

                    // 廣播通知所有人：這位會員上線了
                    messagingTemplate.convertAndSend("/topic/presence", new PresenceMessage(memberId, true));
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    // 監聽 WebSocket 斷線事件 (玩家關閉網頁或點擊斷線)
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        Integer offlineMemberId = presenceService.userDisconnected(sessionId);
        if (offlineMemberId != null) {
            log.info("會員離線: memberId={}", offlineMemberId);

            // 廣播通知所有人：這位會員離線了
            messagingTemplate.convertAndSend("/topic/presence", new PresenceMessage(offlineMemberId, false));
        }
    }
}
