package gameplatform.shop;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import gameplatform.support.model.Actor;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PaymentController.class);
    final PaymentService payment;
    final ShopService shop;

    public PaymentController(PaymentService p, ShopService s) {
        payment = p;
        shop = s;
    }

    public record Checkout(String choosePayment) {
    }

    @GetMapping("/config")
    public Object config() {
        return Map.of("enabled", payment.available(), "testMode", payment.testMode(),
                "publicCallback", payment.publicCallback());
    }

    @PostMapping("/{memberId}/checkout")
    public Object checkout(Actor a, @PathVariable int memberId, @RequestBody Checkout input) {
        return payment.checkout(a, memberId, input.choosePayment() == null ? "" : input.choosePayment());
    }

    @PostMapping("/{memberId}/orders/{orderId}/pay")
    public Object resume(Actor actor, @PathVariable int memberId, @PathVariable int orderId) {
        return payment.resume(actor, memberId, orderId);
    }

    @PostMapping("/{memberId}/orders/{orderId}/sync")
    public Object sync(Actor actor,@PathVariable int memberId,@PathVariable int orderId) {
        return payment.sync(actor,memberId,orderId);
    }

    @GetMapping("/{memberId}/orders/{tradeNo}")
    public Object order(Actor a, @PathVariable int memberId, @PathVariable String tradeNo) {
        return shop.paymentOrder(a, memberId, tradeNo);
    }

    @PostMapping(value = { "/notify",
            "/info" }, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> notify(@RequestParam Map<String, String> input,
            jakarta.servlet.http.HttpServletRequest req) {
        payment.callback(input, req.getRequestURI().endsWith("/info"));
        return ResponseEntity.ok("1|OK");
    }

    @PostMapping(value = { "/result", "/info-redirect" }, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> result(@RequestParam Map<String, String> input,
            jakarta.servlet.http.HttpServletRequest req) {
        String trade = input.get("MerchantTradeNo");
        if (trade != null && !trade.matches("[A-Za-z0-9]{1,30}")) trade = null;
        String status;
        try {
            status = payment.callback(input, req.getRequestURI().endsWith("/info-redirect"));
        } catch (RuntimeException e) {
            // 瀏覽器回傳失敗只導向待核對狀態；保留 notify 的錯誤回應，讓綠界可重送。
            log.warn("付款瀏覽器回傳未完成，交易編號 {}，原因 {}", trade, e.toString());
            status = "unverified";
        }
        return ResponseEntity.status(303).location(payment.redirect(status, trade)).build();
    }
}
