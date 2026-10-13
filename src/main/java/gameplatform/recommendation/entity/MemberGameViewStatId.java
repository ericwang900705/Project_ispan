package gameplatform.recommendation.entity;

import java.io.Serializable;
import java.util.Objects;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class MemberGameViewStatId implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer memberId;
    private Integer gameId;

    public MemberGameViewStatId(Integer memberId, Integer gameId) {
        this.memberId = memberId;
        this.gameId = gameId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof MemberGameViewStatId other)) {
            return false;
        }

        return Objects.equals(memberId, other.memberId)
                && Objects.equals(gameId, other.gameId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberId, gameId);
    }
}
