-- 本批次自行管理實體 SQL Server 交易，不依賴 JPA 的延後交易啟動。
BEGIN TRY
 BEGIN TRANSACTION;
-- 優惠券領取紀錄及適用範圍；不刪除、不覆寫既有資料。
DECLARE @couponLock INT;
EXEC @couponLock=sys.sp_getapplock @Resource='HERO_COUPON_SCHEMA',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
IF @couponLock < 0
BEGIN
 DECLARE @couponLockMessage NVARCHAR(2048)=CONCAT(N'優惠券結構初始化取鎖失敗（sp_getapplock=',@couponLock,N'）：',
  CASE @couponLock WHEN -1 THEN N'等待逾時' WHEN -2 THEN N'要求已取消'
   WHEN -3 THEN N'交易發生死結' ELSE N'請檢查交易狀態與資料庫權限' END);
 THROW 51120,@couponLockMessage,1;
END;
IF OBJECT_ID(N'dbo.coupons',N'U') IS NULL OR OBJECT_ID(N'dbo.members',N'U') IS NULL
 OR OBJECT_ID(N'dbo.tags',N'U') IS NULL OR OBJECT_ID(N'dbo.orders',N'U') IS NULL
 THROW 51121,N'請先建立 GamePlatformDB 基礎資料表。',1;
IF OBJECT_ID(N'dbo.coupon_rules',N'U') IS NULL
BEGIN
 CREATE TABLE dbo.coupon_rules (
  coupon_id INT NOT NULL PRIMARY KEY REFERENCES dbo.coupons(coupon_id),
  scope_type VARCHAR(20) NOT NULL,
  publisher_member_id INT NULL REFERENCES dbo.members(member_id),
  tag_id INT NULL REFERENCES dbo.tags(tag_id),
  CONSTRAINT CK_coupon_rules_scope CHECK (
   (scope_type='ALL' AND publisher_member_id IS NULL AND tag_id IS NULL) OR
   (scope_type='PUBLISHER' AND publisher_member_id IS NOT NULL AND tag_id IS NULL) OR
   (scope_type='TAG' AND publisher_member_id IS NULL AND tag_id IS NOT NULL))
 );
END;
-- 整合 SQL 舊版遺漏的既有後端必要欄位；已存在時略過。
IF COL_LENGTH(N'dbo.orders',N'payment_status') IS NULL
 ALTER TABLE dbo.orders ADD payment_status VARCHAR(20) NULL;
IF COL_LENGTH(N'dbo.orders',N'bank_code') IS NULL
 ALTER TABLE dbo.orders ADD bank_code VARCHAR(20) NULL;
IF COL_LENGTH(N'dbo.orders',N'virtual_account') IS NULL
 ALTER TABLE dbo.orders ADD virtual_account VARCHAR(100) NULL;
IF COL_LENGTH(N'dbo.orders',N'payment_no') IS NULL
 ALTER TABLE dbo.orders ADD payment_no VARCHAR(100) NULL;
IF COL_LENGTH(N'dbo.orders',N'payment_expire_date') IS NULL
 ALTER TABLE dbo.orders ADD payment_expire_date VARCHAR(50) NULL;
IF OBJECT_ID(N'dbo.admin_operation_logs',N'U') IS NULL
BEGIN
 CREATE TABLE dbo.admin_operation_logs (
  operation_id INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
  admin_id INT NOT NULL REFERENCES dbo.admin(admin_id),
  action_code VARCHAR(100) NOT NULL,
  target_id INT NOT NULL,
  detail NVARCHAR(1000) NOT NULL,
  created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME()
 );
END;
IF OBJECT_ID(N'dbo.member_coupon_claims',N'U') IS NULL
BEGIN
 CREATE TABLE dbo.member_coupon_claims (
  member_id INT NOT NULL REFERENCES dbo.members(member_id),
  coupon_id INT NOT NULL REFERENCES dbo.coupons(coupon_id),
  received_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
  CONSTRAINT PK_member_coupon_claims PRIMARY KEY(member_id,coupon_id)
 );
END;

 -- 僅轉換一次舊版按明細累計的使用額度；取消及退款訂單不占用次數。
 IF NOT EXISTS (SELECT 1 FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.coupons')
  AND minor_id=0 AND name=N'HERO_COUPON_ORDER_COUNT_V1')
 BEGIN
  UPDATE c WITH (TABLOCKX) SET used_count=(
   SELECT COUNT(DISTINCT o.order_id) FROM dbo.orders o JOIN dbo.order_items i ON i.order_id=o.order_id
   WHERE i.coupon_id=c.coupon_id AND o.order_status IN ('PENDING','PAID')) FROM dbo.coupons c;
  EXEC sys.sp_addextendedproperty @name=N'HERO_COUPON_ORDER_COUNT_V1',@value=1,
   @level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'coupons';
 END;
 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
