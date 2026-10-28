package gameplatform.support.extension;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import gameplatform.support.controller.SupportContactController;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 公開求助 API 只回傳可使用的客服信箱，不需要會員識別或寄信密碼。 */
class SupportContactTests {
    @Test void configuredEmailIsAvailable(){assertEquals(Map.of("email","help@example.test","available",true),new SupportContactController("help@example.test").contact());}
    @Test void trimsConfiguration(){assertEquals("help@example.test",new SupportContactController("  help@example.test  ").contact().get("email"));}
    @Test void absentOrInvalidAddressIsNeverExposedAsAMailLink(){for(String value:new String[]{null,"","not-configured","help@example.test\r\nBcc:other@example.test","<script>"})assertEquals(Map.of("email","","available",false),new SupportContactController(value).contact());}
    @Test void publicHttpRequestDoesNotRequireAnActor() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new SupportContactController("help@example.test")).build();
        mvc.perform(get("/api/public/support-contact")).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("help@example.test")).andExpect(jsonPath("$.available").value(true)).andExpect(jsonPath("$.password").doesNotExist());
    }
    @Test void missingConfigurationHasAStableHttpResponse() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new SupportContactController("")).build();
        mvc.perform(get("/api/public/support-contact")).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("")).andExpect(jsonPath("$.available").value(false));
    }
}
