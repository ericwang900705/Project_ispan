package gameplatform.friend;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import gameplatform.support.config.SupportActorResolver;
import gameplatform.support.service.JwtService;
import gameplatform.support.model.Actor;
import tools.jackson.databind.ObjectMapper;

/**
 * Per-member invalidation stream. No public room subscriptions; no JWTs in
 * URLs.
 */
@Component
public class FriendSocket extends TextWebSocketHandler {
    private final SupportActorResolver actors;
    private final JwtService tokens;
    private final ObjectMapper json = new ObjectMapper();

    private record Client(WebSocketSession session, Instant opened, String jwt, Actor actor) {
    }

    private final Map<String, Client> clients = new ConcurrentHashMap<>();

    public FriendSocket(JwtService tokens, SupportActorResolver actors) {
        this.tokens = tokens;
        this.actors = actors;
    }

    private Actor resolveToken(String raw) {
        return actors.verifyActor(tokens.actor(tokens.decoder().decode(raw)));
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.setTextMessageSizeLimit(8192);
        clients.put(session.getId(), new Client(session, Instant.now(), null, null));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        var c = clients.get(session.getId());
        if (c == null)
            return;
        try {
            var node = json.readTree(message.getPayload());
            if (c.actor() == null) {
                if (!"auth".equals(node.path("type").asText())) {
                    close(c);
                    return;
                }
                String jwt = node.path("token").asText();
                Actor actor = resolveToken(jwt);
                FriendService.member(actor);
                c = new Client(session, c.opened(), jwt, actor);
                clients.put(session.getId(), c);
                write(c, "{\"type\":\"ready\"}");
                return;
            }
            if (!valid(c)) {
                close(c);
                return;
            }
            if ("ping".equals(node.path("type").asText()))
                write(c, "{\"type\":\"pong\"}");
            else
                close(c);
        } catch (Exception e) {
            close(c);
        }
    }

    private boolean valid(Client c) {
        if (c.actor() == null)
            return false;
        try {
            return resolveToken(c.jwt()).equals(c.actor());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void write(Client c, String text) {
        try {
            synchronized (c.session()) {
                if (c.session().isOpen())
                    c.session().sendMessage(new TextMessage(text));
            }
        } catch (Exception e) {
            close(c);
        }
    }

    private void close(Client c) {
        clients.remove(c.session().getId());
        try {
            c.session().close(CloseStatus.POLICY_VIOLATION);
        } catch (Exception ignored) {
        }
    }

    public boolean online(int id) {
        return clients.values().stream().anyMatch(c -> valid(c) && c.actor().id() == id && c.session().isOpen());
    }

    public void publish(Collection<Integer> ids, String type, int room) {
        String text = "{\"type\":\"" + type + "\",\"roomId\":" + room + "}";
        for (var c : clients.values())
            if (c.actor() != null && ids.contains(c.actor().id())) {
                if (valid(c))
                    write(c, text);
                else
                    close(c);
            }
    }

    @Scheduled(fixedDelay = 5000)
    public void expire() {
        for (var c : clients.values())
            if (c.actor() == null ? c.opened().plusSeconds(10).isBefore(Instant.now()) : !valid(c))
                close(c);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        clients.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable error) {
        var c = clients.get(session.getId());
        if (c != null)
            close(c);
    }
}
