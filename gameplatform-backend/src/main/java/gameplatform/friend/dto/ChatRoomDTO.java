package gameplatform.friend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoomDTO {
    private Integer roomId;
    private String roomType;
    private String roomName;
    private String avatarUrl;
    private LocalDateTime joinedAt;
}
