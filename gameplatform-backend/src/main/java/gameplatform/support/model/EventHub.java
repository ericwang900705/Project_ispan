package gameplatform.support.model;

import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.Objects;

import gameplatform.support.config.SupportActorResolver;
import gameplatform.support.service.JwtService;

@Component
public class EventHub {
    private final JwtService tokens;
    private final SupportActorResolver actors;

    public EventHub(
            JwtService tokens,
            SupportActorResolver actors) {

        this.tokens = tokens;
        this.actors = actors;
    }

    public void disconnect(String id) {
        clients.stream().filter(c -> c.jwt.getId().equals(id)).forEach(c -> {
            clients.remove(c);
            c.emitter.complete();
        });
    }

    private record Client(Actor actor, Jwt jwt, SseEmitter emitter) {
    }

    private final CopyOnWriteArrayList<Client> clients = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe(Actor actor, Jwt jwt) {
        if (actor == null)
            throw Problem.denied();
        var emitter = new SseEmitter(120_000L);
        var c = new Client(actor, jwt, emitter);
        clients.add(c);
        emitter.onCompletion(() -> clients.remove(c));
        emitter.onTimeout(() -> {
            clients.remove(c);
            emitter.complete();
        });
        emitter.onError(e -> clients.remove(c));
        send(c, "refresh");
        return emitter;
    }

    public void changed(int memberId) {
        Runnable emit = () -> clients.stream().filter(c -> c.actor.canSupport() || c.actor.id() == memberId)
                .forEach(c -> send(c,
                        "refresh"));
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    emit.run();
                }
            });
        else
            emit.run();
    }

    public void notification(String recipientType, int recipientId) {
        Runnable emit = () -> clients.stream()
                .filter(c -> (recipientType.equals("MEMBER")
                        ? (c.actor.isMember() || c.actor.role().equals("PUBLISHER"))
                        : !(c.actor.isMember() || c.actor.role().equals("PUBLISHER")))
                        && c.actor.id() == recipientId)
                .forEach(c -> send(c, "refresh"));
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    emit.run();
                }
            });
        else
            emit.run();
    }

    private void send(Client c, String event) {
        try {

            // 1. 檢查 JWT 是否仍然有效
            if (!tokens.active(c.jwt())) {
                clients.remove(c);
                c.emitter.complete();
                return;
            }

            // 2. 重新查詢 SQL Server，確認使用者身分
            Actor currentActor = actors.verifyActor(c.actor());

            // 3. 比較目前身分與訂閱時的身分
            if (!Objects.equals(currentActor, c.actor())) {
                clients.remove(c);
                c.emitter.complete();
                return;
            }

            // 4. 身分有效，發送 SSE 事件
            c.emitter.send(
                    SseEmitter.event()
                            .name(event)
                            .data("{}"));

        } catch (Exception e) {
            clients.remove(c);
            c.emitter.complete();
        }
    }

    @Scheduled(fixedDelay = 15000)
    public void heartbeat() {
        clients.forEach(c -> send(c, "heartbeat"));
    }
}
