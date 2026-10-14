package gameplatform.support.controller;

import java.util.List;
import java.util.Map;
import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class WorkspaceController {
    @GetMapping("/review/status")
    public Object review(@AuthenticationPrincipal Actor actor) {
        if (actor == null || !actor.canReview()) throw Problem.denied();
        return Map.of("status", "READY", "gamesConnected", true, "commentsConnected", false, "message", "遊戲上架審核已接入；下架改為直接處理；評論維持原有頁面");
    }

    @GetMapping("/management/modules")
    public Object modules(@AuthenticationPrincipal Actor actor) {
        if (actor == null || !actor.isAdmin()) throw Problem.denied();
        return List.of(
            Map.of("name", "客服工作區", "path", "/admin", "description", "查看待處理工作、即時聊天與案件。"),
            Map.of("name", "審核工作區", "path", "/review", "description", "處理遊戲上架申請；管理員可強制下架。"));
    }
}
