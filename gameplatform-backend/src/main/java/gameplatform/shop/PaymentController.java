package gameplatform.shop;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import gameplatform.support.model.Actor;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {
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
        String status = payment.callback(input, req.getRequestURI().endsWith("/info-redirect"));
        return ResponseEntity.status(303).location(payment.redirect(status, input.get("MerchantTradeNo"))).build();
    }
}
