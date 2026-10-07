package gameplatform.game.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GameAuditRequest {

    private Integer adminId;
    private String comment;
}