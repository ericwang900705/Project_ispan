package gameplatform.support.model;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Host may forward these invalidation events through its own authenticated SSE/WebSocket. */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="aki.modules.enabled",havingValue="true")
public class EventHub {
    public record SupportChanged(int memberId) {}
    private final ApplicationEventPublisher publisher;
    public EventHub(ApplicationEventPublisher publisher) { this.publisher=publisher; }
    public void changed(int memberId) {
        Runnable emit=()->publisher.publishEvent(new SupportChanged(memberId));
        if(TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { emit.run(); }
            });
        else emit.run();
    }
}
