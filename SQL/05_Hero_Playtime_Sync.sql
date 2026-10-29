USE [GamePlatformDB];
GO
-- 重試同一批遊玩分鐘不重複入帳；與時數更新在同一交易內執行。
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @lock INT;
 EXEC @lock=sys.sp_getapplock @Resource='HERO_PLAYTIME_SYNC_SCHEMA',
  @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
 IF @lock < 0
 BEGIN
  DECLARE @message NVARCHAR(2048)=CONCAT(N'遊玩時數結構取鎖失敗（sp_getapplock=',@lock,N'）');
  THROW 51170,@message,1;
 END;
 IF OBJECT_ID(N'dbo.members',N'U') IS NULL OR OBJECT_ID(N'dbo.games',N'U') IS NULL
  THROW 51171,N'請先建立 members 與 games 基礎資料表。',1;
 IF OBJECT_ID(N'dbo.playtime_sync_batches',N'U') IS NULL
 BEGIN
  CREATE TABLE dbo.playtime_sync_batches(
   member_id INT NOT NULL REFERENCES dbo.members(member_id),
   game_id INT NOT NULL REFERENCES dbo.games(game_id),
   batch_id VARCHAR(36) NOT NULL,
   minutes INT NOT NULL CHECK(minutes BETWEEN 1 AND 1440),
   created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
   CONSTRAINT PK_playtime_sync_batches PRIMARY KEY(member_id,game_id,batch_id)
  );
 END;
 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;

GO
