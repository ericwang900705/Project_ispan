-- 評論管理增量更新：只建立缺少的資料表，可重跑，不補造歷史違規。
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @LockResult INT;
 EXEC @LockResult=sys.sp_getapplock @Resource='HERO_REVIEW_MODERATION_SCHEMA',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
 IF @LockResult < 0
 BEGIN
  DECLARE @Message NVARCHAR(2048)=CONCAT(N'評論管理初始化取鎖失敗，代碼：',@LockResult);
  THROW 51140,@Message,1;
 END;
 IF OBJECT_ID(N'dbo.members',N'U') IS NULL OR OBJECT_ID(N'dbo.game_reviews',N'U') IS NULL OR OBJECT_ID(N'dbo.admin',N'U') IS NULL
  THROW 51141,N'請先建立會員、評論與管理員基礎資料表。',1;
 IF OBJECT_ID(N'dbo.review_violations',N'U') IS NULL
 BEGIN
  CREATE TABLE dbo.review_violations (
   violation_id BIGINT IDENTITY(1,1) PRIMARY KEY,
   review_id INT NOT NULL UNIQUE REFERENCES dbo.game_reviews(review_id),
   member_id INT NOT NULL REFERENCES dbo.members(member_id),
   report_id INT NULL,
   admin_id INT NOT NULL REFERENCES dbo.admin(admin_id),
   reason NVARCHAR(500) NOT NULL,
   confirmed_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
   reversed_at DATETIME2 NULL,
   reversed_by_admin_id INT NULL REFERENCES dbo.admin(admin_id)
  );
  CREATE INDEX IX_review_violations_member_time ON dbo.review_violations(member_id,confirmed_at) INCLUDE(reversed_at);
 END;
 IF OBJECT_ID(N'dbo.member_review_mutes',N'U') IS NULL
 BEGIN
  CREATE TABLE dbo.member_review_mutes (
   member_id INT PRIMARY KEY REFERENCES dbo.members(member_id),
   muted_until DATETIME2 NOT NULL,
   reason NVARCHAR(500) NOT NULL,
   updated_at DATETIME2 NOT NULL DEFAULT SYSDATETIME()
  );
 END;
 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
