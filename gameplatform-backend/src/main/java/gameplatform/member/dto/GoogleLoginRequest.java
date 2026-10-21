package gameplatform.member.dto;

import lombok.Data;
import jakarta.validation.constraints.*;

@Data
public class GoogleLoginRequest {
    @NotBlank
    @Size(max = 12000)
    private String idToken;
    @NotBlank
    @Size(min = 16, max = 128)
    private String nonce;
}
