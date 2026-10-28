package gameplatform.shop;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;

/** cart 優惠券契約的整合版：登入會員領券、查詢及遊戲適用範圍。 */
@Service
public class CouponService {
    private final JdbcTemplate db;

    public CouponService(JdbcTemplate db) {
        this.db = db;
    }

    private int member(Actor a) {
        if (a == null || !a.isMember())
            throw new Problem(403, "只有會員可操作優惠券");
        return a.id();
    }

    private static int n(Object x) {
        return ((Number) x).intValue();
    }

    private static Object time(Object x) {
        return x instanceof Timestamp t ? t.toLocalDateTime() : x;
    }

    private static final String SELECT = "SELECT c.*,COALESCE(r.scope_type,'ALL') AS scope_type,r.publisher_member_id,r.tag_id,"
            +
            "m.username AS publisher_name,t.tag_name FROM coupons c LEFT JOIN coupon_rules r ON r.coupon_id=c.coupon_id "
            +
            "LEFT JOIN members m ON m.member_id=r.publisher_member_id LEFT JOIN tags t ON t.tag_id=r.tag_id ";

    public Map<String, Object> rule(int id) {
        var rows = db.queryForList(SELECT + "WHERE c.coupon_id=?", id);
        if (rows.isEmpty())
            throw new Problem(404, "找不到優惠券");
        return rows.getFirst();
    }

    public Map<String, Object> dto(Map<String, Object> c) {
        var x = new LinkedHashMap<String, Object>();
        for (var e : Map.ofEntries(Map.entry("couponId", "coupon_id"), Map.entry("couponName", "coupon_name"),
                Map.entry("discountType", "discount_type"), Map.entry("discountValue", "discount_value"),
                Map.entry("minSpend", "min_spend"),
                Map.entry("startAt", "start_at"), Map.entry("expiresAt", "expires_at"),
                Map.entry("perMemberLimit", "per_member_limit"),
                Map.entry("totalLimit", "total_limit"), Map.entry("scopeType", "scope_type")).entrySet())
            x.put(e.getKey(), time(c.get(e.getValue())));
        x.put("scopeLabel", switch (Objects.toString(c.get("scope_type"), "ALL")) {
            case "PUBLISHER" -> "限定發行商：" + c.get("publisher_name");
            case "TAG" -> "限定遊戲類型：" + c.get("tag_name");
            default -> "全平台遊戲";
        });
        return x;
    }

    public boolean active(Map<String, Object> c) {
        var now = LocalDateTime.now();
        return "ACTIVE".equals(c.get("status")) && !((Timestamp) c.get("start_at")).toLocalDateTime().isAfter(now)
                && ((Timestamp) c.get("expires_at")).toLocalDateTime().isAfter(now);
    }

    public int uses(int member, int coupon) {
        return db.queryForObject(
                "SELECT COUNT(*) FROM order_items i JOIN orders o ON o.order_id=i.order_id WHERE o.member_id=? AND i.coupon_id=? AND o.order_status IN ('PENDING','PAID')",
                Integer.class, member, coupon);
    }

    public boolean claimed(int member, int coupon) {
        return db.queryForObject("SELECT COUNT(*) FROM member_coupon_claims WHERE member_id=? AND coupon_id=?",
                Integer.class, member, coupon) > 0;
    }

    private boolean available(Map<String, Object> c) {
        return active(c) && (c.get("total_limit") == null || n(c.get("used_count")) < n(c.get("total_limit")));
    }

    public List<Map<String, Object>> center(Actor a) {
        int id = member(a);
        var result = new ArrayList<Map<String, Object>>();
        for (var c : db.queryForList(SELECT + "WHERE c.status='ACTIVE' ORDER BY c.expires_at,c.coupon_id")) {
            if (!active(c))
                continue;
            boolean has = claimed(id, n(c.get("coupon_id")));
            if (has)
                continue;
            result.add(Map.of("coupon", dto(c), "canClaim", true, "reason", ""));
        }
        return result;
    }

    public List<Map<String, Object>> mine(Actor a) {
        int id = member(a);
        var result = new ArrayList<Map<String, Object>>();
        for (var claim : db
                .queryForList("SELECT * FROM member_coupon_claims WHERE member_id=? ORDER BY received_at DESC", id)) {
            var c = rule(n(claim.get("coupon_id")));
            int used = uses(id, n(c.get("coupon_id")));
            var x = new LinkedHashMap<String, Object>();
            x.put("coupon", dto(c));
            x.put("receivedAt", time(claim.get("received_at")));
            String status = "UNUSED", reason = "";
            var now = LocalDateTime.now();
            if (!((Timestamp) c.get("expires_at")).toLocalDateTime().isAfter(now)) {
                status = "EXPIRED";
                reason = "優惠券已過期";
            } else if (!"ACTIVE".equals(c.get("status"))) {
                status = "UNAVAILABLE";
                reason = "優惠券已停用";
            } else if (((Timestamp) c.get("start_at")).toLocalDateTime().isAfter(now)) {
                status = "UNAVAILABLE";
                reason = "尚未到開始時間";
            } else if (used >= n(c.get("per_member_limit"))) {
                status = "USED";
                reason = "個人使用額度已用完";
            } else if (c.get("total_limit") != null && n(c.get("used_count")) >= n(c.get("total_limit"))) {
                status = "UNAVAILABLE";
                reason = "總使用額度已滿";
            }
            x.put("status", status);
            x.put("unavailableReason", reason);
            x.put("usedCount", used);
            x.put("remainingUses", Math.max(0, n(c.get("per_member_limit")) - used));
            result.add(x);
        }
        return result;
    }

    @Transactional
    public void claim(Actor a, int coupon) {
        int id = member(a);
        // 與結帳、後台編輯使用同一張優惠券資料列鎖，避免重複領取及額度競爭。
        if (db.update("UPDATE coupons SET used_count=used_count WHERE coupon_id=?", coupon) != 1)
            throw new Problem(404, "找不到優惠券");
        var c = rule(coupon);
        if (claimed(id, coupon))
            throw new Problem(409, "已領取這張優惠券");
        if (!active(c))
            throw new Problem(409, "優惠券未開始或已失效");
        db.update("INSERT INTO member_coupon_claims(member_id,coupon_id,received_at) VALUES(?,?,?)", id, coupon,
                LocalDateTime.now());
    }

    public List<Map<String, Object>> usable(Actor a) {
        int id = member(a);
        return db.queryForList(SELECT
                + "WHERE EXISTS(SELECT 1 FROM member_coupon_claims mc WHERE mc.member_id=? AND mc.coupon_id=c.coupon_id) ORDER BY c.expires_at,c.coupon_id",
                id)
                .stream().filter(this::available)
                .filter(c -> uses(id, n(c.get("coupon_id"))) < n(c.get("per_member_limit"))).map(this::dto).toList();
    }

    public boolean eligible(Map<String, Object> c, Map<String, Object> item) {
        return switch (Objects.toString(c.get("scope_type"), "ALL")) {
            case "PUBLISHER" -> Objects.equals(item.get("publisher_member_id"), c.get("publisher_member_id"));
            case "TAG" -> db.queryForObject("SELECT COUNT(*) FROM game_tags WHERE game_id=? AND tag_id=?",
                    Integer.class, item.get("game_id"), c.get("tag_id")) > 0;
            default -> true;
        };
    }
}
