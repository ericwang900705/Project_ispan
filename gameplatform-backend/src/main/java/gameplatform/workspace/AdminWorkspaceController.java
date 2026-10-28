
package gameplatform.workspace;

import gameplatform.game.repository.GameRepository;
import gameplatform.game.service.GameTagService;
import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/games")
public class AdminWorkspaceController {
    private final GameRepository games;
    private final GameTagService tags;
    private final JdbcTemplate db;

    public AdminWorkspaceController(GameRepository games, GameTagService tags, JdbcTemplate db) {
        this.games = games;
        this.tags = tags;
        this.db = db;
    }

    @GetMapping("/{id}")
    public Object detail(Actor actor, @PathVariable int id) {
        if (!actor.canReview())
            throw new Problem(403, "沒有遊戲審核權限");
        var game = games.findById(id).orElseThrow(() -> new Problem(404, "找不到遊戲"));
        if ("DRAFT".equals(game.getStatus()))
            throw new Problem(404, "遊戲尚未送審");
        return Map.of("game", game, "tags", tags.findGameTags(id), "builds",
                db.queryForList("SELECT * FROM game_builds WHERE game_id=? ORDER BY build_id", id), "media",
                db.queryForList("SELECT * FROM game_media WHERE game_id=? ORDER BY display_order,media_id", id));
    }
}
