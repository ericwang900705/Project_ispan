package gameplatform.support.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "customer_service_ticket_games")
@Getter @Setter @NoArgsConstructor
public class CustomerServiceTicketGame {
    @EmbeddedId
    private CustomerServiceTicketGameId id;

    @MapsId("ticketId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private CustomerServiceTicket ticket;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
