package gameplatform.member.dto;

import lombok.Data;
import java.time.LocalDate;

import org.hibernate.validator.constraints.URL;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

@Data
public class UpdateProfileRequest {
    @Past(message = "生日必須是過去的日期")
    private LocalDate birthDate;
    @Size(max = 500, message = "地址長度不能超過 500 個字元")
    private String address;
    @Size(max = 500, message = "大頭貼網址長度不能超過 500 個字元")
    @URL(message = "大頭貼必須是有效的網址格式")
    private String avatarUrl;
}
