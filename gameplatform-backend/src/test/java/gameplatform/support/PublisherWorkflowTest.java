package gameplatform.support;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import gameplatform.support.model.*;
import gameplatform.support.model.PublisherModels.*;
import gameplatform.support.service.PublisherService;

class PublisherWorkflowTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate db;
    PublisherService service;
    Actor publisher=new Actor(3,"PUBLISHER","publisher1","");
    Actor other=new Actor(4,"PUBLISHER","publisher2","");
    Actor reviewer=new Actor(4,"REVIEWER","reviewer1","");
    @Configuration @EnableTransactionManagement static class Config {
        @Bean DriverManagerDataSource source() { return new DriverManagerDataSource("jdbc:h2:mem:workflow_"+UUID.randomUUID()+";MODE=MSSQLServer;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa",""); }
        @Bean JdbcTemplate jdbc(DriverManagerDataSource source) { new ResourceDatabasePopulator(new ClassPathResource("demo-schema.sql"),new ClassPathResource("demo-data.sql")).execute(source); return new JdbcTemplate(source); }
        @Bean DataSourceTransactionManager transactions(DriverManagerDataSource source) { return new DataSourceTransactionManager(source); }
        @Bean PublisherService publisher(JdbcTemplate db) { return new PublisherService(db,Clock.fixed(Instant.parse("2026-10-08T00:00:00Z"),ZoneOffset.UTC)); }
    }
    @BeforeEach void setup() { context=new AnnotationConfigApplicationContext(Config.class);db=context.getBean(JdbcTemplate.class);service=context.getBean(PublisherService.class); }
    @AfterEach void close() { context.close(); }
    GameInput input(String name) { return new GameInput(name,new BigDecimal("199.00"),"遊戲介紹","https://example.com/cover.png",null); }
    @SuppressWarnings("unchecked") Map<String,Object> item(Object detail) { return (Map<String,Object>)((Map<String,Object>)detail).get("item"); }
    String key() { return (String)service.game(publisher,1).get("pending_request_key"); }
    @Test void incompletePreflightCannotBeBypassedThroughApi() {
        for (String sql : List.of("DELETE FROM game_tags WHERE game_id=1", "DELETE FROM game_media WHERE game_id=1", "UPDATE games SET cover_url=NULL WHERE game_id=1")) {
            db.update(sql);
            var problem=assertThrows(Problem.class,()->service.request(publisher,1,"PUBLISH",new RequestInput("直接送審")));
            assertTrue(problem.getMessage().contains("上架設定未完成"));
            assertNull(service.game(publisher,1).get("pending_review_type"));
            if (sql.contains("game_tags")) service.saveTags(publisher,1,new TagsInput(List.of(1)));
            if (sql.contains("game_media")) service.saveMedia(publisher,1,new MediaList(List.of(new MediaInput("IMAGE","https://example.com/promo.png",1))));
        }
        service.update(publisher,1,input("已完成資料"));
        service.request(publisher,1,"PUBLISH",new RequestInput("完成"));
        assertEquals("PUBLISH",service.game(publisher,1).get("pending_review_type"));
    }
    @Test void legacyInvalidUrlsAreIncompleteAndRelativeUrlsAreRejected() {
        db.update("UPDATE games SET cover_url='relative.png' WHERE game_id=1");
        @SuppressWarnings("unchecked") var checks=(List<Map<String,Object>>)service.game(publisher,1).get("publish_checks");
        assertEquals(false,checks.get(3).get("complete"));
        assertThrows(Problem.class,()->service.create(publisher,new GameInput("relative",BigDecimal.ZERO,"介紹","relative.png",null)));
        assertThrows(Problem.class,()->service.request(publisher,1,"PUBLISH",new RequestInput("")));
        assertNull(service.game(publisher,1).get("pending_review_type"));
    }
    @SuppressWarnings("unchecked") Map<String,Object> map(Object value) { return (Map<String,Object>) value; }
    long number(Map<String,Object> value,String key) { return ((Number)value.get(key)).longValue(); }
    @Test void salesUseHistoricalAmountsPaymentBoundariesAndOnlyOwnedGames() {
        db.update("DELETE FROM order_items");db.update("DELETE FROM orders");
        for(int id=101;id<=107;id++) db.update("INSERT INTO orders(order_id,member_id,order_status,paid_at) VALUES(?,?,?,?)",id,id==103?2:1,id==104?"REFUNDED":id==105?"CANCELLED":"PAID",java.sql.Timestamp.valueOf(id==107?"2026-10-09 00:00:00":id==101?"2026-10-01 00:00:00":"2026-10-08 23:59:59"));
        db.update("INSERT INTO order_items(order_id,game_id,quantity,unit_price,discount_amount) VALUES(101,1,2,100,20),(102,1,1,50,0),(103,1,1,30,0),(104,1,1,90,0),(105,1,1,1000,0),(106,2,1,9999,0),(107,1,1,700,0)");
        db.update("UPDATE games SET price=888 WHERE game_id=1");
        var result=map(service.sales(publisher,"2026-10-01","2026-10-08",null));
        var summary=map(result.get("summary"));
        assertEquals(4,number(summary,"paid_units"));assertEquals(260,number(summary,"paid_amount"));assertEquals(2,number(summary,"buyers"));
        assertEquals(1,number(summary,"refunded_units"));assertEquals(90,number(summary,"refunded_amount"));
        assertEquals(1,((List<?>)result.get("games")).size());assertEquals(2,((List<?>)result.get("daily")).size());
        assertEquals(summary,map(map(service.sales(publisher,"2026-10-01","2026-10-08",1)).get("summary")));
        assertEquals(0,number(map(map(service.sales(publisher,"2026-09-01","2026-09-30",null)).get("summary")),"paid_units"));
        assertThrows(Problem.class,()->service.sales(publisher,"2026-10-01","2026-10-08",2));
        assertThrows(Problem.class,()->service.sales(publisher,"bad","2026-10-08",null));
        assertThrows(Problem.class,()->service.sales(publisher,"2026-10-09","2026-10-08",null));
        assertThrows(Problem.class,()->service.sales(publisher,"2024-01-01","2026-10-08",null));
    }
    @Test void notificationsArePersistentOwnedAndMarkAllUsesSeenBoundary() {
        service.request(publisher,1,"PUBLISH",new RequestInput(""));
        service.decide(reviewer,1,new DecisionInput("REJECTED","請補介紹",key()));
        var first=map(service.notifications(publisher,0));assertEquals(1,number(first,"unread"));
        int id=((Number)first.get("latestId")).intValue();
        assertThrows(Problem.class,()->service.readNotification(other,id));
        service.readNotification(publisher,id);service.readNotification(publisher,id);
        assertEquals(0,number(map(service.notifications(publisher,0)),"unread"));
        service.request(publisher,1,"PUBLISH",new RequestInput(""));
        service.decide(reviewer,1,new DecisionInput("APPROVED","通過",key()));
        service.readAllNotifications(publisher,id);
        assertEquals(1,number(map(service.notifications(publisher,0)),"unread"));
        service.forceOffShelf(new Actor(1,"ADMIN","admin1",""),1,new RequestInput("違規"));
        var next=map(service.notifications(publisher,0));assertEquals(2,number(next,"unread"));
        var latest=map(((List<?>)next.get("items")).getFirst());assertEquals("OFF_SHELF",latest.get("review_type"));assertTrue(latest.get("comment").toString().contains("違規"));
        service.readAllNotifications(publisher,number(next,"latestId"));
        assertEquals(0,number(map(service.notifications(publisher,0)),"unread"));
        assertEquals(0,number(map(service.notifications(other,0)),"total"));
        assertThrows(Problem.class,()->service.notifications(reviewer,0));
        assertThrows(Problem.class,()->service.notifications(publisher,-1));
    }
    @Test void onlyEightPlatformTagsCanBeSelectedEvenIfOtherTagsExist() {
        db.update("INSERT INTO tags(tag_name) VALUES('FPS')");
        int extra=db.queryForObject("SELECT tag_id FROM tags WHERE tag_name='FPS'",Integer.class);
        @SuppressWarnings("unchecked") var tags=(List<Map<String,Object>>)service.tags(publisher);
        assertEquals(List.of("RPG","多人遊戲","冒險","恐怖","益智","動作","策略","模擬"),tags.stream().map(x->x.get("tag_name")).toList());
        assertThrows(Problem.class,()->service.saveTags(publisher,1,new TagsInput(List.of(extra))));
        assertEquals(1,((List<?>)service.game(publisher,1).get("tags")).size());
        db.update("INSERT INTO game_tags(game_id,tag_id) VALUES(1,?)",extra);
        assertEquals(1,((List<?>)service.game(publisher,1).get("tags")).size());
        service.saveTags(publisher,1,new TagsInput(List.of(1,2,3,4,5,6,7,8)));
        assertEquals(8,((List<?>)service.game(publisher,1).get("tags")).size());
    }
    @Test void buyersCountPaidDistinctPurchasersOnly() {
        assertEquals(2L,service.game(publisher,1).get("buyer_count"));
        assertEquals(2L,service.overview(publisher).get("buyerCount"));
        db.update("UPDATE orders SET order_status='REFUNDED' WHERE order_id=3");
        assertEquals(1L,service.game(publisher,1).get("buyer_count"));
        assertEquals(0L,service.game(other,2).get("buyer_count"));
    }
    @Test void createUpdateAndResourcesAreOwnedAndPersistent() {
        var created=(Map<?,?>)service.create(publisher,input("新遊戲"));int id=((Number)created.get("game_id")).intValue();
        service.update(publisher,id,input("修改遊戲"));
        service.saveTags(publisher,id,new TagsInput(List.of(1,2,1)));
        service.saveBuilds(publisher,id,new BuildsInput(List.of(new BuildInput(null,"1.0","https://example.com/build.zip",123L,"ACTIVE"))));
        service.saveMedia(publisher,id,new MediaList(List.of(new MediaInput("IMAGE","https://example.com/image.png",1),new MediaInput("VIDEO","https://example.com/video.mp4",2))));
        var saved=service.game(publisher,id);assertEquals("修改遊戲",saved.get("game_name"));assertEquals(2,((List<?>)saved.get("tags")).size());assertEquals(1,((List<?>)saved.get("builds")).size());assertEquals(2,((List<?>)saved.get("media")).size());
        assertThrows(Problem.class,()->service.update(other,id,input("偷改")));
        assertThrows(Problem.class,()->service.saveTags(other,id,new TagsInput(List.of())));
        assertThrows(Problem.class,()->service.saveMedia(other,id,new MediaList(List.of())));
    }
    @Test void publishRejectResubmitAndApproveHasHistoryAndStaleGuard() {
        service.request(publisher,1,"PUBLISH",new RequestInput("請審核"));String old=key();
        assertThrows(Problem.class,()->service.update(publisher,1,input("待審時修改")));
        assertThrows(Problem.class,()->service.saveBuilds(publisher,1,new BuildsInput(List.of())));
        assertThrows(Problem.class,()->service.request(publisher,1,"PUBLISH",new RequestInput("重複")));
        service.decide(reviewer,1,new DecisionInput("REJECTED","介紹不足",old));
        assertEquals("DRAFT",service.game(publisher,1).get("status"));
        service.update(publisher,1,input("星際旅人"));
        service.request(publisher,1,"PUBLISH",new RequestInput("已補資料"));
        assertThrows(Problem.class,()->service.decide(reviewer,1,new DecisionInput("APPROVED","過期頁面",old)));
        assertEquals("PENDING",item(service.reviewDetail(reviewer,1)).get("status"));
        service.decide(reviewer,1,new DecisionInput("APPROVED","資料完整",key()));
        var game=service.game(publisher,1);assertEquals("PUBLISHED",game.get("status"));assertNull(game.get("pending_review_type"));
        var history=(List<Map<String,Object>>)game.get("history");assertEquals(2,history.size());assertEquals("介紹不足",history.get(1).get("comment"));
        assertThrows(Problem.class,()->service.decide(reviewer,1,new DecisionInput("APPROVED","重複處理",old)));
    }
    @Test void directOffShelfRestoresWithin24HoursWithoutReview() {
        db.update("UPDATE games SET status='PUBLISHED' WHERE game_id=1");
        var game=(Map<?,?>)service.offShelf(publisher,1,new RequestInput("暫停銷售"));
        assertEquals("OFF_SHELF",game.get("status")); assertEquals(true,game.get("can_restore"));
        assertEquals(86400000L,((Number)game.get("restore_until")).longValue()-((Number)game.get("off_shelf_at")).longValue());
        assertNull(game.get("pending_review_type"));
        assertThrows(Problem.class,()->service.update(publisher,1,input("改完直接恢復")));
        assertThrows(Problem.class,()->service.request(publisher,1,"PUBLISH",new RequestInput("提前重送")));
        long deadline=((Number)game.get("restore_until")).longValue();
        assertThrows(Problem.class,()->service.offShelf(publisher,1,new RequestInput("重置倒數")));
        assertEquals(deadline,service.game(publisher,1).get("restore_until"));
        var restored=(Map<?,?>)service.restore(publisher,1);assertEquals("PUBLISHED",restored.get("status"));assertNull(restored.get("restore_until"));
        assertThrows(Problem.class,()->service.restore(publisher,1));
    }
    @Test void expirationAtExact24HoursDisablesRestoreButKeepsGameAndPurchases() {
        db.update("UPDATE games SET status='PUBLISHED' WHERE game_id=1");
        service.offShelf(publisher,1,new RequestInput(""));
        long now=Instant.parse("2026-10-08T00:00:00Z").toEpochMilli();
        db.update("UPDATE games SET restore_until=? WHERE game_id=1",now);
        assertEquals(false,service.game(publisher,1).get("can_restore"));
        assertThrows(Problem.class,()->service.restore(publisher,1));
        assertEquals("OFF_SHELF",service.game(publisher,1).get("status"));
        assertEquals(2L,service.game(publisher,1).get("buyer_count"));
        service.update(publisher,1,input("星際旅人"));
        service.request(publisher,1,"PUBLISH",new RequestInput("重新申請"));
        service.decide(reviewer,1,new DecisionInput("APPROVED","完整",key()));
        assertEquals("PUBLISHED",service.game(publisher,1).get("status"));
    }
    @Test void lastMillisecondRestoresOriginalComingSoonAndRestartKeepsDeadline() {
        db.update("UPDATE games SET status='COMING_SOON' WHERE game_id=1");
        var game=(Map<?,?>)service.offShelf(publisher,1,new RequestInput(null));
        long deadline=((Number)game.get("restore_until")).longValue();
        var restarted=new PublisherService(db,Clock.fixed(Instant.ofEpochMilli(deadline-1),ZoneOffset.UTC));
        assertEquals(true,restarted.game(publisher,1).get("can_restore"));
        assertEquals(deadline,restarted.game(publisher,1).get("restore_until"));
        assertEquals("COMING_SOON",((Map<?,?>)restarted.restore(publisher,1)).get("status"));
        assertThrows(Problem.class,()->service.offShelf(other,1,new RequestInput("偷下架")));
        assertThrows(Problem.class,()->service.restore(other,1));
    }
    @Test void adminForceOverridesRestorationAndBlocksAllPublisherBypasses() {
        db.update("UPDATE games SET status='PUBLISHED' WHERE game_id=1");
        service.offShelf(publisher,1,new RequestInput("暫停"));
        Actor admin=new Actor(1,"ADMIN","admin1","");
        service.forceOffShelf(admin,1,new RequestInput("違規行為"));
        var game=service.game(publisher,1);assertEquals("ADMIN",game.get("off_shelf_source"));assertEquals("違規行為",game.get("off_shelf_reason"));assertNull(game.get("restore_until"));assertEquals(false,game.get("can_restore"));
        assertThrows(Problem.class,()->service.restore(publisher,1));
        assertThrows(Problem.class,()->service.request(publisher,1,"PUBLISH",new RequestInput("繞過限制")));
        assertThrows(Problem.class,()->service.update(publisher,1,input("繞過修改")));
        assertThrows(Problem.class,()->service.saveTags(publisher,1,new TagsInput(List.of(1))));
        assertThrows(Problem.class,()->service.saveBuilds(publisher,1,new BuildsInput(List.of())));
        assertThrows(Problem.class,()->service.saveMedia(publisher,1,new MediaList(List.of())));
        assertEquals(2L,game.get("buyer_count"));
        assertTrue(((List<Map<String,Object>>)game.get("history")).getFirst().get("comment").toString().contains("違規行為"));
    }
    @Test void forceCancelsPendingPublicationAndOnlyAdminWithReasonCanDoIt() {
        service.request(publisher,1,"PUBLISH",new RequestInput(""));String old=key();
        for(String role:List.of("PUBLISHER","MEMBER","REVIEWER","SUPPORT")) assertThrows(Problem.class,()->service.forceOffShelf(new Actor(1,role,"x",""),1,new RequestInput("無權限")));
        Actor admin=new Actor(1,"ADMIN","admin1","");
        assertThrows(Problem.class,()->service.forceOffShelf(admin,1,new RequestInput(" ")));
        service.forceOffShelf(admin,1,new RequestInput("違規內容"));
        assertNull(service.game(publisher,1).get("pending_request_key"));
        assertThrows(Problem.class,()->service.decide(reviewer,1,new DecisionInput("APPROVED","過期頁面",old)));
        assertThrows(Problem.class,()->service.forceOffShelf(admin,1,new RequestInput("重複")));
    }
    @Test void invalidNestedResourceRollsBackTheWholeSave() {
        int before=db.queryForObject("SELECT COUNT(*) FROM game_builds WHERE game_id=1",Integer.class);
        assertThrows(Problem.class,()->service.saveBuilds(publisher,1,new BuildsInput(List.of(new BuildInput(null,"new","https://example.com/new.zip",0L,"ACTIVE"),new BuildInput(999,"bad","https://example.com/bad.zip",0L,"ACTIVE")))));
        assertEquals(before,db.queryForObject("SELECT COUNT(*) FROM game_builds WHERE game_id=1",Integer.class));
        assertThrows(Problem.class,()->service.saveMedia(publisher,1,new MediaList(List.of(new MediaInput("IMAGE","javascript:alert(1)",1)))));
    }
    @Test void missingBuildAndNonReviewersCannotSubmitOrDecide() {
        assertThrows(Problem.class,()->service.request(other,2,"PUBLISH",new RequestInput("")));
        assertThrows(Problem.class,()->service.reviews(publisher,"PENDING","",0));
        assertThrows(Problem.class,()->service.decide(new Actor(2,"SUPPORT","support1",""),1,new DecisionInput("APPROVED","非法",UUID.randomUUID().toString())));
        service.request(publisher,1,"PUBLISH",new RequestInput(""));
        assertEquals(1L,((Map<?,?>)service.reviews(reviewer,"PENDING","星際",0)).get("total"));
        assertEquals(0L,((Map<?,?>)service.reviews(reviewer,"PENDING","%",0)).get("total"));
    }

    @Test void editorDocumentSavesTogetherAndRollsBackOnInvalidTag() {
        var input = new GameInput("HERO 圖片測試",new BigDecimal("390"),"介紹",
                "/uploads/publisher/12345678-1234-1234-1234-123456789abc.png",null);
        var media = new MediaList(List.of(new MediaInput("IMAGE",input.coverUrl(),1)));
        @SuppressWarnings("unchecked")
        var saved = (Map<String,Object>) service.saveDocument(publisher,null,
                new DocumentInput(input,new TagsInput(List.of(1)),media));
        int id = ((Number)saved.get("game_id")).intValue();
        assertEquals(input.coverUrl(),service.game(publisher,id).get("cover_url"));
        assertEquals(1,((List<?>)saved.get("tags")).size());
        assertEquals(1,((List<?>)saved.get("media")).size());
        int count = db.queryForObject("SELECT COUNT(*) FROM games",Integer.class);
        assertThrows(Problem.class, () -> service.saveDocument(publisher,null,
                new DocumentInput(new GameInput("HERO 回滾測試",input.price(),input.description(),input.coverUrl(),null),new TagsInput(List.of(999999)),media)));
        assertEquals(count,db.queryForObject("SELECT COUNT(*) FROM games",Integer.class));
        assertThrows(Problem.class, () -> service.saveDocument(other,id,
                new DocumentInput(input,new TagsInput(List.of(1)),media)));
    }
}
