package gameplatform.support.model;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import gameplatform.support.config.SupportActorResolver;
import gameplatform.support.service.JwtService;

@Component
public class EventHub {
    private final JwtService tokens;
    private final SupportActorResolver actors;
    private final JdbcTemplate db;

    public EventHub(JwtService tokens, SupportActorResolver actors, JdbcTemplate db) {
        this.tokens = tokens;
        this.actors = actors;
        this.db = db;
    }

    private record Client(Actor actor, Jwt jwt, SseEmitter emitter) {
    }

    private final CopyOnWriteArrayList<Client> clients = new CopyOnWriteArrayList<>();

    public void disconnect(String id) {
        clients.stream()
                .filter(c -> Objects.equals(c.jwt().getId(), id))
                .forEach(this::complete);
    }

    public SseEmitter subscribe(Actor actor, Jwt jwt) {
        if (actor == null)
            throw Problem.denied();

        var emitter = new SseEmitter(120_000L);
        var client = new Client(actor, jwt, emitter);
        clients.add(client);
        emitter.onCompletion(() -> clients.remove(client));
        emitter.onTimeout(() -> complete(client));
        emitter.onError(error -> clients.remove(client));
        send(client, "refresh");
        return emitter;
    }

    /**
     * 判斷目前帳號是否具有客服處理權限。
     * ADMIN / SUPPORT 保留原有權限；STAFF 必須有 TICKET_HANDLE。
     * 排程與交易提交後回呼不一定具有 SecurityContext，因此直接查資料庫。
     */
    private boolean canHandleTicket(Actor actor) {
        if (actor.canSupport())
            return true;
        if (!"STAFF".equals(actor.role()))
            return false;

        Integer count = db.queryForObject("""
                SELECT COUNT(*)
                FROM dbo.admin a
                JOIN dbo.admin_roles r ON r.admin_role_id = a.admin_role_id
                JOIN dbo.admin_user_permissions up ON up.admin_id = a.admin_id
                JOIN dbo.admin_permissions p ON p.permission_id = up.permission_id
                WHERE a.admin_id = ?
                  AND a.status = 'ACTIVE'
                  AND r.role_code = 'STAFF'
                  AND p.permission_code = 'TICKET_HANDLE'
                """, Integer.class, actor.id());
        return count != null && count > 0;
    }

    /**
     * 客服事件：客服人員及案件所屬會員接收。
     * 不可只比對 ID，因為會員與管理員使用不同的 ID 空間。
     */
    public void changed(int memberId) {
        Runnable emit = () -> clients.forEach(client -> {
            try {
                Actor actor = client.actor();
                if ((actor.isMember() && actor.id() == memberId)
                        || canHandleTicket(actor)) {
                    send(client, "refresh");
                }
            } catch (RuntimeException e) {
                // 權限查詢異常時不發送客服事件；其他訂閱者仍可繼續處理。
            }
        });
        afterCommitOrNow(emit);
    }

    /**
     * 一般通知依收件者類型及 ID 分流，不以 TICKET_HANDLE 限制。
     */
    public void notification(String recipientType, int recipientId) {
        Runnable emit = () -> clients.stream()
                .filter(c -> ("MEMBER".equals(recipientType)
                        ? c.actor().isMember()
                        : !c.actor().isMember())
                        && c.actor().id() == recipientId)
                .forEach(c -> send(c, "refresh"));
        afterCommitOrNow(emit);
    }

    private void afterCommitOrNow(Runnable emit) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    emit.run();
                }
            });
        } else {
            emit.run();
        }
    }

    /**
     * 身分比對不比較 password：資料庫驗證回傳的 Actor 密碼欄位為空字串。
     */
    private boolean sameIdentity(Actor original, Actor current) {
        return original.id() == current.id()
                && Objects.equals(original.role(), current.role())
                && Objects.equals(original.username(), current.username());
    }

    private void send(Client client, String event) {
        try {
            if (!tokens.active(client.jwt())) {
                complete(client);
                return;
            }

            Actor currentActor = actors.verifyActor(client.actor());
            if (!sameIdentity(client.actor(), currentActor)) {
                complete(client);
                return;
            }

            client.emitter().send(SseEmitter.event().name(event).data("{}"));
        } catch (Exception e) {
            complete(client);
        }
    }

    private void complete(Client client) {
        clients.remove(client);
        client.emitter().complete();
    }

    @Scheduled(fixedDelay = 15000)
    public void heartbeat() {
        clients.forEach(c -> send(c, "heartbeat"));
    }
}
