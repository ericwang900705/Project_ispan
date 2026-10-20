package gameplatform.friend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSendRequest {
    private Integer roomId;
    private Integer senderId;
    private String content;
    @Builder.Default
    private String messageType = "TEXT"; // 預設為文字訊息
}