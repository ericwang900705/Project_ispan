package gameplatform.friend;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;
@Configuration
@EnableWebSocket
public class FriendSocketConfig implements WebSocketConfigurer {
 @org.springframework.beans.factory.annotation.Value("${app.websocket.allowed-origins:http://localhost:5173,http://127.0.0.1:5173,http://localhost:5174,http://127.0.0.1:5174,http://localhost:8080,http://127.0.0.1:8080}")
 private String[] origins;
 private final FriendSocket socket;
 public FriendSocketConfig(FriendSocket socket){this.socket=socket;}
 @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry){registry.addHandler(socket,"/ws/friends").setAllowedOrigins(origins);}
}
