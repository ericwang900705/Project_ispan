
package gameplatform.recommendation.entity;

import java.io.Serializable;
import java.util.Objects;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class MemberTagSearchStatId implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer memberId;
    private Integer tagId;

    public MemberTagSearchStatId(Integer memberId, Integer tagId) {
        this.memberId = memberId;
        this.tagId = tagId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof MemberTagSearchStatId other)) {
            return false;
        }

        return Objects.equals(memberId, other.memberId)
                && Objects.equals(tagId, other.tagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberId, tagId);
    }
}
