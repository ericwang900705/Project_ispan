INSERT INTO members(username) VALUES ('會員 Aki'),('會員 小楓');
INSERT INTO admin(admin_account) VALUES ('管理者 Aki'),('客服 小林'),('客服 小陳'),('審核人員 小楓');
INSERT INTO games(game_name) VALUES ('星際旅人'),('霓虹競速'),('森林物語');
INSERT INTO customer_service_categories(category_name,description) VALUES ('遊戲','遊戲啟動與遊戲庫相關問題'),('帳戶','會員帳戶相關問題'),('其他','其他需要協助的問題');

-- 發行商是會員身分；只補DEMO帳號與原有遊戲歸屬，不新增TABLE。
INSERT INTO members(username,is_publisher) VALUES ('publisher1',TRUE),('publisher2',TRUE);
UPDATE games SET member_id=3 WHERE game_id=1;
UPDATE games SET member_id=4 WHERE game_id=2;

INSERT INTO tags(tag_name) VALUES ('RPG'),('多人遊戲'),('冒險'),('恐怖'),('益智'),('動作'),('策略'),('模擬');
UPDATE games SET description='探索星際世界的冒險遊戲。',price=390 WHERE game_id=1;
INSERT INTO game_builds(game_id,version,file_url) VALUES(1,'1.0.0','https://example.com/demo/star-traveler.zip');
INSERT INTO orders(member_id,order_status) VALUES(1,'PAID'),(1,'PAID'),(2,'PAID'),(2,'PENDING');
INSERT INTO order_items(order_id,game_id) VALUES(1,1),(2,1),(3,1),(4,1);

-- 完整上架檢核示範：封面、標籤與宣傳素材均設定。
UPDATE games SET cover_url='https://example.com/demo/star-cover.png' WHERE game_id=1;
INSERT INTO game_tags(game_id,tag_id) VALUES(1,1);
INSERT INTO game_media(game_id,media_type,media_url,display_order) VALUES(1,'IMAGE','https://example.com/demo/star-promo.png',1);

-- DEMO 訂單以成交時金額快照展示統計。
UPDATE orders SET paid_at=CURRENT_TIMESTAMP WHERE order_status='PAID';
UPDATE order_items SET unit_price=390,discount_amount=0 WHERE game_id=1;

-- 商城示範：獨立的公開遊戲，不改動原有三款草稿或測試發行商。
-- GitHub 公開查詢只顯示 PUBLISHED / COMING_SOON，因此 Demo 至少要有一款已上架遊戲。
INSERT INTO members(username,is_publisher) VALUES ('HERO 示範發行商', TRUE);
INSERT INTO games(game_name,member_id,price,description,cover_url,release_date,status)
VALUES ('HERO 示範冒險', 5, 199, '這是展示 GitHub 商城列表、搜尋與詳情 API 的示範遊戲。', '/hero-demo-cover.svg', CURRENT_TIMESTAMP, 'PUBLISHED');
INSERT INTO game_builds(game_id,version,file_url,status) VALUES(4,'1.0.0','https://example.com/hero-demo.zip','ACTIVE');
