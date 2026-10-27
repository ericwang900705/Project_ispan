-- 2026-10-27：補齊客服問題分類；保留既有分類主鍵、案件及自訂分類，可重跑。
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @CategoryLock INT;
 EXEC @CategoryLock=sys.sp_getapplock @Resource='HERO_SUPPORT_SCHEMA',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
 IF @CategoryLock<0
 BEGIN
  DECLARE @CategoryLockMessage NVARCHAR(2048)=CONCAT(N'客服分類初始化取鎖失敗，sp_getapplock=',@CategoryLock,N'。');
  THROW 51062,@CategoryLockMessage,1;
 END;
 IF OBJECT_ID(N'dbo.customer_service_categories',N'U') IS NULL
  THROW 51063,N'缺少客服分類表，請先建立專題基礎資料表。',1;

-- BEGIN CATEGORY DATA
-- 沿用原分類 ID；已有新名稱時保留舊分類供歷史案件使用，不刪除任何關聯。
UPDATE dbo.customer_service_categories SET category_name=N'帳號與登入'
WHERE category_id=(SELECT MIN(category_id) FROM dbo.customer_service_categories WHERE LTRIM(RTRIM(category_name)) IN(N'帳號問題',N'帳號'))
AND NOT EXISTS(SELECT 1 FROM dbo.customer_service_categories WHERE LTRIM(RTRIM(category_name))=N'帳號與登入');
UPDATE dbo.customer_service_categories SET category_name=N'訂單問題'
WHERE category_id=(SELECT MIN(category_id) FROM dbo.customer_service_categories WHERE LTRIM(RTRIM(category_name)) IN(N'購買問題',N'訂單'))
AND NOT EXISTS(SELECT 1 FROM dbo.customer_service_categories WHERE LTRIM(RTRIM(category_name))=N'訂單問題');
UPDATE dbo.customer_service_categories SET category_name=N'遊戲問題'
WHERE category_id=(SELECT MIN(category_id) FROM dbo.customer_service_categories WHERE LTRIM(RTRIM(category_name))=N'遊戲')
AND NOT EXISTS(SELECT 1 FROM dbo.customer_service_categories WHERE LTRIM(RTRIM(category_name))=N'遊戲問題');
UPDATE dbo.customer_service_categories SET category_name=N'優惠券問題'
WHERE category_id=(SELECT MIN(category_id) FROM dbo.customer_service_categories WHERE LTRIM(RTRIM(category_name)) IN(N'優惠卷問題',N'優惠卷',N'優惠券'))
AND NOT EXISTS(SELECT 1 FROM dbo.customer_service_categories WHERE LTRIM(RTRIM(category_name))=N'優惠券問題');

INSERT INTO dbo.customer_service_categories(category_name,description)
SELECT v.name,v.description FROM (VALUES
 (N'帳號與登入',N'登入失敗、Google 綁定、信箱驗證及會員資料問題'),
 (N'遊戲問題',N'遊戲資訊、下載、版本或啟動異常'),
 (N'訂單問題',N'訂單查詢、內容錯誤及訂單狀態異常'),
 (N'付款問題',N'付款失敗、付款後未更新及重複付款'),
 (N'退款問題',N'退款申請、退款資格及處理進度'),
 (N'優惠券問題',N'無法領取、無法使用、折扣及適用範圍疑問'),
 (N'網站操作異常',N'頁面錯誤、按鈕無反應及購物車異常'),
 (N'其他問題',N'其他詢問與建議')
) v(name,description)
WHERE NOT EXISTS(SELECT 1 FROM dbo.customer_service_categories c WHERE LTRIM(RTRIM(c.category_name))=v.name);

-- 僅補空白說明，不覆蓋既有自訂說明。
UPDATE dbo.customer_service_categories SET description=CASE category_name
 WHEN N'帳號與登入' THEN N'登入失敗、Google 綁定、信箱驗證及會員資料問題'
 WHEN N'遊戲問題' THEN N'遊戲資訊、下載、版本或啟動異常'
 WHEN N'訂單問題' THEN N'訂單查詢、內容錯誤及訂單狀態異常'
 WHEN N'付款問題' THEN N'付款失敗、付款後未更新及重複付款'
 WHEN N'退款問題' THEN N'退款申請、退款資格及處理進度'
 WHEN N'優惠券問題' THEN N'無法領取、無法使用、折扣及適用範圍疑問'
 WHEN N'網站操作異常' THEN N'頁面錯誤、按鈕無反應及購物車異常'
 WHEN N'其他問題' THEN N'其他詢問與建議'
 ELSE description END
WHERE description IS NULL OR LTRIM(RTRIM(description))='';
-- END CATEGORY DATA

 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
