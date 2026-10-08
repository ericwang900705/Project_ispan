package gameplatform.support.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import gameplatform.support.integration.CurrentModuleActor;
import gameplatform.support.model.Actor;
import gameplatform.support.model.PublisherModels.*;
import gameplatform.support.service.PublisherService;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="aki.modules.enabled",havingValue="true")
@RestController
@RequestMapping("/api/publisher")
public class PublisherController {
    private final PublisherService service;
    public PublisherController(PublisherService service) { this.service=service; }
    @GetMapping("/sales") public Object sales(@CurrentModuleActor Actor a,@RequestParam(required=false) String from,@RequestParam(required=false) String to,@RequestParam(required=false) Integer gameId) { return service.sales(a,from,to,gameId); }
    @GetMapping("/notifications") public Object notifications(@CurrentModuleActor Actor a,@RequestParam(defaultValue="0") int page) { return service.notifications(a,page); }
    @PostMapping("/notifications/{id}/read") public Object readNotification(@CurrentModuleActor Actor a,@PathVariable int id) { return service.readNotification(a,id); }
    @PostMapping("/notifications/read-all") public Object readAllNotifications(@CurrentModuleActor Actor a,@RequestParam long throughId) { return service.readAllNotifications(a,throughId); }
    @GetMapping("/overview") public Object overview(@CurrentModuleActor Actor a) { return service.overview(a); }
    @GetMapping("/tags") public Object tags(@CurrentModuleActor Actor a) { return service.tags(a); }
    @GetMapping("/games") public Object games(@CurrentModuleActor Actor a,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="") String q) { return service.games(a,page,q); }
    @GetMapping("/games/{id}") public Object game(@CurrentModuleActor Actor a,@PathVariable int id) { return service.game(a,id); }
    @PostMapping("/games") public Object create(@CurrentModuleActor Actor a,@Valid @RequestBody GameInput body) { return service.create(a,body); }
    @PutMapping("/games/{id}") public Object update(@CurrentModuleActor Actor a,@PathVariable int id,@Valid @RequestBody GameInput body) { return service.update(a,id,body); }
    @PostMapping("/games/{id}/publish") public Object publish(@CurrentModuleActor Actor a,@PathVariable int id,@Valid @RequestBody RequestInput body) { return service.request(a,id,"PUBLISH",body); }
    @PostMapping("/games/{id}/off-shelf") public Object offShelf(@CurrentModuleActor Actor a,@PathVariable int id,@Valid @RequestBody RequestInput body) { return service.offShelf(a,id,body); }
    @PostMapping("/games/{id}/restore") public Object restore(@CurrentModuleActor Actor a,@PathVariable int id) { return service.restore(a,id); }
    @GetMapping("/games/{id}/history") public Object history(@CurrentModuleActor Actor a,@PathVariable int id) { return service.history(a,id); }
    @PutMapping("/games/{id}/tags") public Object tags(@CurrentModuleActor Actor a,@PathVariable int id,@Valid @RequestBody TagsInput body) { return service.saveTags(a,id,body); }
    @PutMapping("/games/{id}/builds") public Object builds(@CurrentModuleActor Actor a,@PathVariable int id,@Valid @RequestBody BuildsInput body) { return service.saveBuilds(a,id,body); }
    @PutMapping("/games/{id}/media") public Object media(@CurrentModuleActor Actor a,@PathVariable int id,@Valid @RequestBody MediaList body) { return service.saveMedia(a,id,body); }
}
