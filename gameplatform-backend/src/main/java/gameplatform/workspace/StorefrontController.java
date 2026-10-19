package gameplatform.workspace;

import gameplatform.game.entity.Game;
import gameplatform.game.service.GameService;
import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/** Preserve cart's view model, with game/admin as the catalogue source. */
@RestController
@RequestMapping("/api")
public class StorefrontController {
    private final GameService games;
    private final JdbcTemplate db;
    public StorefrontController(GameService games,JdbcTemplate db) { this.games=games;this.db=db; }
    private Map<String,Object> dto(Game game,Map<Integer,String> genres) {
        var out=new LinkedHashMap<String,Object>();out.put("gameId",game.getGameId());out.put("gameName",game.getGameName());
        out.put("price",game.getPrice());out.put("originalPrice",game.getPrice());out.put("coverUrl",game.getCoverUrl());
        out.put("description",game.getDescription());out.put("status",game.getStatus());out.put("releaseDate",game.getReleaseDate());
        out.put("genre",genres.getOrDefault(game.getGameId(),"其他"));return out;
    }
    @GetMapping("/games") public Object list() {
        var genres=new HashMap<Integer,String>();
        db.query("SELECT gt.game_id,t.tag_name FROM game_tags gt JOIN tags t ON t.tag_id=gt.tag_id ORDER BY t.tag_id",(org.springframework.jdbc.core.RowCallbackHandler)rs->genres.putIfAbsent(rs.getInt(1),rs.getString(2)));
        var result=new ArrayList<Map<String,Object>>();
        int page=0;org.springframework.data.domain.Page<Game> batch;
        do {batch=games.findAll(PageRequest.of(page++,200));for(var game:batch) result.add(dto(game,genres));} while(batch.hasNext());
        return result;
    }
    @GetMapping("/games/stats") public Object stats() {
        return db.queryForList("SELECT g.game_id AS gameId,(SELECT AVG(CAST(r.rating AS FLOAT)) FROM game_reviews r WHERE r.game_id=g.game_id AND r.status='NORMAL') AS avgRating,(SELECT COUNT(*) FROM game_reviews r WHERE r.game_id=g.game_id AND r.status='NORMAL') AS reviewCount,(SELECT COALESCE(SUM(oi.quantity),0) FROM order_items oi JOIN orders o ON o.order_id=oi.order_id WHERE oi.game_id=g.game_id AND o.order_status='PAID') AS soldCount FROM games g WHERE g.status IN ('PUBLISHED','COMING_SOON','PENDING_OFF_SHELF')");
    }
    @GetMapping("/games/{id}/images") public Object images(@PathVariable int id) {
        if(games.findById(id).isEmpty()) throw new Problem(404,"找不到遊戲");
        return db.queryForList("SELECT media_url FROM game_media WHERE game_id=? AND media_type='IMAGE' ORDER BY display_order,media_id",String.class,id);
    }
    @GetMapping("/members/lookup") public Object lookup(Actor a,@RequestParam String username) {
        if(!a.isMember()) throw new Problem(403,"只有會員可查詢送禮對象");
        var list=db.queryForList("SELECT member_id AS memberId,username FROM members WHERE username=?",username);
        if(list.isEmpty()) throw new Problem(404,"找不到會員");return list.getFirst();
    }
}
