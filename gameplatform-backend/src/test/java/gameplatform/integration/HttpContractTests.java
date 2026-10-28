package gameplatform.integration;

import gameplatform.friend.*;
import gameplatform.shop.*;
import gameplatform.game.controller.GameController;
import gameplatform.game.service.GameService;
import gameplatform.support.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.data.domain.Pageable;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HttpContractTests {
 private final HandlerMethodArgumentResolver actor=new HandlerMethodArgumentResolver() {
  public boolean supportsParameter(MethodParameter p){return p.getParameterType()==Actor.class;}
  public Object resolveArgument(MethodParameter p,ModelAndViewContainer m,NativeWebRequest r,WebDataBinderFactory b){return new Actor(7,"MEMBER","aki","");}
 };
 @Test void shopPermissionFailureKeepsHttpStatusAndReadableMessage() throws Exception {
  var service=mock(ShopService.class);when(service.coupons(any())).thenThrow(new Problem(403,"只有會員可操作"));
  var mvc=MockMvcBuilders.standaloneSetup(new ShopController(service)).setCustomArgumentResolvers(actor).setControllerAdvice(new Problem.Handler()).build();
  mvc.perform(get("/api/coupons/usable")).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("只有會員可操作"));
 }
 @Test void friendAccessFailureDoesNotTurnIntoServerError() throws Exception {
  var service=mock(FriendService.class);when(service.rooms(any())).thenThrow(new Problem(403,"你已離開此聊天室"));
  var mvc=MockMvcBuilders.standaloneSetup(new FriendController(service)).setCustomArgumentResolvers(actor).setControllerAdvice(new Problem.Handler()).build();
  mvc.perform(get("/api/chat/rooms")).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("你已離開此聊天室"));
 }
 @Test void advancedSearchDefaultSortIsAccepted() throws Exception {
  var service=mock(GameService.class);var mvc=MockMvcBuilders.standaloneSetup(new GameController(service)).setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver()).build();
  mvc.perform(get("/api/public/games/advanced-search")).andExpect(status().isOk());
  var page=ArgumentCaptor.forClass(Pageable.class);
  verify(service).advancedSearch(isNull(),isNull(),isNull(),isNull(),isNull(),isNull(),isNull(),isNull(),page.capture());
  assertNotNull(page.getValue().getSort().getOrderFor("releaseDate"));
 }
 @Test void recommendationCannotReadAnotherMembersPrivatePurchases() throws Exception {
  var service=mock(gameplatform.recommendation.service.AiGameRecommendationService.class);
  var refresh=mock(gameplatform.recommendation.service.AiRecommendationRefreshService.class);
  var controller=new gameplatform.recommendation.controller.AiGameRecommendationController(service,refresh);
  var mvc=MockMvcBuilders.standaloneSetup(controller).setCustomArgumentResolvers(actor).setControllerAdvice(new Problem.Handler()).build();
  mvc.perform(get("/api/recommendations/members/8/purchased-games")).andExpect(status().isForbidden());verifyNoInteractions(service,refresh);
  mvc.perform(get("/api/recommendations/members/7/purchased-games")).andExpect(status().isOk());verify(service).findPurchasedGames(7);
 }
}
