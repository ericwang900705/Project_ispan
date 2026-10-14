-- 在既有 1013 GamePlatformDB 執行；不刪除、不重建資料表。
-- 資料庫名稱不同時，先在 SSMS 選擇自己的資料庫。
IF OBJECT_ID(N'dbo.orders',N'U') IS NULL
    THROW 50001, N'請先建立 1013 orders / order_items / member_games 等商城資料表', 1;
GO
IF COL_LENGTH('dbo.orders','payment_status') IS NULL
    ALTER TABLE dbo.orders ADD payment_status VARCHAR(20) NOT NULL CONSTRAINT DF_orders_payment_status DEFAULT ('PENDING');
IF COL_LENGTH('dbo.orders','ecpay_trade_no') IS NULL
    ALTER TABLE dbo.orders ADD ecpay_trade_no VARCHAR(30) NULL;
IF COL_LENGTH('dbo.orders','payment_type') IS NULL
    ALTER TABLE dbo.orders ADD payment_type VARCHAR(30) NULL;
IF COL_LENGTH('dbo.orders','bank_code') IS NULL
    ALTER TABLE dbo.orders ADD bank_code VARCHAR(10) NULL;
IF COL_LENGTH('dbo.orders','virtual_account') IS NULL
    ALTER TABLE dbo.orders ADD virtual_account VARCHAR(40) NULL;
IF COL_LENGTH('dbo.orders','payment_no') IS NULL
    ALTER TABLE dbo.orders ADD payment_no VARCHAR(40) NULL;
IF COL_LENGTH('dbo.orders','payment_expire_date') IS NULL
    ALTER TABLE dbo.orders ADD payment_expire_date VARCHAR(40) NULL;
GO
UPDATE dbo.orders SET payment_status=order_status
WHERE order_status IN ('PAID','CANCELLED','REFUNDED') AND payment_status='PENDING';
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.orders') AND name='UX_orders_ecpay_trade_no')
    CREATE UNIQUE INDEX UX_orders_ecpay_trade_no ON dbo.orders(ecpay_trade_no) WHERE ecpay_trade_no IS NOT NULL;
GO
-- order_status 仍維持 PENDING / PAID / CANCELLED / REFUNDED。
-- 金流失敗以 order_status=CANCELLED、payment_status=FAILED 保存，不放寬原 CHECK。
-- 使用 order_no 原欄位保存 MerchantTradeNo，不以字串拆解 order_id。
-- member_games 保留 order_item_id 外鍵；order_items.final_amount 仍由 SQL 計算。
