package gameplatform.game.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GameBuildRequest {

    private String version;
    private String fileUrl;
    private Long fileSize;
}