package gameplatform.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
    @Pattern(regexp = "[0-9]{8}", message = "統一編號必須為 8 位數字")
    private String taxId;

    // 網址允許不填
    @URL(message = "公司網址格式不正確")
    @Size(max = 255, message = "公司網址最多 255 字元")
    private String websiteUrl;

    @NotBlank(message = "銀行名稱或代碼不能為空")
    @Size(max = 100, message = "銀行名稱長度不能超過 100 字元")
    private String bankName;

    @NotBlank(message = "收款銀行帳號不能為空")
    @Size(max = 30, message = "銀行帳號長度不能超過 30 字元")
    @Pattern(regexp = "^[0-9\\-]+$", message = "銀行帳號只能包含數字與連字號")
    private String bankAccount;
}
