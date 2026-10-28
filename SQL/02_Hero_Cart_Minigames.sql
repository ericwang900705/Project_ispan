-- 02｜建立 CART 六款小遊戲與示範發行商；先完成 01。
-- 遊戲由既有前端 minigames 程式執行，並非上傳外部 ZIP。
-- 最後執行 03 才會補齊示範發行商登入密碼與驗證資料。
USE [GamePlatformDB];
GO
SET XACT_ABORT ON;
DECLARE @FrontendBase VARCHAR(200)='http://127.0.0.1:5173';
DECLARE @now DATETIME2=SYSDATETIME();
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @lock INT;
 EXEC @lock=sys.sp_getapplock @Resource='HERO_MINIGAMES_SEED',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
 IF @lock<0 THROW 51410,N'小遊戲資料忙碌，請稍後重試。',1;
 DECLARE @PublisherId INT=(SELECT member_id FROM dbo.members WHERE username='demo_studio');
 IF @PublisherId IS NULL
 BEGIN
  INSERT dbo.members(username,email,is_publisher,company_name,publisher_description,created_at)
  VALUES('demo_studio','demo_studio@hero.example.test',1,N'HERO 示範工作室',N'展示 CART 六款網頁小遊戲。',DATEADD(DAY,-120,@now));
  SET @PublisherId=CONVERT(INT,SCOPE_IDENTITY());
 END;
 CREATE TABLE #MiniNames(name NVARCHAR(150) PRIMARY KEY,price DECIMAL(10,2),genre NVARCHAR(50),cover VARCHAR(40),description NVARCHAR(500));
 INSERT #MiniNames VALUES
 (N'貪食蛇',49,N'策略','snake.svg',N'方向鍵或 WASD 引導貪食蛇，吃下食物並挑戰最高分。'),
 (N'數字合成 2048',49,N'策略','2048.svg',N'合併相同數字，挑戰 2048。支援方向鍵與手機滑動。'),
 (N'踩地雷',49,N'策略','minesweeper.svg',N'根據數字線索找出安全格子，左鍵翻開、右鍵插旗。'),
 (N'打磚塊',69,N'動作冒險','breakout.svg',N'移動擋板反彈球，清除所有磚塊。'),
 (N'記憶翻牌',49,N'策略','memory.svg',N'記住卡片位置，一次翻兩張，配對所有圖案。'),
 (N'火柴人格鬥：霓虹對決',99,N'動作冒險','stickman.svg',N'霓虹競技場火柴人格鬥；A／D 移動、W 跳、J 拳、K 踢、L 防禦、U 氣功波。');

 INSERT dbo.tags(tag_name) SELECT DISTINCT genre FROM #MiniNames n WHERE NOT EXISTS(SELECT 1 FROM dbo.tags t WHERE t.tag_name=n.genre);
 INSERT dbo.games(member_id,game_name,price,status,description,cover_url,release_date,created_at)
 SELECT @PublisherId,name,price,CASE WHEN name=N'火柴人格鬥：霓虹對決' THEN 'DRAFT' ELSE 'PUBLISHED' END,description,@FrontendBase+'/minigames/covers/'+cover,CASE WHEN name=N'火柴人格鬥：霓虹對決' THEN DATEADD(DAY,2,@now) ELSE DATEADD(DAY,-70,@now) END,DATEADD(DAY,-90,@now)
 FROM #MiniNames n WHERE NOT EXISTS(SELECT 1 FROM dbo.games g WHERE g.game_name=n.name);
 INSERT dbo.game_tags(game_id,tag_id)
 SELECT g.game_id,t.tag_id FROM #MiniNames n JOIN dbo.games g ON g.game_name=n.name AND g.member_id=@PublisherId JOIN dbo.tags t ON t.tag_name=n.genre
 WHERE NOT EXISTS(SELECT 1 FROM dbo.game_tags x WHERE x.game_id=g.game_id AND x.tag_id=t.tag_id);
 INSERT dbo.game_media(game_id,media_type,media_url,display_order)
 SELECT g.game_id,'IMAGE',g.cover_url,1 FROM dbo.games g WHERE g.game_name=N'火柴人格鬥：霓虹對決' AND g.member_id=@PublisherId
 AND NOT EXISTS(SELECT 1 FROM dbo.game_media x WHERE x.game_id=g.game_id);
 INSERT dbo.game_builds(game_id,version,file_url,file_size,status)
 SELECT g.game_id,'demo-1.0.0',@FrontendBase+'/minigames/lifecycle-demo.zip',28223,'ACTIVE' FROM dbo.games g
 WHERE g.game_name=N'火柴人格鬥：霓虹對決' AND g.member_id=@PublisherId AND NOT EXISTS(SELECT 1 FROM dbo.game_builds b WHERE b.game_id=g.game_id AND b.version='demo-1.0.0');
 COMMIT TRANSACTION;
 SELECT g.game_id,g.game_name,g.price,g.status,g.cover_url FROM dbo.games g JOIN #MiniNames n ON n.name=g.game_name ORDER BY g.game_id;
 DROP TABLE #MiniNames;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
GO

/*
【流程測試專用：不要在第一次建資料時執行下列段落】
1. demo_studio 登入 → 發行商工作區 → 草稿「火柴人格鬥：霓虹對決」→ 送出上架審核。
2. hero_admin／demo_reviewer 在後台核准；發售日期尚未到時，狀態為即將發售，不能購買。
3. 若要快速測試開賣，可在核准後，只選取以下 UPDATE 執行，再等待後端每分鐘排程開賣：
UPDATE g SET release_date=DATEADD(MINUTE,-1,SYSDATETIME())
FROM dbo.games g JOIN dbo.members m ON m.member_id=g.member_id
WHERE m.username='demo_studio' AND g.game_name=N'火柴人格鬥：霓虹對決' AND g.status='COMING_SOON';
4. 一般會員購買後從遊戲庫開始遊玩；發行商可申請下架，24 小時內測試「取消下架」。
5. 若要快速測試期限到後正式下架，先再申請下架，再只執行以下 UPDATE，等待每分鐘排程：
UPDATE g SET scheduled_off_shelf_at=DATEADD(MINUTE,-1,SYSDATETIME())
FROM dbo.games g JOIN dbo.members m ON m.member_id=g.member_id
WHERE m.username='demo_studio' AND g.game_name=N'火柴人格鬥：霓虹對決' AND g.status='PENDING_OFF_SHELF';
以上只供本機示範遊戲加速時間，不跳過審核，也不修改其他遊戲。
*/
