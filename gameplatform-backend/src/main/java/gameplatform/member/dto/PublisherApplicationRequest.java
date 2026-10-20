package gameplatform.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

@Data
public class PublisherApplicationRequest {

    @NotBlank(message = "公司名稱不能為空")
    @Size(max = 100, message = "公司名稱長度不能超過 100 字元")
    private String companyName;

    @NotBlank(message = "統一編號不能為空")
    @Size(min = 8, max = 8, message = "統一編號必須為 8 碼")
    private String taxId;

    // 網址允許不填
    @URL(message = "公司網址格式不正確")
    private String websiteUrl;

    @NotBlank(message = "銀行名稱或代碼不能為空")
    @Size(max = 100, message = "銀行名稱長度不能超過 100 字元")
    private String bankName;
}