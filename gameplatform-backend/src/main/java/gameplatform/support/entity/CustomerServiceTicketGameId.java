package gameplatform.support.entity;

import java.io.Serializable;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class CustomerServiceTicketGameId implements Serializable {
    private static final long serialVersionUID = 1L;
    @Column(name = "ticket_id")
    private Integer ticketId;
    @Column(name = "game_id")
    private Integer gameId;
}
