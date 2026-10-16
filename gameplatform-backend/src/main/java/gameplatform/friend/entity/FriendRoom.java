package gameplatform.friend.entity;

import java.time.LocalDateTime;

import gameplatform.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "friend_rooms")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FriendRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "friend_room_id")
    private Integer friendRoomId;

    // 'DIRECT' (一對一), 'GROUP' (多人)
    @Column(name = "room_type", nullable = false, length = 20)
    @Builder.Default
    private String roomType = "DIRECT";

    // 房間名稱 (多人聊天室才需要，一對一通常顯示對方名字)
    @Column(name = "room_name", length = 100)
    private String roomName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_member_id", nullable = false)
    private Member creator;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
