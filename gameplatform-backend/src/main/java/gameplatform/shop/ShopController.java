package gameplatform.shop;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import gameplatform.support.model.Actor;
@RestController
@RequestMapping("/api")
public class ShopController {
 final ShopService shop;
 public ShopController(ShopService shop){this.shop=shop;}
 public record CouponInput(Integer couponId){}
 @GetMapping("/cart/{memberId}") public Object cart(Actor a,@PathVariable int memberId){return shop.cart(a,memberId);}
 @PostMapping("/cart/{memberId}/add") public Object add(Actor a,@PathVariable int memberId,@RequestBody ShopService.Add input){return shop.add(a,memberId,input);}
 @PutMapping("/cart/{memberId}/coupon") public Object coupon(Actor a,@PathVariable int memberId,@RequestBody CouponInput input){return shop.apply(a,memberId,input.couponId());}
 @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
 @DeleteMapping("/cart-items/{memberId}/{itemId}") public void remove(Actor a,@PathVariable int memberId,@PathVariable int itemId){shop.remove(a,memberId,itemId);}
 @DeleteMapping("/cart/{memberId}") public Object clear(Actor a,@PathVariable int memberId){return shop.clear(a,memberId);}
 @GetMapping("/coupons/usable") public Object coupons(Actor a){return shop.coupons(a);}
 @GetMapping("/member-games/{memberId}") public Object library(Actor a,@PathVariable int memberId){return shop.library(a,memberId);}
 @GetMapping("/orders/{memberId}") public Object orders(Actor a,@PathVariable int memberId){return shop.orders(a,memberId);}
 @PostMapping("/orders/{memberId}/{orderId}/cancel") public Object cancel(Actor a,@PathVariable int memberId,@PathVariable int orderId){return shop.cancel(a,memberId,orderId);}
 @GetMapping("/reviews/game/{gameId}") public Object reviews(org.springframework.security.core.Authentication auth,@PathVariable int gameId){return shop.reviews(gameId,auth!=null && auth.getPrincipal() instanceof Actor a?a:null);}
 @PostMapping("/reviews/{memberId}/game/{gameId}") public Object review(Actor a,@PathVariable int memberId,@PathVariable int gameId,@RequestBody ShopService.Review input){return shop.saveReview(a,memberId,gameId,null,input);}
 @PutMapping("/reviews/{memberId}/{reviewId}") public Object updateReview(Actor a,@PathVariable int memberId,@PathVariable int reviewId,@RequestBody ShopService.Review input){return shop.updateReview(a,memberId,reviewId,input);}
 @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
 @DeleteMapping("/reviews/{memberId}/{reviewId}") public void deleteReview(Actor a,@PathVariable int memberId,@PathVariable int reviewId){shop.deleteReview(a,memberId,reviewId);}
}
