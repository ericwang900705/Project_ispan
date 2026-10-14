package gameplatform.support.service;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;

@Service
public class DashboardService {
    private final JdbcTemplate db;
    public DashboardService(JdbcTemplate db) { this.db = db; }
    @Transactional(readOnly = true)
    public Map<String,Object> overview(Actor actor) {
        if (actor == null || !actor.isAdmin()) throw new Problem(403, "只有管理者可以查看平台儀表板");
        var result = new LinkedHashMap<String,Object>();
        result.put("memberCount", db.queryForObject("SELECT COUNT(*) FROM members", Long.class));
        result.put("gameCount", db.queryForObject("SELECT COUNT(*) FROM games", Long.class));
        // The uploaded project does not provide an order/revenue integration.
        // Null means unavailable, never zero or synthetic statistics.
        result.put("monthlyOrderCount", null);
        result.put("monthlyRevenue", null);
        result.put("revenueTrend", List.of());
        result.put("commerceConnected", false);
        result.put("updatedAt", Instant.now());
        return result;
    }
}
