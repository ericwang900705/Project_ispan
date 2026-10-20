package gameplatform.backoffice;

import gameplatform.support.model.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/backoffice")
public class BackofficeController {
    private final BackofficeService service;

    @GetMapping("/{area}")
    public Object list(Actor a, @PathVariable String area, @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String status, @RequestParam(defaultValue = "0") int page) {
        return service.list(a, area, q, status, page);
    }

    @GetMapping("/orders/{id}")
    public Object order(Actor a, @PathVariable int id) {
        return service.order(a, id, false);
    }

    @GetMapping("/refunds/{id}")
    public Object refund(Actor a, @PathVariable int id) {
        return service.order(a, id, true);
    }

    @PostMapping("/tags")
    public Object tag(Actor a, @RequestBody BackofficeService.TagInput input) {
        service.saveTag(a, null, input);
        return ok();
    }

    @PutMapping("/tags/{id}")
    public Object tag(Actor a, @PathVariable int id, @RequestBody BackofficeService.TagInput input) {
        service.saveTag(a, id, input);
        return ok();
    }

    @DeleteMapping("/tags/{id}")
    public Object deleteTag(Actor a, @PathVariable int id) {
        service.deleteTag(a, id);
        return ok();
    }

    @PostMapping("/coupons")
    public Object coupon(Actor a, @RequestBody BackofficeService.CouponInput input) {
        service.saveCoupon(a, null, input);
        return ok();
    }

    @PutMapping("/coupons/{id}")
    public Object coupon(Actor a, @PathVariable int id, @RequestBody BackofficeService.CouponInput input) {
        service.saveCoupon(a, id, input);
        return ok();
    }

    @PatchMapping("/reviews/{id}")
    public Object review(Actor a, @PathVariable int id, @RequestBody BackofficeService.ReviewInput input) {
        service.moderate(a, id, input);
        return ok();
    }

    @PostMapping("/review-reports/{id}/decision")
    public Object report(Actor a, @PathVariable int id, @RequestBody BackofficeService.ReportInput input) {
        service.report(a, id, input);
        return ok();
    }

    private Object ok() {
        return Map.of("message", "已儲存");
    }
}
