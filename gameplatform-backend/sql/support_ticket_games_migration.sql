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
