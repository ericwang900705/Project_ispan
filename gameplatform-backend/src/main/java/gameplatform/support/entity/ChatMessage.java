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
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "support_message_id")
    private Integer supportMessageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "support_room_id", nullable = false)
    private ChatRoom chatRoom;

    @Column(name = "member_id")
    private Integer memberId;

    @Column(name = "admin_id")
    private Integer adminId;

    @Column(name = "sender_type", nullable = false, length = 10)
    private String senderType;

    @Column(name = "content", nullable = false, columnDefinition = "nvarchar(max)")
    private String content;

    @Column(name = "sent_at", nullable = false, updatable = false, columnDefinition = "datetime2")
    private LocalDateTime sentAt;

    @PrePersist
    private void beforeInsert() {
        if (sentAt == null) sentAt = LocalDateTime.now();
    }
}
