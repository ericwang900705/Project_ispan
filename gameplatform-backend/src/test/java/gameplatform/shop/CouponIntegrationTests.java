package gameplatform.shop;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;

/** 以實際 JDBC/H2 驗證領取、會員隔離、範圍計價與額度；不呼叫金流。 */
class CouponIntegrationTests {
    JdbcTemplate db; CouponService coupons; ShopService shop; TransactionTemplate tx;
    Actor member=new Actor(1,"MEMBER","member","");
    @BeforeEach void setup() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MSSQLServer;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        db=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        db.execute("CREATE TABLE members(member_id INT PRIMARY KEY,username VARCHAR(50),is_publisher INT,failed_login_count INT)");
        db.execute("CREATE TABLE tags(tag_id INT PRIMARY KEY,tag_name VARCHAR(50))");
        db.execute("CREATE TABLE game_tags(game_id INT,tag_id INT)");
        db.execute("CREATE TABLE coupons(coupon_id INT PRIMARY KEY,coupon_name VARCHAR(100),discount_type VARCHAR(10),discount_value DECIMAL(10,2),min_spend DECIMAL(10,2),total_limit INT,per_member_limit INT,used_count INT,status VARCHAR(20),start_at TIMESTAMP,expires_at TIMESTAMP)");
        db.execute("CREATE TABLE coupon_rules(coupon_id INT PRIMARY KEY,scope_type VARCHAR(20),publisher_member_id INT,tag_id INT)");
        db.execute("CREATE TABLE member_coupon_claims(member_id INT,coupon_id INT,received_at TIMESTAMP,PRIMARY KEY(member_id,coupon_id))");
        db.execute("CREATE TABLE orders(order_id INT PRIMARY KEY,member_id INT,order_status VARCHAR(20))");
        db.execute("CREATE TABLE order_items(order_id INT,coupon_id INT)");
        db.update("INSERT INTO members VALUES(1,'member',0,0),(2,'other',0,0),(7,'publisher',1,0)");
        db.update("INSERT INTO tags VALUES(3,'動作')");db.update("INSERT INTO game_tags VALUES(10,3)");
        db.update("INSERT INTO coupons VALUES(1,'測試券','RATE',0.8,0,NULL,10,0,'ACTIVE',?,?)",LocalDateTime.now().minusDays(1),LocalDateTime.now().plusDays(1));
        coupons=new CouponService(db);shop=new ShopService(db);
    }
    void claim() { tx.executeWithoutResult(s->coupons.claim(member,1)); }
    Map<String,Object> item(int game,int publisher,String price) {
        return new HashMap<>(Map.of("game_id",game,"publisher_member_id",publisher,"price",new BigDecimal(price),"coupon_id",1));
    }
    @Test void claimMovesFromUnclaimedToMineAndIsIsolated() {
        assertEquals(1,coupons.center(member).size());claim();
        assertEquals(0,coupons.center(member).size());assertEquals(1,coupons.mine(member).size());
        assertEquals(0,coupons.mine(new Actor(2,"MEMBER","other","")).size());
        assertEquals(409,assertThrows(Problem.class,this::claim).httpStatus());
    }
    @Test void staffAndAnonymousCannotClaimOrRead() {
        assertEquals(403,assertThrows(Problem.class,()->coupons.claim(new Actor(3,"STAFF","staff",""),1)).httpStatus());
        assertEquals(403,assertThrows(Problem.class,()->coupons.mine(null)).httpStatus());
    }
    @Test void cannotApplyUnclaimedCoupon() {
        assertThrows(Problem.class,()->shop.price(List.of(item(10,7,"500")),1,true));
    }
    @Test void publisherScopeOnlyDiscountsMatchingGames() {
        claim();db.update("INSERT INTO coupon_rules VALUES(1,'PUBLISHER',7,NULL)");
        var price=shop.price(List.of(item(10,7,"500"),item(11,8,"1000")),1,true);
        assertEquals(0,new BigDecimal("1500").compareTo(price.subtotal()));
        assertEquals(0,new BigDecimal("100").compareTo(price.discount()));
        assertEquals(0,price.discounts().get(1).signum());
    }
    @Test void minimumCountsEligibleSubtotalOnly() {
        claim();db.update("INSERT INTO coupon_rules VALUES(1,'TAG',NULL,3)");
        db.update("UPDATE coupons SET min_spend=600");
        assertThrows(Problem.class,()->shop.price(List.of(item(10,7,"500"),item(11,8,"1000")),1,true));
        var boundary=shop.price(List.of(item(10,7,"600"),item(11,8,"1000")),1,true);
        assertEquals(0,new BigDecimal("120").compareTo(boundary.discount()));
    }
    @Test void fixedDiscountIsCappedAtEligibleSubtotal() {
        claim();db.update("INSERT INTO coupon_rules VALUES(1,'TAG',NULL,3)");
        db.update("UPDATE coupons SET discount_type='AMOUNT',discount_value=900");
        var price=shop.price(List.of(item(10,7,"500"),item(11,8,"1000")),1,true);
        assertEquals(0,new BigDecimal("500").compareTo(price.discount()));
        assertEquals(0,price.discounts().get(1).signum());
    }
    @Test void wrongScopeExpiredAndStoppedAreRejected() {
        claim();db.update("INSERT INTO coupon_rules VALUES(1,'PUBLISHER',7,NULL)");
        assertThrows(Problem.class,()->shop.price(List.of(item(11,8,"1000")),1,true));
        db.update("UPDATE coupons SET expires_at=?",LocalDateTime.now().minusMinutes(1));
        assertEquals(0,coupons.usable(member).size());assertThrows(Problem.class,()->shop.price(List.of(item(10,7,"500")),1,true));
        db.update("UPDATE coupons SET expires_at=?,status='INACTIVE'",LocalDateTime.now().plusDays(1));
        assertThrows(Problem.class,()->shop.price(List.of(item(10,7,"500")),1,true));
    }
    @Test void pendingUseReservesQuotaAndCancellationReleasesIt() {
        claim();db.update("UPDATE coupons SET per_member_limit=1,total_limit=1,used_count=1");
        db.update("INSERT INTO orders VALUES(1,1,'PENDING')");db.update("INSERT INTO order_items VALUES(1,1)");
        assertEquals(0,coupons.usable(member).size());
        db.update("UPDATE orders SET order_status='CANCELLED'");db.update("UPDATE coupons SET used_count=0");
        assertEquals(1,coupons.usable(member).size());
    }
    @Test void simultaneousClaimCreatesOneRecord() throws Exception {
        var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<Boolean> action=()->{start.await();try {claim();return true;}catch(Problem e){assertEquals(409,e.httpStatus());return false;}};
            var a=executor.submit(action);var b=executor.submit(action);start.countDown();
            assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
            assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM member_coupon_claims",Integer.class));
        } finally {executor.shutdownNow();}
    }
    @Test void totalQuotaExhaustionIsNotShownAsUsableAndRecoversAfterRelease() {
        claim();db.update("UPDATE coupons SET total_limit=1,used_count=1");
        var exhausted=coupons.mine(member).getFirst();
        assertEquals("UNAVAILABLE",exhausted.get("status"));
        assertEquals("總使用額度已滿",exhausted.get("unavailableReason"));
        assertEquals(0,coupons.usable(member).size());
        db.update("UPDATE coupons SET used_count=0");
        assertEquals("UNUSED",coupons.mine(member).getFirst().get("status"));
        assertEquals(1,coupons.usable(member).size());
    }
}
