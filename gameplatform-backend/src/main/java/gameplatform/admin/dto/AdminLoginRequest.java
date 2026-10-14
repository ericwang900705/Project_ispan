package gameplatform.admin.dto;

import lombok.Data;

@Data
public class AdminLoginRequest {
    private String adminAccount;
    private String password;
}