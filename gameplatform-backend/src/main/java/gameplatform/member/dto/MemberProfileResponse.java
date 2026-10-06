package gameplatform.member.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class MemberProfileResponse {
    private Integer memberId;
    private String username;
    private String email;
    private LocalDateTime createdAt;
}
