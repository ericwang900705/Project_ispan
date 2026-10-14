-- HERO 1013 既有資料庫補充；SSMS 先執行建庫檔或選擇既有 GamePlatformDB。
-- 不含 CREATE DATABASE / DROP / TRUNCATE；請勿以原始建庫檔覆蓋既有資料。
-- 發行商欄位、審核已讀、案件關聯／優先度與通知。可重複執行。
USE GamePlatformDB;
GO
-- ===== support_roles_migration.sql =====
-- 三種後台角色：管理者繼承審核與客服權限。
-- SSMS 先選取既有資料庫；不建立或改派人員帳號。
SET XACT_ABORT ON;
GO
BEGIN TRY
    BEGIN TRANSACTION;
    IF OBJECT_ID(N'dbo.admin_roles', N'U') IS NULL
        OR OBJECT_ID(N'dbo.admin_permissions', N'U') IS NULL
        OR OBJECT_ID(N'dbo.admin_role_permissions', N'U') IS NULL
        THROW 50010, N'請先建立組內角色及權限資料表。', 1;

    DECLARE @roles TABLE (code VARCHAR(50), name NVARCHAR(100), description NVARCHAR(255));
    INSERT INTO @roles VALUES
        ('ADMIN', N'管理者', N'可使用管理後台，並繼承審核及客服權限'),
        ('REVIEWER', N'審核人員', N'僅使用審核工作區；實際審核功能尚在規劃'),
        ('SUPPORT', N'客服人員', N'使用客服工作總覽、即時聊天與案件功能');
    INSERT INTO dbo.admin_roles(role_code,role_name,description)
        SELECT code,name,description FROM @roles src
        WHERE NOT EXISTS (SELECT 1 FROM dbo.admin_roles r WITH (UPDLOCK,HOLDLOCK) WHERE r.role_code=src.code);
    UPDATE r SET role_name=src.name,description=src.description
        FROM dbo.admin_roles r JOIN @roles src ON src.code=r.role_code;

    DECLARE @permissions TABLE(code VARCHAR(100),name NVARCHAR(100),description NVARCHAR(255));
    INSERT INTO @permissions VALUES
        ('MANAGEMENT_ACCESS',N'管理後台入口',N'管理者可使用'),
        ('REVIEW_ACCESS',N'審核工作區',N'管理者與審核人員可使用'),
        ('SUPPORT_ACCESS',N'客服工作區',N'管理者與客服人員可使用');
    INSERT INTO dbo.admin_permissions(permission_code,permission_name,description)
        SELECT code,name,description FROM @permissions src
        WHERE NOT EXISTS (SELECT 1 FROM dbo.admin_permissions p WITH (UPDLOCK,HOLDLOCK) WHERE p.permission_code=src.code);

    DECLARE @grants TABLE(role_code VARCHAR(50),permission_code VARCHAR(100));
    INSERT INTO @grants VALUES
        ('ADMIN','MANAGEMENT_ACCESS'),('ADMIN','REVIEW_ACCESS'),('ADMIN','SUPPORT_ACCESS'),
        ('REVIEWER','REVIEW_ACCESS'),('SUPPORT','SUPPORT_ACCESS');
    -- 僅校正這三種角色的三個工作區權限；其他模組的既有權限不變。
    DELETE arp FROM dbo.admin_role_permissions arp
        JOIN dbo.admin_roles r ON r.admin_role_id=arp.admin_role_id
        JOIN dbo.admin_permissions p ON p.permission_id=arp.permission_id
        WHERE r.role_code IN ('ADMIN','REVIEWER','SUPPORT')
          AND p.permission_code IN ('MANAGEMENT_ACCESS','REVIEW_ACCESS','SUPPORT_ACCESS')
          AND NOT EXISTS (SELECT 1 FROM @grants g WHERE g.role_code=r.role_code AND g.permission_code=p.permission_code);
    INSERT INTO dbo.admin_role_permissions(admin_role_id,permission_id)
        SELECT r.admin_role_id,p.permission_id FROM @grants g
        JOIN dbo.admin_roles r ON r.role_code=g.role_code
        JOIN dbo.admin_permissions p ON p.permission_code=g.permission_code
        WHERE NOT EXISTS (SELECT 1 FROM dbo.admin_role_permissions arp WITH (UPDLOCK,HOLDLOCK)
            WHERE arp.admin_role_id=r.admin_role_id AND arp.permission_id=p.permission_id);
    COMMIT;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK;
    THROW;
END CATCH;
GO

-- ===== publisher_workflow_migration.sql =====
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

-- ===== publisher_insights_migration.sql =====
-- 先完成 publisher_workflow_migration.sql，再執行本檔。可重複執行。
-- 不新增 TABLE。orders.paid_at 與 order_items.final_amount 沿用原資料庫。
-- NULL 表示未讀；既有審核紀錄也會出現在通知中心。
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.game_audit_logs','publisher_read_at') IS NULL
    ALTER TABLE dbo.game_audit_logs ADD publisher_read_at DATETIME2 NULL;
COMMIT;
GO

-- 固定標籤：只補缺少的名稱，不刪除其他功能既有的標籤或關聯。
-- 發行商 API 僅開放下列八種；儲存標籤時會用本次勾選取代該遊戲的舊關聯。
INSERT INTO dbo.tags(tag_name)
SELECT v.tag_name FROM (VALUES (N'RPG'),(N'多人遊戲'),(N'冒險'),(N'恐怖'),(N'益智'),(N'動作'),(N'策略'),(N'模擬')) v(tag_name)
WHERE NOT EXISTS (SELECT 1 FROM dbo.tags t WHERE t.tag_name=v.tag_name);
GO

-- ===== support_ticket_games_migration.sql =====
-- 既有資料庫升級：不重建、不刪除案件，補回原本的單一遊戲關聯。
-- 請在 SSMS 選擇你們實際使用的資料庫後執行；先部署此 SQL 再更新後端。
SET XACT_ABORT ON;
GO
BEGIN TRY
    BEGIN TRANSACTION;
    IF OBJECT_ID(N'dbo.customer_service_tickets', N'U') IS NULL OR OBJECT_ID(N'dbo.games', N'U') IS NULL
        THROW 50001, N'缺少案件或遊戲表，請確認資料庫。', 1;
    IF OBJECT_ID(N'dbo.customer_service_ticket_games', N'U') IS NULL
    BEGIN
CREATE TABLE dbo.customer_service_ticket_games (
    ticket_id INT NOT NULL,
    game_id INT NOT NULL,
    sort_order INT NOT NULL DEFAULT (0),
    CONSTRAINT PK_customer_service_ticket_games PRIMARY KEY (ticket_id, game_id),
    CONSTRAINT FK_cstg_ticket FOREIGN KEY (ticket_id) REFERENCES dbo.customer_service_tickets(ticket_id),
    CONSTRAINT FK_cstg_game FOREIGN KEY (game_id) REFERENCES dbo.games(game_id),
    CONSTRAINT CK_cstg_sort CHECK (sort_order >= 0)
);
    END;
    IF COL_LENGTH(N'dbo.customer_service_ticket_games', N'sort_order') IS NULL
        THROW 50002, N'已有不同版本的關聯表，請先核對 sort_order 欄位，不要覆蓋既有資料。', 1;
    EXEC(N'INSERT INTO dbo.customer_service_ticket_games(ticket_id,game_id,sort_order)
        SELECT t.ticket_id,t.game_id,0 FROM dbo.customer_service_tickets t
        WHERE t.game_id IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM dbo.customer_service_ticket_games tg WITH (UPDLOCK,HOLDLOCK)
            WHERE tg.ticket_id=t.ticket_id AND tg.game_id=t.game_id)');
    COMMIT;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK;
    THROW;
END CATCH;
GO

-- ===== support_ticket_priority_1012.sql =====
-- Execute against existing GamePlatformDB; does not rebuild other tables.
USE GamePlatformDB;
GO
IF COL_LENGTH('dbo.customer_service_tickets', 'priority') IS NULL
BEGIN
    ALTER TABLE dbo.customer_service_tickets
    ADD priority VARCHAR(10) NOT NULL CONSTRAINT DF_cst_priority DEFAULT ('NORMAL');
END;
GO
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name='CK_cst_priority' AND parent_object_id=OBJECT_ID('dbo.customer_service_tickets'))
BEGIN
    ALTER TABLE dbo.customer_service_tickets ADD CONSTRAINT CK_cst_priority
        CHECK (priority IN ('URGENT','HIGH','NORMAL','LOW'));
END;
GO

-- ===== notifications_1012.sql =====
-- HERO 通知系統：既有 GamePlatformDB 升級使用，不會重建資料庫。
USE GamePlatformDB;
GO
IF OBJECT_ID(N'dbo.notifications',N'U') IS NULL
BEGIN
    CREATE TABLE dbo.notifications (
        notification_id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_notifications PRIMARY KEY,
        recipient_type VARCHAR(10) NOT NULL,
        recipient_id INT NOT NULL,
        event_type VARCHAR(40) NOT NULL,
        title NVARCHAR(120) NOT NULL,
        body NVARCHAR(500) NOT NULL,
        target_path VARCHAR(250) NOT NULL,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_notifications_created DEFAULT SYSDATETIME(),
        read_at DATETIME2 NULL,
        CONSTRAINT CK_notifications_recipient_type CHECK (recipient_type IN ('MEMBER','ADMIN'))
    );
    CREATE INDEX IX_notifications_inbox ON dbo.notifications(recipient_type,recipient_id,notification_id DESC) INCLUDE (read_at);
END
GO
-- recipient_type=MEMBER -> members.member_id（含發行商）；ADMIN -> admin.admin_id（管理員、客服、審核人員）。
-- 多型 recipient_id 由後端驗證，無法建立單一 FK 指向兩張主表。
