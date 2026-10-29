-- 已存在的 GamePlatformDB：補齊 CART 五種標籤
-- 可重複執行，僅新增缺少的類型；不刪除其他標籤。
USE [GamePlatformDB];
GO
IF OBJECT_ID(N'dbo.tags', N'U') IS NULL
    THROW 51201, N'dbo.tags 不存在，請確認資料庫是否已建立。', 1;
GO
-- CART 原始五種遊戲類型：只新增尚未存在者（不覆蓋既有標籤）
INSERT INTO dbo.tags (tag_name)
SELECT v.tag_name
FROM (VALUES
    (N'動作冒險'),
    (N'角色扮演'),
    (N'競速遊戲'),
    (N'射擊'),
    (N'策略')
) AS v(tag_name)
WHERE NOT EXISTS (SELECT 1 FROM dbo.tags AS t WHERE t.tag_name = v.tag_name);
GO
SELECT tag_id, tag_name FROM dbo.tags
WHERE tag_name IN (N'動作冒險',N'角色扮演',N'競速遊戲',N'射擊',N'策略')
ORDER BY tag_id;
GO
