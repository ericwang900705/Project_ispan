package gameplatform.member.dto;

import lombok.Data;

@Data
public class GoogleLoginRequest {
    private String idToken; // 前端從 Google 那邊拿到的 Token
}
