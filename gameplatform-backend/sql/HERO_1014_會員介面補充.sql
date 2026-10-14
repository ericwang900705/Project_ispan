-- SSMS 先選擇你現有的資料庫；不重建、不刪除資料。
IF OBJECT_ID(N'dbo.member_verifications',N'U') IS NULL
    THROW 50001,N'請先建立 member_verifications 資料表',1;
GO
IF COL_LENGTH('dbo.member_verifications','watch_token_hash') IS NULL
    ALTER TABLE dbo.member_verifications ADD watch_token_hash VARCHAR(64) NULL;
GO
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.member_verifications') AND name='UX_member_verification_watch')
    CREATE UNIQUE INDEX UX_member_verification_watch ON dbo.member_verifications(watch_token_hash) WHERE watch_token_hash IS NOT NULL;
GO
-- 原等待頁只以獨立憑證查詢是否已驗證，無法用此憑證啟用帳號。
-- 生日限制透過 UPDATE ... WHERE birth_date IS NULL 原子檢查，不需要新欄位。
