-- 對既有 SQL_server資料庫_1007-7 執行一次；可重複執行。
-- 不新增 TABLE；沿用 games、game_audit_logs、game_builds、tags、game_tags、game_media、orders、order_items。
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.games','pending_review_type') IS NULL
    ALTER TABLE dbo.games ADD pending_review_type VARCHAR(20) NULL;
IF COL_LENGTH('dbo.games','review_requested_at') IS NULL
    ALTER TABLE dbo.games ADD review_requested_at DATETIME2 NULL;
IF COL_LENGTH('dbo.games','review_request_reason') IS NULL
    ALTER TABLE dbo.games ADD review_request_reason NVARCHAR(500) NULL;
IF COL_LENGTH('dbo.games','pending_request_key') IS NULL
    ALTER TABLE dbo.games ADD pending_request_key VARCHAR(36) NULL;
COMMIT;
GO
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name='CK_games_pending_review')
    ALTER TABLE dbo.games ADD CONSTRAINT CK_games_pending_review CHECK (
        (pending_review_type IS NULL AND review_requested_at IS NULL AND review_request_reason IS NULL AND pending_request_key IS NULL)
        OR (pending_review_type IS NOT NULL AND pending_review_type IN ('PUBLISH','OFF_SHELF') AND review_requested_at IS NOT NULL AND pending_request_key IS NOT NULL)
    );
GO

-- 直接下架與24小時恢復窗口；時間使用UTC Unix毫秒，避免伺服器時區變更。
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.games','off_shelf_source') IS NULL
    ALTER TABLE dbo.games ADD off_shelf_source VARCHAR(20) NULL;
IF COL_LENGTH('dbo.games','off_shelf_at') IS NULL
    ALTER TABLE dbo.games ADD off_shelf_at BIGINT NULL;
IF COL_LENGTH('dbo.games','restore_until') IS NULL
    ALTER TABLE dbo.games ADD restore_until BIGINT NULL;
IF COL_LENGTH('dbo.games','off_shelf_previous_status') IS NULL
    ALTER TABLE dbo.games ADD off_shelf_previous_status VARCHAR(20) NULL;
IF COL_LENGTH('dbo.games','off_shelf_reason') IS NULL
    ALTER TABLE dbo.games ADD off_shelf_reason NVARCHAR(500) NULL;
IF COL_LENGTH('dbo.games','off_shelf_admin_id') IS NULL
    ALTER TABLE dbo.games ADD off_shelf_admin_id INT NULL;
COMMIT;
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name='FK_games_force_admin')
    ALTER TABLE dbo.games ADD CONSTRAINT FK_games_force_admin FOREIGN KEY(off_shelf_admin_id) REFERENCES dbo.admin(admin_id);
GO
-- 上一版未完成的下架申請改成即時下架，自本次更新起計24小時恢復。
DECLARE @now BIGINT=DATEDIFF_BIG(MILLISECOND,CONVERT(DATETIME2,'1970-01-01'),SYSUTCDATETIME());
UPDATE dbo.games SET off_shelf_previous_status=status,status='OFF_SHELF',off_shelf_source='PUBLISHER',
    off_shelf_at=@now,restore_until=@now+86400000,off_shelf_reason=review_request_reason,off_shelf_admin_id=NULL,
    pending_review_type=NULL,review_requested_at=NULL,review_request_reason=NULL,pending_request_key=NULL
WHERE pending_review_type='OFF_SHELF' AND status IN ('PUBLISHED','COMING_SOON');
GO
