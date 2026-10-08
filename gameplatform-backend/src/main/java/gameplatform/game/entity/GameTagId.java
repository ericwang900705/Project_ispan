package gameplatform.game.entity;

import java.io.Serializable;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class GameTagId implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer gameId;

    private Integer tagId;
}