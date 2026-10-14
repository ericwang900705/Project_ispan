package gameplatform.support.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import gameplatform.support.model.Actor;
import gameplatform.support.model.PublisherModels.*;
import gameplatform.support.service.PublisherService;

@RestController
@RequestMapping("/api/publisher")
public class PublisherController {
    private final PublisherService service;
    public PublisherController(PublisherService service) { this.service=service; }
    @PostMapping("/games/document")
    public Object createDocument(@AuthenticationPrincipal Actor actor, @Valid @RequestBody DocumentInput body) {
        return service.saveDocument(actor,null,body);
    }
    @PutMapping("/games/{id}/document")
    public Object updateDocument(@AuthenticationPrincipal Actor actor, @PathVariable int id, @Valid @RequestBody DocumentInput body) {
        return service.saveDocument(actor,id,body);
    }
    @GetMapping("/sales") public Object sales(@AuthenticationPrincipal Actor a,@RequestParam(required=false) String from,@RequestParam(required=false) String to,@RequestParam(required=false) Integer gameId) { return service.sales(a,from,to,gameId); }
    @GetMapping("/notifications") public Object notifications(@AuthenticationPrincipal Actor a,@RequestParam(defaultValue="0") int page) { return service.notifications(a,page); }
    @PostMapping("/notifications/{id}/read") public Object readNotification(@AuthenticationPrincipal Actor a,@PathVariable int id) { return service.readNotification(a,id); }
    @PostMapping("/notifications/read-all") public Object readAllNotifications(@AuthenticationPrincipal Actor a,@RequestParam long throughId) { return service.readAllNotifications(a,throughId); }
    @GetMapping("/overview") public Object overview(@AuthenticationPrincipal Actor a) { return service.overview(a); }
    @GetMapping("/tags") public Object tags(@AuthenticationPrincipal Actor a) { return service.tags(a); }
    @GetMapping("/games") public Object games(@AuthenticationPrincipal Actor a,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="") String q) { return service.games(a,page,q); }
    @GetMapping("/games/{id}") public Object game(@AuthenticationPrincipal Actor a,@PathVariable int id) { return service.game(a,id); }
    @PostMapping("/games") public Object create(@AuthenticationPrincipal Actor a,@Valid @RequestBody GameInput body) { return service.create(a,body); }
    @PutMapping("/games/{id}") public Object update(@AuthenticationPrincipal Actor a,@PathVariable int id,@Valid @RequestBody GameInput body) { return service.update(a,id,body); }
    @PostMapping("/games/{id}/publish") public Object publish(@AuthenticationPrincipal Actor a,@PathVariable int id,@Valid @RequestBody RequestInput body) { return service.request(a,id,"PUBLISH",body); }
    @PostMapping("/games/{id}/off-shelf") public Object offShelf(@AuthenticationPrincipal Actor a,@PathVariable int id,@Valid @RequestBody RequestInput body) { return service.offShelf(a,id,body); }
    @PostMapping("/games/{id}/restore") public Object restore(@AuthenticationPrincipal Actor a,@PathVariable int id) { return service.restore(a,id); }
    @GetMapping("/games/{id}/history") public Object history(@AuthenticationPrincipal Actor a,@PathVariable int id) { return service.history(a,id); }
    @PutMapping("/games/{id}/tags") public Object tags(@AuthenticationPrincipal Actor a,@PathVariable int id,@Valid @RequestBody TagsInput body) { return service.saveTags(a,id,body); }
    @PutMapping("/games/{id}/builds") public Object builds(@AuthenticationPrincipal Actor a,@PathVariable int id,@Valid @RequestBody BuildsInput body) { return service.saveBuilds(a,id,body); }
    @PutMapping("/games/{id}/media") public Object media(@AuthenticationPrincipal Actor a,@PathVariable int id,@Valid @RequestBody MediaList body) { return service.saveMedia(a,id,body); }
}
