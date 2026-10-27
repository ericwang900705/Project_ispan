package gameplatform.shop;

import org.springframework.web.bind.annotation.*;
import gameplatform.support.model.Actor;

/** 沿用 cart 領券中心／我的優惠券功能，會員身分由登入狀態取得。 */
@RestController
@RequestMapping("/api/coupons")
public class CouponController {
    private final CouponService service;
    public CouponController(CouponService service) { this.service=service; }
    @GetMapping("/center") public Object center(Actor a) { return service.center(a); }
    @GetMapping("/mine") public Object mine(Actor a) { return service.mine(a); }
    @PostMapping("/{couponId}/claim") public Object claim(Actor a,@PathVariable int couponId) {
        service.claim(a,couponId);return java.util.Map.of("message","領取成功");
    }
}
