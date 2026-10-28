-- 標籤名稱清理：移除 dbo.tags 的 [QA26] 前綴，保留遊戲、優惠券與搜尋統計關聯。
-- 僅更新標籤；不修改測試帳號、遊戲名稱、圖片或訂單。
-- 執行前停止後端並備份 GamePlatformDB；可重跑。使用 UTF-8（不含 BOM）。
USE [GamePlatformDB];
GO
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @LockResult INT;
    EXEC @LockResult = sys.sp_getapplock
        @Resource = 'HERO_TAG_NAME_CLEANUP', @LockMode = 'Exclusive',
        @LockOwner = 'Transaction', @LockTimeout = 10000;
    IF @LockResult < 0
        THROW 51210, N'標籤清理取鎖失敗，請稍後重試。', 1;
    IF OBJECT_ID(N'dbo.tags', N'U') IS NULL
        THROW 51211, N'缺少 tags 資料表，請先確認資料庫。', 1;

    -- 若專題另加了未涵蓋的標籤外鍵，停止並完整回滾，避免遺漏關聯。
    IF EXISTS (
        SELECT 1 FROM sys.foreign_keys fk
        WHERE fk.referenced_object_id = OBJECT_ID(N'dbo.tags')
          AND fk.parent_object_id NOT IN (
              ISNULL(OBJECT_ID(N'dbo.game_tags'), -1),
              ISNULL(OBJECT_ID(N'dbo.member_tag_search_stats'), -1),
              ISNULL(OBJECT_ID(N'dbo.coupon_rules'), -1))
    )
        THROW 51212, N'發現額外的標籤關聯資料表，請先核對後再合併。', 1;

    DECLARE @Changes TABLE (old_name NVARCHAR(50), new_name NVARCHAR(50));
    DECLARE @OldId INT, @NewId INT, @OldName NVARCHAR(50), @NewName NVARCHAR(50);
    DECLARE tag_names CURSOR LOCAL FAST_FORWARD FOR
        SELECT tag_id, tag_name, LTRIM(RTRIM(SUBSTRING(tag_name, 7, 50)))
        FROM dbo.tags WITH (UPDLOCK, HOLDLOCK)
        WHERE LEFT(tag_name, 6) = N'[QA26]'
        ORDER BY tag_id;
    OPEN tag_names;
    FETCH NEXT FROM tag_names INTO @OldId, @OldName, @NewName;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        IF LEN(@NewName) = 0
            THROW 51213, N'有標籤移除前綴後為空白，請先替該標籤命名。', 1;
        SET @NewId = NULL;
        SELECT @NewId = tag_id FROM dbo.tags WITH (UPDLOCK, HOLDLOCK)
        WHERE tag_name = @NewName;
        IF @NewId IS NULL
            UPDATE dbo.tags SET tag_name = @NewName WHERE tag_id = @OldId;
        ELSE
        BEGIN
            -- 已有同名正式標籤：合併到正式標籤，不建立重複關聯。
            INSERT dbo.game_tags(game_id, tag_id, created_at)
            SELECT old.game_id, @NewId, old.created_at
            FROM dbo.game_tags old
            WHERE old.tag_id = @OldId AND NOT EXISTS (
                SELECT 1 FROM dbo.game_tags existing
                WHERE existing.game_id = old.game_id AND existing.tag_id = @NewId);
            DELETE dbo.game_tags WHERE tag_id = @OldId;

            IF EXISTS (
                SELECT 1 FROM dbo.member_tag_search_stats old
                JOIN dbo.member_tag_search_stats existing ON existing.member_id = old.member_id AND existing.tag_id = @NewId
                WHERE old.tag_id = @OldId AND CAST(old.search_count AS BIGINT) + existing.search_count > 2147483647
            )
                THROW 51214, N'搜尋統計合計超出欄位範圍，本次變更已回滾。', 1;
            UPDATE existing SET
                search_count = existing.search_count + old.search_count,
                first_searched_at = CASE WHEN old.first_searched_at < existing.first_searched_at THEN old.first_searched_at ELSE existing.first_searched_at END,
                last_searched_at = CASE WHEN old.last_searched_at > existing.last_searched_at THEN old.last_searched_at ELSE existing.last_searched_at END
            FROM dbo.member_tag_search_stats existing
            JOIN dbo.member_tag_search_stats old ON old.member_id = existing.member_id AND old.tag_id = @OldId
            WHERE existing.tag_id = @NewId;
            INSERT dbo.member_tag_search_stats(member_id, tag_id, search_count, first_searched_at, last_searched_at)
            SELECT old.member_id, @NewId, old.search_count, old.first_searched_at, old.last_searched_at
            FROM dbo.member_tag_search_stats old
            WHERE old.tag_id = @OldId AND NOT EXISTS (
                SELECT 1 FROM dbo.member_tag_search_stats existing
                WHERE existing.member_id = old.member_id AND existing.tag_id = @NewId);
            DELETE dbo.member_tag_search_stats WHERE tag_id = @OldId;

            IF OBJECT_ID(N'dbo.coupon_rules', N'U') IS NOT NULL
                EXEC sys.sp_executesql N'UPDATE dbo.coupon_rules SET tag_id = @NewId WHERE tag_id = @OldId',
                    N'@OldId INT, @NewId INT', @OldId, @NewId;
            DELETE dbo.tags WHERE tag_id = @OldId;
        END;
        INSERT @Changes VALUES (@OldName, @NewName);
        FETCH NEXT FROM tag_names INTO @OldId, @OldName, @NewName;
    END;
    CLOSE tag_names;
    DEALLOCATE tag_names;
    COMMIT TRANSACTION;
    SELECT old_name, new_name FROM @Changes ORDER BY old_name;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO
SELECT tag_id, tag_name FROM dbo.tags ORDER BY tag_id;
GO
