package gameplatform.discount;

import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;
import java.math.*;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 遊戲折扣：七天活動、結束後冷卻三十天。 */
@Service
public class GameDiscountService {
    private final JdbcTemplate db;

    public GameDiscountService(JdbcTemplate db) {
        this.db = db;
    }

    private void publisher(Actor a) {
        if (a == null || !"PUBLISHER".equals(a.role()))
            throw new Problem(403, "只有發行商可以設定折扣");
    }

    public Object list(Actor a) {
        publisher(a);
        return db.queryForList(
                """
                        SELECT g.game_id AS gameId,g.game_name AS gameName,g.price AS originalPrice,g.status,
                               d.discount_percent AS discountPercent,d.start_at AS startAt,d.end_at AS endAt,
                               DATEADD(day,30,d.end_at) AS nextAllowedAt,
                               CASE WHEN d.start_at<=SYSDATETIME() AND d.end_at>SYSDATETIME()
                                    THEN CAST(ROUND(g.price*(100-d.discount_percent)/100.0,0) AS decimal(18,0)) ELSE g.price END AS currentPrice
                        FROM dbo.games g OUTER APPLY (
                            SELECT TOP 1 discount_percent,start_at,end_at FROM dbo.game_discounts
                            WHERE game_id=g.game_id ORDER BY end_at DESC,discount_id DESC
                        ) d WHERE g.member_id=? ORDER BY g.game_id DESC
                        """,
                a.id());
    }

    @Transactional
    public Object create(Actor a, GameDiscountController.CreateRequest r) {
        publisher(a);
        if (r == null || r.gameId() == null || r.percent() == null || r.percent().doubleValue() < 1
                || r.percent().doubleValue() > 99
                || r.percent().doubleValue() != Math.rint(r.percent().doubleValue()))
            throw new Problem(400, "折扣百分比只能是 1～99 的整數");
        // 保留原始數值檢查，避免 JSON 小數被 Integer 轉型後截斷。
        int percent = r.percent().intValue();
        LocalDateTime start;
        try {
            start = LocalDateTime.parse(r.startAt());
        } catch (DateTimeParseException | NullPointerException e) {
            throw new Problem(400, "請輸入正確的開始時間");
        }
        if (!start.isAfter(LocalDateTime.now()))
            throw new Problem(400, "折扣開始時間不可早於現在");
        var games = db.queryForList(
                "SELECT game_id,price,status FROM dbo.games WITH (UPDLOCK,HOLDLOCK) WHERE game_id=? AND member_id=?",
                r.gameId(), a.id());
        if (games.isEmpty())
            throw new Problem(404, "找不到你的遊戲");
        if (!"PUBLISHED".equals(games.getFirst().get("status")))
            throw new Problem(409, "只有已上架遊戲可以設定折扣");
        LocalDateTime end = start.plusDays(7);
        var last = db.queryForList("SELECT TOP 1 end_at FROM dbo.game_discounts WHERE game_id=? ORDER BY end_at DESC",
                r.gameId());
        if (!last.isEmpty()) {
            LocalDateTime next = ((java.sql.Timestamp) last.getFirst().get("end_at")).toLocalDateTime().plusDays(30);
            if (start.isBefore(next))
                throw new Problem(409, "折扣冷卻中，最早可開始時間：" + next);
        }
        db.update("INSERT INTO dbo.game_discounts(game_id,discount_percent,start_at,end_at) VALUES(?,?,?,?)",
                r.gameId(), percent, start, end);
        BigDecimal original = new BigDecimal(games.getFirst().get("price").toString());
        BigDecimal finalPrice = original.multiply(BigDecimal.valueOf(100 - percent)).divide(BigDecimal.valueOf(100), 0,
                RoundingMode.HALF_UP);
        return Map.of("gameId", r.gameId(), "discountPercent", percent, "startAt", start.toString(), "endAt",
                end.toString(), "nextAllowedAt", end.plusDays(30).toString(), "discountPrice", finalPrice);
    }
}
