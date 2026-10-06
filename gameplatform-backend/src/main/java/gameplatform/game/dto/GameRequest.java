package gameplatform.game.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GameRequest {

    private Integer memberId;

    private String gameName;

    private String coverUrl;

    private LocalDateTime releaseDate;

    private BigDecimal price;

    private String description;
}