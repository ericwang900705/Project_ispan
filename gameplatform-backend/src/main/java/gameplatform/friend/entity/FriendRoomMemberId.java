package gameplatform.friend.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendRoomMemberId implements Serializable {
    private Integer friendRoom;
    private Integer member;

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        FriendRoomMemberId that = (FriendRoomMemberId) o;
        return Objects.equals(friendRoom, that.friendRoom) && Objects.equals(member, that.member);
    }

    @Override
    public int hashCode() {
        return Objects.hash(friendRoom, member);
    }
}
