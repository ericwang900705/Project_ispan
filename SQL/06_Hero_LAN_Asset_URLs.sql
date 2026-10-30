-- 既有資料庫才需要：修正開發伺服器素材網址；全新建庫已執行新版 02、03 可略過。
USE [GamePlatformDB];
GO
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @prefix VARCHAR(100);
 DECLARE prefixes CURSOR LOCAL FAST_FORWARD FOR
 SELECT prefix FROM (VALUES ('http://127.0.0.1:5173/'),('http://localhost:5173/'),
 ('http://127.0.0.1:5174/'),('http://localhost:5174/')) p(prefix);
 OPEN prefixes;
 FETCH NEXT FROM prefixes INTO @prefix;
 WHILE @@FETCH_STATUS=0
 BEGIN
  UPDATE dbo.games SET cover_url=STUFF(cover_url,1,LEN(@prefix)-1,'')
  WHERE LEFT(cover_url,LEN(@prefix))=@prefix;
  UPDATE dbo.game_media SET media_url=STUFF(media_url,1,LEN(@prefix)-1,'')
  WHERE LEFT(media_url,LEN(@prefix))=@prefix;
  UPDATE dbo.game_builds SET file_url=STUFF(file_url,1,LEN(@prefix)-1,'')
  WHERE LEFT(file_url,LEN(@prefix))=@prefix;
  FETCH NEXT FROM prefixes INTO @prefix;
 END;
 CLOSE prefixes;
 DEALLOCATE prefixes;
 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
GO
