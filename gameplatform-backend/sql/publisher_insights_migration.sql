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
