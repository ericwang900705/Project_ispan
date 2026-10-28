-- 03｜示範帳號、訂單、遊戲庫、評論、檢舉、時數及操作紀錄；先完成 01、02。
-- 只供全新資料庫第一次載入。若已有帳號／訂單，會停止，避免覆蓋原資料。
-- 唯一最高管理員 hero_admin；所有本檔帳號密碼均為 HeroDemo2026!。
-- 付款、登入與操作紀錄均為示範資料，沒有向綠界建立真實交易或寄出驗證信。
USE [GamePlatformDB];
GO
SET NOCOUNT ON;
SET XACT_ABORT ON;
DECLARE @PasswordHash VARCHAR(255)='$2a$10$DrkdUKkiWKZXAmD/ErO98u05tXJ1PCMm7Nlx0kBpMUW4pAsmz42c2';
DECLARE @now DATETIME2=SYSDATETIME();
DECLARE @FrontendBase VARCHAR(200)='http://127.0.0.1:5173';
DECLARE @FixedAdminAccount VARCHAR(50)='hero_admin',@NewAdmin BIT=1;
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @lock INT;
 EXEC @lock=sys.sp_getapplock @Resource='HERO_DEMO_SEED',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
 IF @lock<0 THROW 51420,N'示範資料忙碌，請稍後重試。',1;
 IF EXISTS(SELECT 1 FROM dbo.admin) OR EXISTS(SELECT 1 FROM dbo.orders)
  OR EXISTS(SELECT 1 FROM dbo.members WHERE username<>'demo_studio') OR EXISTS(SELECT 1 FROM dbo.game_reviews)
  THROW 51421,N'此檔只供全新資料庫首次載入；已存在帳號或資料，本次未變更。',1;
 DECLARE @AdminRole INT=(SELECT admin_role_id FROM dbo.admin_roles WHERE role_code='ADMIN');
 IF @AdminRole IS NULL THROW 51422,N'請先完成 01 的角色初始化。',1;
 DECLARE @PublisherId INT=(SELECT member_id FROM dbo.members WHERE username='demo_studio' AND is_publisher=1);
 IF @PublisherId IS NULL THROW 51423,N'請先執行 02，建立示範發行商及六款遊戲。',1;
 INSERT dbo.admin(admin_account,admin_role_id,admin_name,email,password_hash,status,created_at)
 VALUES(@FixedAdminAccount,@AdminRole,N'HERO 最高管理員','hero_admin@hero.example.test',@PasswordHash,'ACTIVE',DATEADD(DAY,-120,@now));
 DECLARE @AdminId INT=CONVERT(INT,SCOPE_IDENTITY());
 CREATE TABLE #MiniNames(name NVARCHAR(150) PRIMARY KEY,price DECIMAL(10,2),genre NVARCHAR(50),cover VARCHAR(40),description NVARCHAR(500));
 INSERT #MiniNames VALUES
 (N'貪食蛇',49,N'策略','snake.svg',N'方向鍵或 WASD 引導貪食蛇，吃下食物並挑戰最高分。'),
 (N'數字合成 2048',49,N'策略','2048.svg',N'合併相同數字，挑戰 2048。支援方向鍵與手機滑動。'),
 (N'踩地雷',49,N'策略','minesweeper.svg',N'根據數字線索找出安全格子，左鍵翻開、右鍵插旗。'),
 (N'打磚塊',69,N'動作冒險','breakout.svg',N'移動擋板反彈球，清除所有磚塊。'),
 (N'記憶翻牌',49,N'策略','memory.svg',N'記住卡片位置，一次翻兩張，配對所有圖案。'),
 (N'火柴人格鬥：霓虹對決',99,N'動作冒險','stickman.svg',N'霓虹競技場火柴人格鬥；A／D 移動、W 跳、J 拳、K 踢、L 防禦、U 氣功波。');

 IF (SELECT COUNT(*) FROM dbo.games g JOIN #MiniNames n ON n.name=g.game_name AND g.member_id=@PublisherId)<>6
  THROW 51424,N'六款小遊戲尚未齊全，請先執行 02。',1;
 -- 以下均為本次新建的展示資料，不冒充平台真實歷史。
 DECLARE @Accounts TABLE(account VARCHAR(30) PRIMARY KEY,email VARCHAR(100),display_name NVARCHAR(50));
 INSERT @Accounts VALUES
 ('demo_studio','demo_studio@hero.example.test',N'HERO 示範工作室'),
 ('demo_aurora','demo_aurora@hero.example.test',N'晨光玩家'),
 ('demo_lin','demo_lin@hero.example.test',N'林間旅人'),
 ('demo_miko','demo_miko@hero.example.test',N'米可'),
 ('demo_kai','demo_kai@hero.example.test',N'凱'),
 ('demo_neo','demo_neo@hero.example.test',N'尼歐'),
 ('demo_sora','demo_sora@hero.example.test',N'天空');
 INSERT dbo.members(username,email,is_publisher,created_at)
 SELECT account,email,0,DATEADD(DAY,-30,@now) FROM @Accounts v WHERE NOT EXISTS(SELECT 1 FROM dbo.members m WHERE m.username=v.account);
 UPDATE dbo.members SET is_publisher=1,company_name=N'HERO 示範工作室',publisher_description=N'展示 CART 六款小遊戲的示範發行商。',failed_login_count=0,locked_until=NULL WHERE member_id=@PublisherId;
 INSERT dbo.password_history(member_id,password_hash,created_at)
 SELECT m.member_id,@PasswordHash,@now FROM dbo.members m JOIN @Accounts a ON a.account=m.username;
 INSERT dbo.member_verifications(email,verification_token,sent_at,expires_at,is_verified,verified_at)
 SELECT email,CONCAT('HERO-DEMO-',NEWID()),DATEADD(MINUTE,-5,@now),DATEADD(HOUR,1,@now),1,@now FROM @Accounts;
 DECLARE @Staff TABLE(account VARCHAR(50),[role] VARCHAR(50),name NVARCHAR(50));
 INSERT @Staff VALUES('demo_staff','STAFF',N'示範營運人員'),('demo_reviewer','REVIEWER',N'示範審核人員'),('demo_support','SUPPORT',N'示範客服人員');
 INSERT dbo.admin(admin_account,admin_role_id,admin_name,email,password_hash,status)
 SELECT s.account,r.admin_role_id,s.name,s.account+'@hero.example.test',@PasswordHash,'ACTIVE'
 FROM @Staff s JOIN dbo.admin_roles r ON r.role_code=s.[role];
 INSERT dbo.admin_user_permissions(admin_id,permission_id,granted_by_admin_id)
 SELECT a.admin_id,p.permission_id,@AdminId FROM dbo.admin a CROSS JOIN dbo.admin_permissions p
 WHERE a.admin_account='demo_staff' AND p.permission_code IN('DASHBOARD_VIEW','MEMBER_SUSPEND','ORDER_VIEW','REFUND_MANAGE','PROMOTION_MANAGE','COUPON_MANAGE','TAG_MANAGE','COMMENT_REVIEW','TICKET_HANDLE','OPERATION_LOG_VIEW','LOGIN_LOG_VIEW');
 INSERT dbo.tags(tag_name) SELECT DISTINCT genre FROM #MiniNames n WHERE NOT EXISTS(SELECT 1 FROM dbo.tags t WHERE t.tag_name=n.genre);
 INSERT dbo.games(member_id,game_name,price,status,description,cover_url,release_date)
 SELECT @PublisherId,name,price,CASE WHEN name=N'火柴人格鬥：霓虹對決' THEN 'DRAFT' ELSE 'PUBLISHED' END,description,@FrontendBase+'/minigames/covers/'+cover,DATEADD(DAY,-10,@now)
 FROM #MiniNames n WHERE NOT EXISTS(SELECT 1 FROM dbo.games g WHERE g.game_name=n.name);
 UPDATE g SET member_id=@PublisherId,price=n.price,status=CASE WHEN n.name=N'火柴人格鬥：霓虹對決' THEN 'DRAFT' ELSE 'PUBLISHED' END,scheduled_off_shelf_at=NULL,description=n.description,cover_url=@FrontendBase+'/minigames/covers/'+n.cover
 FROM dbo.games g JOIN #MiniNames n ON n.name=g.game_name;
 INSERT dbo.game_tags(game_id,tag_id)
 SELECT g.game_id,t.tag_id FROM #MiniNames n JOIN dbo.games g ON g.game_name=n.name JOIN dbo.tags t ON t.tag_name=n.genre
 WHERE NOT EXISTS(SELECT 1 FROM dbo.game_tags x WHERE x.game_id=g.game_id AND x.tag_id=t.tag_id);
 CREATE TABLE #GameOrder(n INT PRIMARY KEY,game_id INT,price DECIMAL(10,2));
 INSERT #GameOrder SELECT ROW_NUMBER() OVER(ORDER BY n.name),g.game_id,g.price FROM #MiniNames n JOIN dbo.games g ON g.game_name=n.name WHERE g.status='PUBLISHED';
 -- 示範訂單含已付款、待付款及已退款；沒有對綠界建立交易。
 DECLARE @MemberId INT,@Account VARCHAR(30),@GameId INT,@Price DECIMAL(10,2),@Num INT,@OrderId INT,@ItemId INT,@Minutes INT,@OrderState VARCHAR(20);
 DECLARE members CURSOR LOCAL FAST_FORWARD FOR SELECT m.member_id,m.username FROM dbo.members m JOIN @Accounts a ON a.account=m.username WHERE m.is_publisher=0 ORDER BY m.member_id;
 OPEN members; FETCH NEXT FROM members INTO @MemberId,@Account;
 WHILE @@FETCH_STATUS=0
 BEGIN
  SET @Num=1;
  WHILE @Num<=5
  BEGIN
   SELECT @GameId=game_id,@Price=price FROM #GameOrder WHERE n=@Num;
   SET @Minutes=((@MemberId*37+@Num*53)%720)+25;
   SET @OrderState=CASE WHEN @Account='demo_sora' AND @Num=5 THEN 'PENDING' WHEN @Account='demo_lin' AND @Num=5 THEN 'REFUNDED' ELSE 'PAID' END;
   INSERT dbo.orders(order_no,member_id,subtotal_amount,discount_amount,total_amount,order_status,payment_type,payment_status,payment_environment,created_at,paid_at,ecpay_trade_no)
    VALUES(CONCAT('DEMO-',@Account,'-',@Num),@MemberId,@Price,0,@Price,@OrderState,'Credit',@OrderState,'TEST',DATEADD(DAY,-7,@now),CASE WHEN @OrderState='PENDING' THEN NULL ELSE DATEADD(DAY,-7,@now) END,CASE WHEN @OrderState='PENDING' THEN NULL ELSE CONCAT('DM',@MemberId,'G',@Num) END);
   SET @OrderId=CONVERT(INT,SCOPE_IDENTITY());
   INSERT dbo.order_items(order_id,game_id,quantity,unit_price,discount_amount,is_gift) VALUES(@OrderId,@GameId,1,@Price,0,0);
   SET @ItemId=CONVERT(INT,SCOPE_IDENTITY());
   IF @OrderState<>'PENDING'
   BEGIN
   INSERT dbo.member_games(member_id,game_id,order_item_id,obtained_source,total_play_minutes,is_active,obtained_at,last_played_at,revoked_at)
    VALUES(@MemberId,@GameId,@ItemId,'PURCHASE',@Minutes,CASE WHEN @OrderState='REFUNDED' THEN 0 ELSE 1 END,DATEADD(DAY,-7,@now),DATEADD(HOUR,-@Num,@now),CASE WHEN @OrderState='REFUNDED' THEN @now ELSE NULL END);
   INSERT dbo.play_records(member_id,game_id,started_at,ended_at,play_seconds)
    VALUES(@MemberId,@GameId,DATEADD(MINUTE,-@Minutes,DATEADD(HOUR,-@Num,@now)),DATEADD(HOUR,-@Num,@now),@Minutes*60);
   END;
   IF @Num<=4
    INSERT dbo.game_reviews(member_id,game_id,rating,is_recommended,content,status,created_at)
    VALUES(@MemberId,@GameId,CASE WHEN @Account IN('demo_kai','demo_neo') THEN 2 ELSE 4+(@Num%2) END,CASE WHEN @Account IN('demo_kai','demo_neo') THEN 0 ELSE 1 END,
      CASE WHEN @Account='demo_kai' AND @Num<=2 THEN N'一直貼相同廣告內容，歡迎到外部網站購買代練服務。'
      WHEN @Account='demo_neo' AND @Num IN(3,4) THEN N'重複廣告內容，已由示範管理員確認違規。'
      WHEN @Num=1 THEN N'操作直覺，適合在休息時間挑戰自己的紀錄。'
      WHEN @Num=2 THEN N'規則容易理解，玩起來很順暢，希望之後增加更多關卡。'
      WHEN @Num=3 THEN N'畫面簡潔，鍵盤操作反應很快，值得推薦。'
      ELSE N'朋友推薦來玩，遊戲節奏剛好，也很適合反覆挑戰。' END,
      CASE WHEN @Account='demo_neo' AND @Num IN(3,4) THEN 'HIDDEN' ELSE 'NORMAL' END,DATEADD(DAY,-2,@now));
   SET @Num+=1;
  END;
  INSERT dbo.member_login_logs(member_id,ip_address,login_status,login_at) VALUES(@MemberId,CONCAT('192.0.2.',@MemberId%200+1),'SUCCESS',DATEADD(HOUR,-1,@now)),(@MemberId,'192.0.2.200','FAILED',DATEADD(DAY,-1,@now));
  FETCH NEXT FROM members INTO @MemberId,@Account;
 END;
 CLOSE members; DEALLOCATE members;
 -- 示範檢舉：兩筆待辦及兩筆已成立，評論快照與原文一致。
 DECLARE @Reporter INT=(SELECT member_id FROM dbo.members WHERE username='demo_aurora');
 INSERT dbo.ugc_reports(reporter_member_id,target_type,target_id,snapshot_text,reason,status,created_at,admin_id,reviewed_at,review_comment)
 SELECT @Reporter,'REVIEW',r.review_id,r.content,N'疑似重複張貼廣告，請協助核對。',CASE WHEN m.username='demo_neo' THEN 'REMOVED' ELSE 'PENDING' END,
  DATEADD(HOUR,-12,@now),CASE WHEN m.username='demo_neo' THEN @AdminId ELSE NULL END,CASE WHEN m.username='demo_neo' THEN @now ELSE NULL END,CASE WHEN m.username='demo_neo' THEN N'[DEMO] 已確認重複廣告並軟刪除。' ELSE NULL END
 FROM dbo.game_reviews r JOIN dbo.members m ON m.member_id=r.member_id
 WHERE (m.username='demo_kai' AND r.content LIKE N'一直貼相同廣告%') OR (m.username='demo_neo' AND r.status='HIDDEN');
 INSERT dbo.review_violations(review_id,member_id,report_id,admin_id,reason,confirmed_at)
 SELECT r.review_id,r.member_id,p.report_id,@AdminId,N'[DEMO] 示範重複廣告違規',@now
 FROM dbo.game_reviews r JOIN dbo.ugc_reports p ON p.target_type='REVIEW' AND p.target_id=r.review_id AND p.status='REMOVED'
 JOIN dbo.members m ON m.member_id=r.member_id WHERE m.username='demo_neo';
 INSERT dbo.member_review_mutes(member_id,muted_until,reason) SELECT member_id,DATEADD(HOUR,6,@now),N'[DEMO] 30 天內兩則不同評論違規，禁言 6 小時' FROM dbo.members WHERE username='demo_neo';
 -- 優惠活動列表用四種狀態；每款活動固定 7 天。
 INSERT dbo.game_discounts(game_id,discount_percent,start_at,end_at)
 SELECT game_id,CASE n WHEN 1 THEN 20 WHEN 2 THEN 15 WHEN 3 THEN 30 ELSE 10 END,
  DATEADD(DAY,CASE n WHEN 1 THEN -2 WHEN 2 THEN 1 WHEN 3 THEN -14 ELSE -50 END,@now),
  DATEADD(DAY,CASE n WHEN 1 THEN 5 WHEN 2 THEN 8 WHEN 3 THEN -7 ELSE -43 END,@now)
 FROM #GameOrder WHERE n<=4;
 -- 示範優惠券：部分會員已領取，其他會員仍可從未領取清單領取。
 INSERT dbo.coupons(coupon_name,discount_type,discount_value,min_spend,total_limit,per_member_limit,status,start_at,expires_at)
 VALUES(N'[DEMO] 小遊戲八折券','RATE',0.8,50,100,1,'ACTIVE',DATEADD(DAY,-1,@now),DATEADD(DAY,20,@now)),
       (N'[DEMO] 工作室折抵 20 元','AMOUNT',20,100,NULL,1,'ACTIVE',DATEADD(DAY,-1,@now),DATEADD(DAY,30,@now));
 INSERT dbo.coupon_rules(coupon_id,scope_type,tag_id,publisher_member_id)
 SELECT c.coupon_id,CASE WHEN c.discount_type='RATE' THEN 'TAG' ELSE 'PUBLISHER' END,
 CASE WHEN c.discount_type='RATE' THEN (SELECT tag_id FROM dbo.tags WHERE tag_name=N'策略') ELSE NULL END,
 CASE WHEN c.discount_type='AMOUNT' THEN @PublisherId ELSE NULL END FROM dbo.coupons c WHERE LEFT(c.coupon_name,6)=N'[DEMO]';
 INSERT dbo.member_coupon_claims(member_id,coupon_id)
 SELECT m.member_id,c.coupon_id FROM dbo.members m CROSS JOIN dbo.coupons c
 WHERE m.username IN('demo_aurora','demo_lin','demo_miko') AND c.coupon_name=N'[DEMO] 小遊戲八折券';
 -- 一筆待處理退款、一筆已退款，方便展示會員訂單與後台處理流程。
 DECLARE @RefundOrder INT,@RefundMember INT,@RefundState VARCHAR(12),@RefundTicket INT,@RefundNo VARCHAR(30),@RefundCategory INT;
 SELECT @RefundCategory=category_id FROM dbo.customer_service_categories WHERE category_name=N'退款問題';
 IF @RefundCategory IS NULL THROW 51408,N'缺少退款問題分類，請先完成客服結構初始化。',1;
 DECLARE refunds CURSOR LOCAL FAST_FORWARD FOR
 SELECT o.order_id,o.member_id,CASE WHEN o.order_status='REFUNDED' THEN 'REFUNDED' ELSE 'PENDING' END
 FROM dbo.orders o WHERE o.order_no IN('DEMO-demo_lin-5','DEMO-demo_miko-5');
 OPEN refunds; FETCH NEXT FROM refunds INTO @RefundOrder,@RefundMember,@RefundState;
 WHILE @@FETCH_STATUS=0
 BEGIN
  SET @RefundNo=CONCAT('CS-DEMO-',@RefundOrder);
  INSERT dbo.customer_service_tickets(ticket_no,member_id,category_id,subject,content,status,admin_id,priority,created_at,updated_at)
  VALUES(@RefundNo,@RefundMember,@RefundCategory,CONCAT(N'退款申請｜訂單 #',@RefundOrder),N'示範退款申請：買錯遊戲，希望協助核對。',
   CASE WHEN @RefundState='REFUNDED' THEN 'CLOSED' ELSE 'OPEN' END,CASE WHEN @RefundState='REFUNDED' THEN @AdminId ELSE NULL END,'NORMAL',DATEADD(HOUR,-2,@now),@now);
  SET @RefundTicket=CONVERT(INT,SCOPE_IDENTITY());
  INSERT dbo.support_order_links(ticket_id,member_id,order_id,linked_by_member_id) VALUES(@RefundTicket,@RefundMember,@RefundOrder,@RefundMember);
  INSERT dbo.refund_requests(order_id,member_id,ticket_id,reason,status,requested_at,processed_at,processed_by_admin_id,decision_reason,refund_mode)
  VALUES(@RefundOrder,@RefundMember,@RefundTicket,N'買錯遊戲，希望協助核對。',@RefundState,DATEADD(HOUR,-2,@now),
   CASE WHEN @RefundState='REFUNDED' THEN @now ELSE NULL END,CASE WHEN @RefundState='REFUNDED' THEN @AdminId ELSE NULL END,
   CASE WHEN @RefundState='REFUNDED' THEN N'[DEMO] 已核對並完成整筆測試退款。' ELSE NULL END,CASE WHEN @RefundState='REFUNDED' THEN 'TEST' ELSE NULL END);
  INSERT dbo.customer_service_messages(ticket_id,sender_type,sender_member_id,message_content) VALUES(@RefundTicket,'MEMBER',@RefundMember,N'我買錯遊戲了，可以協助核對退款嗎？');
  IF @RefundState='REFUNDED'
   INSERT dbo.customer_service_messages(ticket_id,sender_type,admin_id,message_content) VALUES(@RefundTicket,'ADMIN',@AdminId,N'[DEMO] 測試退款已完成，對應遊戲權益已撤銷。');
  INSERT dbo.notifications(recipient_type,recipient_id,event_type,title,body,target_path)
   VALUES('MEMBER',@RefundMember,'TICKET_REPLY',CASE WHEN @RefundState='REFUNDED' THEN N'測試退款已完成' ELSE N'退款申請已建立' END,@RefundNo,CONCAT('/member/support/',@RefundTicket));
  FETCH NEXT FROM refunds INTO @RefundOrder,@RefundMember,@RefundState;
 END;
 CLOSE refunds; DEALLOCATE refunds;
 -- 明確標示示範來源的操作紀錄。
 DECLARE @i INT=1;
 WHILE @i<=24
 BEGIN
  INSERT dbo.admin_operation_logs(admin_id,action_code,target_id,detail,created_at)
   VALUES(@AdminId,CASE @i%4 WHEN 0 THEN 'DEMO_GAME_REVIEW' WHEN 1 THEN 'DEMO_TAG_CREATE' WHEN 2 THEN 'DEMO_COUPON_CREATE' ELSE 'DEMO_MEMBER_VIEW' END,@i,
    CONCAT(N'[DEMO] 示範資料：',CASE @i%4 WHEN 0 THEN N'完成遊戲上架資料核對' WHEN 1 THEN N'整理平台標籤' WHEN 2 THEN N'核對優惠券使用規則' ELSE N'檢視會員資料與遊戲庫' END),DATEADD(HOUR,-@i,@now));
  SET @i+=1;
 END;
 INSERT dbo.admin_operation_logs(admin_id,action_code,target_id,detail) VALUES(@AdminId,'DEMO_SEED',@AdminId,N'[DEMO] 初始化示範會員、遊戲庫、評論、優惠活動與展示紀錄；非實際營運歷史。');
 IF (SELECT COUNT(*) FROM dbo.admin WHERE admin_role_id=@AdminRole)<>1 THROW 51406,N'最高管理員數量不等於一，已回滾。',1;
 DROP TABLE #GameOrder;
 DROP TABLE #MiniNames;
 COMMIT TRANSACTION;
 SELECT a.admin_account,a.admin_name,r.role_code,a.status,CASE WHEN a.admin_id=@AdminId AND @NewAdmin=0 THEN N'保留原密碼' ELSE N'HeroDemo2026!' END AS login_password
 FROM dbo.admin a JOIN dbo.admin_roles r ON r.admin_role_id=a.admin_role_id WHERE a.admin_id=@AdminId OR a.admin_account IN('demo_staff','demo_reviewer','demo_support') ORDER BY a.admin_id;
 SELECT m.username,m.email,N'HeroDemo2026!' AS login_password,m.is_publisher FROM dbo.members m JOIN @Accounts a ON a.account=m.username ORDER BY m.member_id;
 SELECT N'示範資料完成；操作紀錄與付款資料為展示用，未對綠界建立交易。' AS result;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
GO
