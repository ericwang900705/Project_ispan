package gameplatform.shop;

import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;

/**
 * Adapted cart branch API contract to HERO's 1013 SQL schema and JWT identity.
 */
@Service
public class ShopService {
    final JdbcTemplate db;
    final CouponService couponRules;

    public ShopService(JdbcTemplate db) {
        this.db = db;
        this.couponRules = new CouponService(db);
    }

    static Problem bad(String s) {
        return new Problem(400, s);
    }

    public void owner(Actor actor, int member) {
        if (actor == null || !actor.isMember() || actor.id() != member)
            throw new Problem(403, "只能操作自己的商城資料");
    }

    void lockMember(int id) {
        // Transactional no-op UPDATE acquires a row lock on SQL Server.
        if (db.update("UPDATE members SET failed_login_count=failed_login_count WHERE member_id=?", id) != 1)
            throw new Problem(404, "找不到會員");
    }

    int count(String sql, Object... args) {
        return db.queryForObject(sql, Integer.class, args);
    }

    Map<String, Object> row(String sql, Object... args) {
        var rows = db.queryForList(sql, args);
        if (rows.isEmpty())
            throw new Problem(404, "找不到資料");
        return rows.getFirst();
    }

    static int number(Object v) {
        return ((Number) v).intValue();
    }

    static BigDecimal money(Object v) {
        return v instanceof BigDecimal b ? b : new BigDecimal(v.toString());
    }

    static boolean yes(Object v) {
        return Boolean.TRUE.equals(v) || v instanceof Number n && n.intValue() != 0;
    }

    static Object time(Object o) {
        return o instanceof Timestamp t ? t.toLocalDateTime() : o;
    }

    int insert(String sql, String key, Object... args) {
        var holder = new GeneratedKeyHolder();
        db.update(c -> {
            var ps = c.prepareStatement(sql, new String[] { key });
            for (int i = 0; i < args.length; i++)
                ps.setObject(i + 1, args[i]);
            return ps;
        }, holder);
        return Objects.requireNonNull(holder.getKey()).intValue();
    }

    Map<String, Object> game(Map<String, Object> r) {
        var out = new LinkedHashMap<String, Object>();
        out.put("gameId", r.get("game_id"));
        out.put("gameName", r.get("game_name"));
        out.put("coverUrl", r.get("cover_url"));
        out.put("price", r.get("price"));
        out.put("originalPrice", r.getOrDefault("original_price", r.get("price")));
        out.put("discountPercent", r.get("discount_percent"));
        return out;
    }

    boolean owned(int member, int game) {
        return count("SELECT COUNT(*) FROM member_games WHERE member_id=? AND game_id=? AND is_active=1", member,
                game) > 0;
    }

    Map<String, Object> couponRow(int id) {
        return couponRules.rule(id);
    }

    Map<String, Object> couponDto(Map<String, Object> r) {
        return couponRules.dto(r);
    }

    boolean couponActive(Map<String, Object> c) {
        LocalDateTime now = LocalDateTime.now();
        return "ACTIVE".equals(c.get("status")) && !((Timestamp) c.get("start_at")).toLocalDateTime().isAfter(now)
                && ((Timestamp) c.get("expires_at")).toLocalDateTime().isAfter(now);
    }

    void validCoupon(Map<String, Object> c, int member, int items, BigDecimal subtotal) {
        if (!couponRules.claimed(member, number(c.get("coupon_id"))))
            throw bad("請先到優惠券頁領取這張優惠券");
        if (items == 0)
            throw bad("購物車沒有符合優惠券使用規則的遊戲");
        if (!couponActive(c) || subtotal.compareTo(money(c.get("min_spend"))) < 0)
            throw bad("優惠券已過期、停用或未達最低金額");
        int limit = c.get("total_limit") == null ? Integer.MAX_VALUE : number(c.get("total_limit"));
        if ((long) number(c.get("used_count")) + items > limit)
            throw bad("優惠券使用名額不足");
        int uses = count(
                "SELECT COUNT(*) FROM order_items i JOIN orders o ON o.order_id=i.order_id WHERE o.member_id=? AND i.coupon_id=? AND o.order_status IN ('PENDING','PAID')",
                member, c.get("coupon_id"));
        if ((long) uses + items > number(c.get("per_member_limit")))
            throw bad("超過每位會員的優惠券使用上限（按訂單明細計次）");
    }

    public List<Map<String, Object>> coupons(Actor a) {
        return couponRules.usable(a);
    }

    List<Map<String, Object>> cartRows(int member) {
        return db.queryForList(
                """
                SELECT c.*,g.game_name,g.cover_url,g.price AS original_price,
                    CASE WHEN d.discount_percent IS NULL THEN g.price
                         ELSE CAST(ROUND(g.price*(100-d.discount_percent)/100.0,0) AS decimal(18,0)) END AS price,
                    d.discount_percent,g.member_id AS publisher_member_id,g.status AS game_status,m.username AS recipient_name
                FROM cart_items c JOIN games g ON g.game_id=c.game_id
                OUTER APPLY (SELECT TOP 1 discount_percent FROM dbo.game_discounts
                    WHERE game_id=g.game_id AND start_at<=SYSDATETIME() AND end_at>SYSDATETIME()
                    ORDER BY start_at DESC) d
                LEFT JOIN members m ON m.member_id=c.recipient_member_id
                WHERE c.member_id=? ORDER BY c.created_at,c.cart_item_id
                """,
                member);
    }

    record Pricing(List<BigDecimal> discounts, BigDecimal subtotal, BigDecimal discount, Map<String, Object> coupon,
            List<Boolean> eligible) {
    }

    Pricing price(List<Map<String, Object>> rows, int member, boolean strict) {
        BigDecimal sub = rows.stream().map(x -> money(x.get("price"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> coupon = null;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal eligibleSubtotal = sub;
        var eligible = new ArrayList<Boolean>(Collections.nCopies(rows.size(), true));
        Integer cid = rows.isEmpty() || rows.getFirst().get("coupon_id") == null ? null
                : number(rows.getFirst().get("coupon_id"));
        if (cid != null) {
            if (rows.stream().anyMatch(x -> !Objects.equals(x.get("coupon_id"), rows.getFirst().get("coupon_id"))))
                throw bad("購物車優惠券不一致，請重新套用");
            coupon = couponRow(cid);
            try {
                for (int i = 0; i < rows.size(); i++)
                    eligible.set(i, couponRules.eligible(coupon, rows.get(i)));
                eligibleSubtotal = BigDecimal.ZERO;
                int matches = 0;
                for (int i = 0; i < rows.size(); i++)
                    if (eligible.get(i)) {
                        eligibleSubtotal = eligibleSubtotal.add(money(rows.get(i).get("price")));
                        matches++;
                    }
                validCoupon(coupon, member, matches, eligibleSubtotal);
            } catch (Problem e) {
                if (strict)
                    throw e;
                coupon = null;
            }
            if (coupon != null) {
                totalDiscount = "RATE".equals(coupon.get("discount_type"))
                        ? eligibleSubtotal.multiply(BigDecimal.ONE.subtract(money(coupon.get("discount_value"))))
                        : money(coupon.get("discount_value"));
                // ECPay requires whole TWD amounts; discounts round down once for the full
                // cart.
                totalDiscount = totalDiscount.max(BigDecimal.ZERO).min(eligibleSubtotal).setScale(0, RoundingMode.DOWN);
            }
        }
        var ds = new ArrayList<BigDecimal>();
        BigDecimal left = totalDiscount;
        for (int index = 0; index < rows.size(); index++) {
            var row = rows.get(index);
            BigDecimal value = money(row.get("price"));
            BigDecimal d = !eligible.get(index) || eligibleSubtotal.signum() == 0 ? BigDecimal.ZERO
                    : totalDiscount.multiply(value).divide(eligibleSubtotal, 2, RoundingMode.DOWN);
            ds.add(d);
            left = left.subtract(d);
        }
        // Distribute residual cents into non-zero rows; zero-price last items must not
        // lose discount cents.
        for (int i = rows.size() - 1; i >= 0 && left.signum() > 0; i--) {
            if (!eligible.get(i))
                continue;
            BigDecimal extra = left.min(money(rows.get(i).get("price")).subtract(ds.get(i)));
            ds.set(i, ds.get(i).add(extra));
            left = left.subtract(extra);
        }
        return new Pricing(ds, sub, totalDiscount, coupon, eligible);
    }

    public Map<String, Object> cart(Actor a, int member) {
        owner(a, member);
        var rows = cartRows(member);
        var p = price(rows, member, false);
        var items = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < rows.size(); i++) {
            var r = rows.get(i);
            var x = new LinkedHashMap<String, Object>();
            x.put("cartItemId", r.get("cart_item_id"));
            x.put("game", game(r));
            x.put("isGift", yes(r.get("is_gift")));
            x.put("recipientMember", r.get("recipient_member_id") == null ? null
                    : Map.of("memberId", r.get("recipient_member_id"), "username", r.get("recipient_name")));
            x.put("coupon", p.coupon() == null || !p.eligible().get(i) ? null : couponDto(p.coupon()));
            x.put("finalPrice", money(r.get("price")).subtract(p.discounts().get(i)));
            items.add(x);
        }
        var out = new LinkedHashMap<String, Object>();
        out.put("items", items);
        out.put("coupon", p.coupon() == null ? null : couponDto(p.coupon()));
        out.put("totalPrice", p.subtotal().subtract(p.discount()));
        return out;
    }

    public record Add(Integer gameId, Boolean isGift, Integer recipientMemberId) {
    }

    @Transactional
    public Map<String, Object> add(Actor a, int member, Add input) {
        owner(a, member);
        lockMember(member);
        if (input == null || input.gameId() == null)
            throw bad("請指定遊戲");
        var g = row("SELECT * FROM games WHERE game_id=?", input.gameId());
        if (!Set.of("PUBLISHED", "PENDING_OFF_SHELF").contains(g.get("status")))
            throw bad("只有已上架遊戲能購買");
        boolean gift = Boolean.TRUE.equals(input.isGift());
        Integer recipient = gift ? input.recipientMemberId() : null;
        if (gift && (recipient == null || recipient == member
                || count("SELECT COUNT(*) FROM members WHERE member_id=?", recipient) != 1))
            throw bad("請指定有效的受贈會員，不能送給自己");
        if (owned(gift ? recipient : member, input.gameId()))
            throw bad("會員已擁有此遊戲");
        if (count(
                "SELECT COUNT(*) FROM cart_items WHERE member_id=? AND game_id=? AND ((recipient_member_id IS NULL AND ? IS NULL) OR recipient_member_id=?)",
                member, input.gameId(), recipient, recipient) > 0)
            throw new Problem(409, "此遊戲已在購物車");
        var rows = cartRows(member);
        Object coupon = rows.isEmpty() ? null : rows.getFirst().get("coupon_id");
        db.update("INSERT INTO cart_items(member_id,game_id,is_gift,recipient_member_id,coupon_id) VALUES(?,?,?,?,?)",
                member, input.gameId(), gift, recipient, coupon);
        return cart(a, member);
    }

    @Transactional
    public Map<String, Object> apply(Actor a, int member, Integer id) {
        owner(a, member);
        lockMember(member);
        var rows = cartRows(member);
        if (rows.isEmpty())
            throw bad("購物車是空的");
        if (id != null) {
            var c = couponRow(id);
            var matching = rows.stream().filter(x -> couponRules.eligible(c, x)).toList();
            BigDecimal sub = matching.stream().map(x -> money(x.get("price"))).reduce(BigDecimal.ZERO, BigDecimal::add);
            validCoupon(c, member, matching.size(), sub);
        }
        db.update("UPDATE cart_items SET coupon_id=? WHERE member_id=?", id, member);
        return cart(a, member);
    }

    @Transactional
    public void remove(Actor a, int member, int item) {
        owner(a, member);
        lockMember(member);
        if (db.update("DELETE FROM cart_items WHERE member_id=? AND cart_item_id=?", member, item) == 0)
            throw new Problem(404, "找不到你的購物車項目");
    }

    @Transactional
    public Map<String, Object> clear(Actor a, int member) {
        owner(a, member);
        lockMember(member);
        db.update("DELETE FROM cart_items WHERE member_id=?", member);
        return cart(a, member);
    }

    // 沿用 CART 的 POST { minutes } 累加 member_games 時數，不另外建立計時工作階段。
    @Transactional
    public Map<String, Object> recordPlay(Actor actor, int member, int gameId, Integer minutes) {
        owner(actor, member);
        if (minutes == null || minutes < 1 || minutes > 1440)
            throw bad("遊玩分鐘數必須介於 1 與 1440");
        var game = row("SELECT g.game_name FROM member_games l JOIN games g ON g.game_id=l.game_id "
                + "WHERE l.member_id=? AND l.game_id=? AND l.is_active=1", member, gameId);
        if (!Set.of("貪食蛇", "數字合成 2048", "踩地雷", "打磚塊", "記憶翻牌", "火柴人格鬥：霓虹對決")
                .contains(game.get("game_name")))
            throw new Problem(409, "這款遊戲尚無網頁遊玩版本");
        int changed = db.update("UPDATE member_games SET total_play_minutes=COALESCE(total_play_minutes,0)+?, "
                + "last_played_at=? WHERE member_id=? AND game_id=? AND is_active=1",
                minutes, LocalDateTime.now(), member, gameId);
        if (changed == 0)
            throw new Problem(404, "找不到你的遊戲庫紀錄");
        return Map.of("minutesAdded", minutes);
    }

    public List<Map<String, Object>> library(Actor a, int member) {
        owner(a, member);
        return db.queryForList(
                "SELECT l.*,g.game_name,g.cover_url,g.price FROM member_games l JOIN games g ON g.game_id=l.game_id WHERE l.member_id=? AND l.is_active=1 ORDER BY l.obtained_at DESC,l.library_id DESC",
                member).stream().map(r -> {
                    var m = new LinkedHashMap<String, Object>();
                    m.put("libraryId", r.get("library_id"));
                    m.put("game", game(r));
                    for (var p : Map.of("obtainedSource", "obtained_source", "obtainedAt", "obtained_at",
                            "totalPlayMinutes", "total_play_minutes", "lastPlayedAt", "last_played_at").entrySet())
                        m.put(p.getKey(), time(r.get(p.getValue())));
                    m.put("isActive", true);
                    return (Map<String, Object>) m;
                }).toList();
    }

    public List<Map<String, Object>> orders(Actor a, int member) {
        owner(a, member);
        return db.queryForList("SELECT order_id FROM orders WHERE member_id=? ORDER BY created_at DESC,order_id DESC",
                member).stream().map(r -> orderDto(number(r.get("order_id")))).toList();
    }

    Map<String, Object> orderRow(int id) {
        return row("SELECT * FROM orders WHERE order_id=?", id);
    }

    List<Map<String, Object>> orderItems(int id) {
        return db.queryForList(
                "SELECT i.*,g.game_name,g.cover_url,g.price FROM order_items i JOIN games g ON g.game_id=i.game_id WHERE i.order_id=? ORDER BY i.order_item_id",
                id);
    }

    public Map<String, Object> orderDto(int id) {
        var r = orderRow(id);
        var m = new LinkedHashMap<String, Object>();
        for (var pair : Map.ofEntries(Map.entry("orderId", "order_id"), Map.entry("orderNo", "order_no"),
                Map.entry("totalAmount", "total_amount"), Map.entry("subtotalAmount", "subtotal_amount"),
                Map.entry("discountAmount", "discount_amount"), Map.entry("orderStatus", "order_status"),
                Map.entry("pointsEarned", "points_earned"), Map.entry("createdAt", "created_at"),
                Map.entry("paidAt", "paid_at"), Map.entry("paymentType", "payment_type")).entrySet())
            m.put(pair.getKey(), time(r.get(pair.getValue())));
        Object paid = r.get("paid_at");
        java.time.LocalDateTime paidTime = paid instanceof java.sql.Timestamp timestamp ? timestamp.toLocalDateTime()
                : paid instanceof java.time.LocalDateTime local ? local : null;
        m.put("refundDeadline", "PAID".equals(r.get("order_status")) && paidTime != null
                ? paidTime.plusDays(14).toString() : null);
        var info = new LinkedHashMap<String, Object>();
        info.put("bankCode", r.get("bank_code"));
        info.put("virtualAccount", r.get("virtual_account"));
        info.put("paymentNo", r.get("payment_no"));
        info.put("expireDate", r.get("payment_expire_date"));
        m.put("paymentInfo", r.get("virtual_account") == null && r.get("payment_no") == null ? null : info);
        m.put("paymentStatus", r.get("payment_status"));
        m.put("orderItems", orderItems(id).stream().map(x -> {
            var item = new LinkedHashMap<String, Object>();
            item.put("orderItemId", x.get("order_item_id"));
            item.put("game", game(x));
            item.put("unitPrice", x.get("unit_price"));
            item.put("discountAmount", x.get("discount_amount"));
            item.put("quantity", x.get("quantity"));
            item.put("isGift", yes(x.get("is_gift")));
            item.put("recipientMember",
                    x.get("recipient_member_id") == null ? null
                            : row("SELECT member_id AS memberId,username FROM members WHERE member_id=?",
                                    x.get("recipient_member_id")));
            item.put("coupon", x.get("coupon_id") == null ? null : couponDto(couponRow(number(x.get("coupon_id")))));
            return item;
        }).toList());
        return m;
    }

    public Map<String, Object> paymentOrder(Actor a, int member, String trade) {
        owner(a, member);
        var r = row("SELECT order_id,member_id FROM orders WHERE order_no=?", trade);
        if (number(r.get("member_id")) != member)
            throw new Problem(403, "只能查詢自己的訂單");
        return orderDto(number(r.get("order_id")));
    }

    @Transactional
    public Map<String, Object> createOrder(Actor a, int member, String method) {
        owner(a, member);
        var initialRows = cartRows(member);
        var members = new TreeSet<Integer>();
        members.add(member);
        for (var r : initialRows)
            if (r.get("recipient_member_id") != null)
                members.add(number(r.get("recipient_member_id")));
        for (int id : members)
            lockMember(id);
        var rows = cartRows(member);
        if (rows.isEmpty())
            throw bad("購物車是空的");
        for (var r : rows)
            if (r.get("recipient_member_id") != null && !members.contains(number(r.get("recipient_member_id"))))
                throw new Problem(409, "購物車已變更，請重新結帳");
        for (var r : rows) {
            if (!Set.of("PUBLISHED", "PENDING_OFF_SHELF").contains(r.get("game_status")))
                throw bad("購物車有尚未上架或下架遊戲");
            int recipient = r.get("recipient_member_id") == null ? member : number(r.get("recipient_member_id"));
            if (owned(recipient, number(r.get("game_id"))))
                throw bad("會員已擁有購物車中的遊戲");
            if (count(
                    "SELECT COUNT(*) FROM order_items i JOIN orders o ON o.order_id=i.order_id WHERE o.order_status='PENDING' AND i.game_id=? AND ((i.is_gift=0 AND o.member_id=?) OR (i.is_gift=1 AND i.recipient_member_id=?))",
                    r.get("game_id"), recipient, recipient) > 0)
                throw new Problem(409, "此遊戲已有待付款訂單，請先付款或取消");
        }
        Integer cid = rows.getFirst().get("coupon_id") == null ? null : number(rows.getFirst().get("coupon_id"));
        if (cid != null)
            db.update("UPDATE coupons SET used_count=used_count WHERE coupon_id=?", cid);
        var p = price(rows, member, true);
        BigDecimal total = p.subtotal().subtract(p.discount());
        if (total.signum() <= 0 || total.stripTrailingZeros().scale() > 0)
            throw bad("目前金流僅支援正整數新台幣訂單");
        String no = "EC" + UUID.randomUUID().toString().replace("-", "").substring(0, 18).toUpperCase(Locale.ROOT);
        int id = insert(
                "INSERT INTO orders(order_no,member_id,subtotal_amount,discount_amount,total_amount,payment_type,payment_status) VALUES(?,?,?,?,?,?,'PENDING')",
                "order_id", no, member, p.subtotal(), p.discount(), total, method);
        for (int i = 0; i < rows.size(); i++) {
            var r = rows.get(i);
            db.update(
                    "INSERT INTO order_items(order_id,game_id,quantity,unit_price,discount_amount,is_gift,recipient_member_id,coupon_id) VALUES(?,?,1,?,?,?,?,?)",
                    id, r.get("game_id"), r.get("price"), p.discounts().get(i), r.get("is_gift"),
                    r.get("recipient_member_id"), p.eligible().get(i) ? cid : null);
        }
        if (cid != null)
            db.update("UPDATE coupons SET used_count=used_count+? WHERE coupon_id=?",
                    (int) p.eligible().stream().filter(Boolean::booleanValue).count(), cid);
        // Cart contents were snapshotted and moved into a pending order; library stays
        // unchanged.
        db.update("DELETE FROM cart_items WHERE member_id=?", member);
        return orderDto(id);
    }

    void lockOrder(int id) {
        if (db.update("UPDATE orders SET updated_at=updated_at WHERE order_id=?", id) != 1)
            throw new Problem(404, "找不到訂單");
    }

    @Transactional
    public Map<String, Object> cancel(Actor a, int member, int id) {
        owner(a, member);
        lockMember(member);
        lockOrder(id);
        var r = orderRow(id);
        if (number(r.get("member_id")) != member)
            throw new Problem(403, "只能取消自己的訂單");
        if (!"PENDING".equals(r.get("order_status")))
            throw bad("只有待付款訂單可以取消");
        cancelInternal(id, "CANCELLED");
        return orderDto(id);
    }

    void cancelInternal(int id, String paymentStatus) {
        // Release reserved coupon uses once, under the order row lock.
        for (var c : db.queryForList(
                "SELECT coupon_id,COUNT(*) AS n FROM order_items WHERE order_id=? AND coupon_id IS NOT NULL GROUP BY coupon_id",
                id))
            db.update("UPDATE coupons SET used_count=used_count-? WHERE coupon_id=?", c.get("n"), c.get("coupon_id"));
        db.update(
                "UPDATE orders SET order_status='CANCELLED',payment_status=?,cancelled_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE order_id=?",
                paymentStatus, id);
    }

    @Transactional
    public String payment(Map<String, String> input, boolean info) {
        var initial = row("SELECT * FROM orders WHERE order_no=?", input.get("MerchantTradeNo"));
        int id = number(initial.get("order_id"));
        // Lock buyer and recipients in sorted order, then order, matching
        // checkout/cancel lock order.
        var people = new TreeSet<Integer>();
        people.add(number(initial.get("member_id")));
        for (var x : orderItems(id))
            if (x.get("recipient_member_id") != null)
                people.add(number(x.get("recipient_member_id")));
        for (int m : people)
            lockMember(m);
        lockOrder(id);
        var order = orderRow(id);
        BigDecimal amount;
        try {
            amount = new BigDecimal(input.get("TradeAmt"));
        } catch (Exception e) {
            throw bad("付款金額無效");
        }
        if (amount.compareTo(money(order.get("total_amount"))) != 0)
            throw bad("付款金額與訂單不符");
        if ("PAID".equals(order.get("order_status"))) {
            if (!info && !Objects.equals(order.get("ecpay_trade_no"), input.get("TradeNo")))
                throw new Problem(409, "金流交易編號與已付款訂單不符");
            return "success";
        }
        if ("FAILED".equals(order.get("payment_status")) && !"1".equals(input.get("RtnCode")) && !info)
            return "fail";
        if (!"PENDING".equals(order.get("order_status")))
            throw new Problem(409, "此訂單已取消，需人工核對金流");
        if (info) {
            if (!Set.of("2", "10100073").contains(input.get("RtnCode")))
                throw bad("取號通知失敗");
            db.update(
                    "UPDATE orders SET bank_code=?,virtual_account=?,payment_no=?,payment_expire_date=?,updated_at=CURRENT_TIMESTAMP WHERE order_id=?",
                    input.get("BankCode"), input.get("vAccount"), input.get("PaymentNo"), input.get("ExpireDate"), id);
            return "pending";
        }
        if (!"1".equals(input.get("RtnCode"))) {
            cancelInternal(id, "FAILED");
            return "fail";
        }
        String trade = input.get("TradeNo");
        if (trade == null || trade.isBlank())
            throw bad("缺少金流交易編號");
        if (count("SELECT COUNT(*) FROM orders WHERE ecpay_trade_no=? AND order_id<>?", trade, id) > 0)
            throw new Problem(409, "金流交易編號重複");
        for (var x : orderItems(id)) {
            int member = x.get("recipient_member_id") == null ? number(order.get("member_id"))
                    : number(x.get("recipient_member_id"));
            int game = number(x.get("game_id"));
            if (owned(member, game))
                throw new Problem(409, "會員已擁有此遊戲，需人工核對訂單");
            String source = yes(x.get("is_gift")) ? "GIFT" : "PURCHASE";
            int changed = db.update(
                    "UPDATE member_games SET is_active=1,revoked_at=NULL,order_item_id=?,obtained_source=?,obtained_at=CURRENT_TIMESTAMP WHERE member_id=? AND game_id=? AND is_active=0",
                    x.get("order_item_id"), source, member, game);
            if (changed == 0)
                db.update("INSERT INTO member_games(member_id,game_id,order_item_id,obtained_source) VALUES(?,?,?,?)",
                        member, game, x.get("order_item_id"), source);
        }
        db.update(
                "UPDATE orders SET order_status='PAID',payment_status='PAID',ecpay_trade_no=?,payment_type=?,paid_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE order_id=?",
                trade, input.get("PaymentType"), id);
        return "success";
    }

    public record Review(Integer rating, Boolean recommended, String content) {
    }

    public List<Map<String, Object>> reviews(int game, Actor actor) {
        return db.queryForList(
                "SELECT r.*,m.username FROM game_reviews r JOIN members m ON m.member_id=r.member_id JOIN games g ON g.game_id=r.game_id WHERE r.game_id=? AND r.status='NORMAL' AND g.status IN ('PUBLISHED','COMING_SOON','PENDING_OFF_SHELF') ORDER BY r.created_at DESC,r.review_id DESC",
                game).stream().map(r -> {
                    var m = new LinkedHashMap<String, Object>();
                    m.put("id", r.get("review_id"));
                    m.put("gameId", r.get("game_id"));
                    m.put("username", r.get("username"));
                    m.put("rating", r.get("rating"));
                    m.put("recommended", yes(r.get("is_recommended")));
                    m.put("content", r.get("content"));
                    m.put("createdAt", time(r.get("created_at")));
                    m.put("updatedAt", time(r.get("updated_at")));
                    m.put("mine", actor != null && actor.isMember() && actor.id() == number(r.get("member_id")));
                    return (Map<String, Object>) m;
                }).toList();
    }

    public Object reviewMuteStatus(Actor actor){
        if(actor==null||!actor.isMember())throw new Problem(403,"僅會員可查詢自己的評論限制");
        return new gameplatform.moderation.ReviewModerationPolicy(db).status(actor.id());
    }

    @Transactional
    public Object reportReview(Actor actor,int reviewId,String reason){
        if(actor==null||!actor.isMember())throw new Problem(403,"請先登入會員");
        if(reason==null||reason.isBlank()||reason.trim().length()>500)throw bad("請填寫 1～500 字的檢舉原因");
        lockMember(actor.id());
        var rows=db.queryForList("SELECT r.member_id,r.content FROM game_reviews r JOIN games g ON g.game_id=r.game_id WHERE r.review_id=? AND r.status='NORMAL' AND g.status IN ('PUBLISHED','COMING_SOON','PENDING_OFF_SHELF')",reviewId);
        if(rows.isEmpty())throw new Problem(404,"找不到可檢舉的評論");var review=rows.getFirst();
        if(number(review.get("member_id"))==actor.id())throw bad("不能檢舉自己的評論");
        String snapshot=Objects.toString(review.get("content"),"");if(snapshot.isBlank())throw bad("此評論沒有可檢舉的文字內容");
        if(count("SELECT COUNT(*) FROM dbo.ugc_reports WHERE reporter_member_id=? AND target_type='REVIEW' AND target_id=? AND status='PENDING'",actor.id(),reviewId)>0)throw new Problem(409,"你已檢舉此評論，請等候處理");
        db.update("INSERT INTO dbo.ugc_reports(reporter_member_id,target_type,target_id,snapshot_text,reason) VALUES(?,'REVIEW',?,?,?)",actor.id(),reviewId,snapshot,reason.trim());
        return Map.of("message","已送出檢舉，確認成立前不會計次或禁言");
    }

    void reviewValid(int member, int game, Review r) {
        if (r == null || r.rating() == null || r.rating() < 1 || r.rating() > 5 || r.recommended() == null)
            throw bad("請選擇 1～5 分與是否推薦");
        if (r.content() != null && r.content().length() > 5000)
            throw bad("評論最多 5000 字");
        if (!owned(member, game))
            throw new Problem(403, "須擁有遊戲才能評論");
    }

    @Transactional
    public List<Map<String, Object>> saveReview(Actor a, int member, int game, Integer id, Review r) {
        owner(a, member);
        lockMember(member);
        new gameplatform.moderation.ReviewModerationPolicy(db).ensureCanPost(member);
        reviewValid(member, game, r);
        if (id == null) {
            var old = db.queryForList("SELECT review_id,status FROM game_reviews WHERE member_id=? AND game_id=?",
                    member, game);
            if (!old.isEmpty()) {
                if (!"DELETED".equals(old.getFirst().get("status")))
                    throw new Problem(409, "此遊戲已有評論");
                id = number(old.getFirst().get("review_id"));
            }
        }
        if (id == null)
            db.update("INSERT INTO game_reviews(member_id,game_id,rating,is_recommended,content) VALUES(?,?,?,?,?)",
                    member, game, r.rating(), r.recommended(), r.content());
        else if (db.update(
                "UPDATE game_reviews SET rating=?,is_recommended=?,content=?,status='NORMAL',updated_at=CURRENT_TIMESTAMP WHERE review_id=? AND member_id=? AND game_id=? AND status IN ('NORMAL','DELETED')",
                r.rating(), r.recommended(), r.content(), id, member, game) != 1)
            throw new Problem(403, "不能修改別人的評論或被隱藏的評論");
        return reviews(game, a);
    }

    @Transactional
    public List<Map<String, Object>> updateReview(Actor a, int member, int id, Review r) {
        owner(a, member);
        var x = row("SELECT game_id FROM game_reviews WHERE review_id=? AND member_id=?", id, member);
        return saveReview(a, member, number(x.get("game_id")), id, r);
    }

    @Transactional
    public void deleteReview(Actor a, int member, int id) {
        owner(a, member);
        if (db.update(
                "UPDATE game_reviews SET status='DELETED',updated_at=CURRENT_TIMESTAMP WHERE review_id=? AND member_id=? AND status='NORMAL'",
                id, member) != 1)
            throw new Problem(404, "找不到你的評論");
    }
}
