package gameplatform.support.service;

import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="aki.modules.enabled",havingValue="true")
@Service
@Transactional(readOnly = true)
public class AdminGameSearchService {
    private static final Set<String> STATUSES = Set.of("DRAFT", "PENDING", "APPROVED", "PUBLISHED", "REJECTED", "OFF_SHELF", "COMING_SOON");
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "createdAt,desc", "game_id DESC",
            "createdAt,asc", "game_id ASC",
            "gameName,asc", "game_name ASC, game_id ASC",
            "gameName,desc", "game_name DESC, game_id DESC");
    private final JdbcTemplate jdbc;

    public AdminGameSearchService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Object search(Actor actor, String keyword, String status, int page, int size, String sort) {
        if (actor == null || !"ADMIN".equals(actor.role())) throw new Problem(403, "僅管理員可以搜尋所有遊戲");
        if (keyword == null || keyword.length() > 100 || status == null ||
                (!status.isEmpty() && !STATUSES.contains(status)) ||
                page < 0 || page > 100000 || !Set.of(5, 10, 20, 50).contains(size) ||
                !SORT_COLUMNS.containsKey(sort)) throw new Problem(400, "搜尋條件不正確");

        String pattern = "%" + keyword.trim().replace("!", "!!").replace("%", "!%")
                .replace("_", "!_").replace("[", "![") + "%";
        String where = " WHERE game_name LIKE ? ESCAPE '!' AND (? = '' OR status = ?)";
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM games" + where, Long.class, pattern, status, status);
        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT game_id, game_name, member_id, price, description, status, release_date, off_shelf_source, off_shelf_reason " +
                "FROM games" + where + " ORDER BY " + SORT_COLUMNS.get(sort) +
                " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", pattern, status, status, page * size, size);
        return Map.of("items", items, "total", total == null ? 0 : total, "page", page,
                "pageSize", size, "totalPages", total == null ? 0 : (total + size - 1) / size);
    }
}
