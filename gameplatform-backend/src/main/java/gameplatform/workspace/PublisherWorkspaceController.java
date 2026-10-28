package gameplatform.workspace;

import gameplatform.support.model.Actor;
import gameplatform.support.service.PublisherImageStorage;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** Adapts the existing publisher workspace to game/admin's workflow. */
@RestController
@RequestMapping("/api/workspace/publisher")
public class PublisherWorkspaceController {
        private final PublisherWorkspaceService service;

        public PublisherWorkspaceController(PublisherWorkspaceService service) {
                this.service = service;
        }

        @GetMapping("/overview")
        public Object overview(Actor a) {
                return service.overview(a);
        }

        @GetMapping("/sales")
        public Object sales(Actor a, @RequestParam(required = false) String from,
                        @RequestParam(required = false) String to, @RequestParam(required = false) Integer gameId) {
                return service.sales(a, from, to, gameId);
        }

        @GetMapping("/tags")
        public Object tags(Actor a) {
                return service.tags(a);
        }

        @GetMapping("/games")
        public Object games(Actor a, @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "") String status) {
                return service.games(a, page, q, size, status);
        }

        @GetMapping("/games/{id}")
        public Object game(Actor a, @PathVariable int id) {
                return service.game(a, id);
        }

        @PostMapping("/games/document")
        public Object create(Actor a, @Valid @RequestBody PublisherWorkspaceService.Document body) {
                return service.document(a, null, body);
        }

        @PutMapping("/games/{id}/document")
        public Object update(Actor a, @PathVariable int id,
                        @Valid @RequestBody PublisherWorkspaceService.Document body) {
                return service.document(a, id, body);
        }

        @PutMapping("/games/{id}/tags")
        public Object tags(Actor a, @PathVariable int id, @Valid @RequestBody PublisherWorkspaceService.Tags body) {
                return service.saveTags(a, id, body);
        }

        @PutMapping("/games/{id}/builds")
        public Object builds(Actor a, @PathVariable int id, @Valid @RequestBody PublisherWorkspaceService.Builds body) {
                return service.saveBuilds(a, id, body);
        }

        @PostMapping("/games/{id}/builds/{buildId}/release")
        public Object releaseBuild(Actor a,@PathVariable int id,@PathVariable int buildId) {
                return service.releaseBuild(a,id,buildId);
        }

        @PutMapping("/games/{id}/media")
        public Object media(Actor a, @PathVariable int id,
                        @Valid @RequestBody PublisherWorkspaceService.MediaList body) {
                return service.saveMedia(a, id, body);
        }

        @PostMapping("/games/{id}/publish")
        public Object publish(Actor a, @PathVariable int id) {
                return service.action(a, id, "submit");
        }

        @PostMapping("/games/{id}/off-shelf")
        public Object offShelf(Actor a, @PathVariable int id) {
                return service.action(a, id, "off-shelf");
        }

        @PostMapping("/games/{id}/restore")
        public Object restore(Actor a, @PathVariable int id) {
                return service.action(a, id, "cancel-off-shelf");
        }
}
