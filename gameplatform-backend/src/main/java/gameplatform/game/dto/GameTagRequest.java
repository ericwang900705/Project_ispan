
package gameplatform.game.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GameTagRequest {

    private List<Integer> tagIds;
}