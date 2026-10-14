package gameplatform.support.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import gameplatform.support.model.Actor;
import gameplatform.support.model.PublisherModels.DecisionInput;
import gameplatform.support.service.PublisherService;

@RestController
@RequestMapping("/api/review/games")
public class GameReviewController {
    private final PublisherService service;
    public GameReviewController(PublisherService service) { this.service=service; }
    @GetMapping public Object list(@AuthenticationPrincipal Actor a,@RequestParam(defaultValue="PENDING") String status,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page) { return service.reviews(a,status,q,page); }
    @GetMapping("/{id}") public Object detail(@AuthenticationPrincipal Actor a,@PathVariable int id) { return service.reviewDetail(a,id); }
    @PutMapping("/{id}") public Object decide(@AuthenticationPrincipal Actor a,@PathVariable int id,@Valid @RequestBody DecisionInput body) { return service.decide(a,id,body); }
}
