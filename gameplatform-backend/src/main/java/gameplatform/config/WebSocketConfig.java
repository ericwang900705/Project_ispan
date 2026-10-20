package gameplatform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

// Legacy adapter kept for reference; active integration uses FriendController + /ws/friends.
// Legacy STOMP broker is not registered.
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 1. 設定前端連線的端點網址為：ws://localhost:8080/ws
        // 2. setAllowedOriginPatterns("*") 允許跨域連線，方便前端或測試工具連入
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 1. 啟用簡單的記憶體訊息代理 (Message Broker)
        // 當後端要把訊息廣播給前端時，路徑開頭會是 "/topic" (例如：/topic/room/1)
        registry.enableSimpleBroker("/topic");

        // 2. 設定前端發送訊息給後端時的路徑前綴
        // 前端傳訊息時，路徑開頭會是 "/app" (例如：/app/chat.send)
        registry.setApplicationDestinationPrefixes("/app");
    }
}
