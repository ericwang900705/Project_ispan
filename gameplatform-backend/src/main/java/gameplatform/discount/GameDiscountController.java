package gameplatform.discount;

import gameplatform.support.model.Actor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** 發行商折扣管理。 */
@RestController
@RequestMapping("/api/workspace/publisher/discounts")
public class GameDiscountController {
    private final GameDiscountService service;
    public GameDiscountController(GameDiscountService service) { this.service = service; }
    @GetMapping public Object list(Actor actor) { return service.list(actor); }
    @PostMapping public Object create(Actor actor, @RequestBody CreateRequest request) { return service.create(actor, request); }
    public record CreateRequest(Integer gameId, Integer percent, String startAt) {}
}
