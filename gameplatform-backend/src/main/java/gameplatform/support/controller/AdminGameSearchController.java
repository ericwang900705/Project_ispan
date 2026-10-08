package gameplatform.support.controller;

import gameplatform.support.model.Actor;
import gameplatform.support.service.AdminGameSearchService;
import gameplatform.support.service.PublisherService;
import gameplatform.support.model.PublisherModels.RequestInput;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import gameplatform.support.integration.CurrentModuleActor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="aki.modules.enabled",havingValue="true")
@RestController
@RequestMapping("/api/review/admin-games")
public class AdminGameSearchController {
    private final AdminGameSearchService service;
    private final PublisherService publisher;
    public AdminGameSearchController(AdminGameSearchService service,PublisherService publisher) { this.service = service; this.publisher=publisher; }

    @PostMapping("/{id}/force-off-shelf")
    public Object force(@CurrentModuleActor Actor actor,@PathVariable int id,@Valid @RequestBody RequestInput body) { return publisher.forceOffShelf(actor,id,body); }

    @GetMapping
    public Object search(@CurrentModuleActor Actor actor,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return service.search(actor, keyword, status, page, size, sort);
    }
}
