package gameplatform.support.controller;

import java.util.List;
import java.util.Map;
import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;
import gameplatform.support.integration.CurrentModuleActor;
import org.springframework.web.bind.annotation.*;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="aki.modules.enabled",havingValue="true")
@RestController
@RequestMapping("/api/review")
public class WorkspaceController {
    @GetMapping("/status")
    public Object review(@CurrentModuleActor Actor actor) {
        if (actor == null || !actor.canReview()) throw Problem.denied();
        return Map.of("status", "READY", "gamesConnected", true, "commentsConnected", false, "message", "評論資料尚未串接");
    }

}
