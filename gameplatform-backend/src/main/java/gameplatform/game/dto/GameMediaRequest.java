package gameplatform.game.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GameMediaRequest {

    private String mediaType;

    private String mediaUrl;

    private Integer displayOrder;
}