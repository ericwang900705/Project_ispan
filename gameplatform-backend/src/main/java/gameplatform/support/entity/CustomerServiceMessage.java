package gameplatform.support.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Setter
@Getter
@Entity
@Table(name = "customer_service_messages")
public class CustomerServiceMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Integer messageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private CustomerServiceTicket ticket;

    // 會員發言填 senderMemberId；客服發言時此欄為 null。
    @Column(name = "sender_member_id")
    private Integer senderMemberId;

    // 客服發言填 adminId；會員發言時此欄為 null。
    @Column(name = "admin_id")
    private Integer adminId;

    @Column(name = "sender_type", nullable = false, length = 10)
    private String senderType;

    @Column(name = "message_content", nullable = false, columnDefinition = "nvarchar(max)")
    private String messageContent;

    @Column(name = "sent_at", nullable = false, updatable = false, columnDefinition = "datetime2")
    private LocalDateTime sentAt;

    @PrePersist
    private void beforeInsert() {
        if (sentAt == null) sentAt = LocalDateTime.now();
    }
}
