package gameplatform.shop;

import gameplatform.support.model.Actor;
import org.springframework.web.bind.annotation.*;

/** 退款專用後台 API；每項操作都由服務再次檢查 REFUND_MANAGE。 */
@RestController
@RequestMapping("/api/backoffice/refund-requests")
public class RefundController {
    private final RefundService service;
    public RefundController(RefundService service){this.service=service;}
    @GetMapping public Object list(Actor actor,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="0") int page){return service.list(actor,q,status,page);}
    @GetMapping("/{id}") public Object detail(Actor actor,@PathVariable int id){return service.detail(actor,id);}
    @PostMapping("/{id}/decision") public Object decide(Actor actor,@PathVariable int id,@RequestBody RefundService.Decision input){return service.decide(actor,id,input);}
}
