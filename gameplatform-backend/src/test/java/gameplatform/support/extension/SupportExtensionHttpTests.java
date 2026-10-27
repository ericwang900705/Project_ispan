package gameplatform.support.extension;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import gameplatform.support.controller.SupportExtensionController;
import gameplatform.support.model.*;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.core.MethodParameter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;

/** 驗證實際 Spring 路由、JSON 輸入與錯誤狀態；資料隔離另由 JDBC 測試驗證。 */
class SupportExtensionHttpTests {

  SupportExtensionService service;
  MockMvc mvc;
  final Actor member = new Actor(1, "MEMBER", "Aki", "");

  @BeforeEach
  void setup() {
    service = mock(SupportExtensionService.class);
    var actor = new HandlerMethodArgumentResolver() {
      public boolean supportsParameter(MethodParameter p) {
        return p.getParameterType() == Actor.class;
      }

      public Object resolveArgument(
        MethodParameter p,
        ModelAndViewContainer m,
        NativeWebRequest r,
        WebDataBinderFactory b
      ) {
        return member;
      }
    };
    mvc = MockMvcBuilders.standaloneSetup(
      new SupportExtensionController(service, mock(JdbcTemplate.class))
    )
      .setCustomArgumentResolvers(actor)
      .setControllerAdvice(new Problem.Handler())
      .build();
  }

  @Test
  void optionsMapBothKindsAndOwnerScope() throws Exception {
    mvc
      .perform(get("/api/rooms/12/order-options?q=game&page=2"))
      .andExpect(status().isOk());
    verify(service).options(member, "rooms", 12, "orders", "game", 2);
    mvc
      .perform(get("/api/tickets/34/coupon-options"))
      .andExpect(status().isOk());
    verify(service).options(member, "tickets", 34, "coupons", "", 0);
  }

  @Test
  void linkAndDeleteUseStructuredIds() throws Exception {
    mvc
      .perform(
        post("/api/rooms/12/order-links")
          .contentType("application/json")
          .content("{\"orderId\":100}")
      )
      .andExpect(status().isOk());
    verify(service).link(member, "rooms", 12, "order", 100, false);
    mvc
      .perform(delete("/api/tickets/34/coupon-links/8"))
      .andExpect(status().isNoContent());
    verify(service).link(member, "tickets", 34, "coupon", 8, true);
  }

  @Test
  void missingLinkIdIsBadRequest() throws Exception {
    mvc
      .perform(
        post("/api/rooms/12/coupon-links")
          .contentType("application/json")
          .content("{}")
      )
      .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void privatePermissionDenialKeeps403() throws Exception {
    when(service.notes(member, "rooms", 12, Long.MAX_VALUE)).thenThrow(
      new Problem(403, "未授權使用客服私人資料")
    );
    mvc
      .perform(get("/api/admin/support/rooms/12/notes"))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.message").value("未授權使用客服私人資料"));
  }

  @Test
  void noteCreationReturns201() throws Exception {
    when(service.addNote(member, "tickets", 34, "internal")).thenReturn(
      Map.of("noteId", "1")
    );
    mvc
      .perform(
        post("/api/admin/support/tickets/34/notes")
          .contentType("application/json")
          .content("{\"content\":\"internal\"}")
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.noteId").value("1"));
  }

  @Test
  void faqAndTemplateRoutesAreDistinct() throws Exception {
    mvc.perform(get("/api/public/faqs")).andExpect(status().isOk());
    verify(service).content(null, "faqs", false, "", null, null, 0);
    mvc
      .perform(get("/api/admin/support/reply-templates?status=INACTIVE"))
      .andExpect(status().isOk());
    verify(service).content(
      member,
      "reply-templates",
      true,
      "",
      "INACTIVE",
      null,
      0
    );
  }
}
