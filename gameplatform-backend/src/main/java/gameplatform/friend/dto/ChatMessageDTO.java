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
public class ChatMessageDTO {
    private Long messageId;
    private Integer senderId;
    private String senderName;
    private String avatarUrl;
    private String content;
    private String messageType;
    private LocalDateTime createdAt;
}
