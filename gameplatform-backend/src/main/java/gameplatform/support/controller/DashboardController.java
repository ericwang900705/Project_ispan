package gameplatform.support.controller;

import gameplatform.support.model.Actor;
import gameplatform.support.service.DashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/management")
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public Object dashboard(Actor actor) {
        return service.overview(actor);
    }
}
