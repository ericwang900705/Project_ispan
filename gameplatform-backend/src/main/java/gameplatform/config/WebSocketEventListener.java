
package gameplatform.config;

import gameplatform.friend.service.OnlinePresenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@Component
public class WebSocketEventListener {

    private final OnlinePresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    // 手動建立建構子
    public WebSocketEventListener(
            OnlinePresenceService presenceService,
            SimpMessagingTemplate messagingTemplate) {

        this.presenceService = presenceService;
        this.messagingTemplate = messagingTemplate;
    }

    public record PresenceMessage(Integer memberId, boolean isOnline) {
    }

    // 玩家建立 STOMP 連線
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        String sessionId = accessor.getSessionId();
        String memberIdStr = accessor.getFirstNativeHeader("memberId");

        if (memberIdStr != null) {
            try {
                Integer memberId = Integer.parseInt(memberIdStr);

                boolean justCameOnline = presenceService.userConnected(memberId, sessionId);

                if (justCameOnline) {
                    log.info("會員上線: memberId={}", memberId);

                    messagingTemplate.convertAndSend(
                            "/topic/presence",
                            new PresenceMessage(memberId, true));
                }

            } catch (NumberFormatException ignored) {
            }
        }
    }

    // 玩家斷線
    @EventListener
    public void handleWebSocketDisconnectListener(
            SessionDisconnectEvent event) {

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        String sessionId = accessor.getSessionId();

        Integer offlineMemberId = presenceService.userDisconnected(sessionId);

        if (offlineMemberId != null) {
            log.info("會員離線: memberId={}", offlineMemberId);

            messagingTemplate.convertAndSend(
                    "/topic/presence",
                    new PresenceMessage(offlineMemberId, false));
        }
    }
}
