-- 退款申請集中管理；可重跑，不刪除會員、訂單或原客服案件。
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @RefundLock INT;
    EXEC @RefundLock = sys.sp_getapplock @Resource='HERO_REFUND_SCHEMA',
        @LockMode='Exclusive', @LockOwner='Transaction', @LockTimeout=10000;
    IF @RefundLock < 0 THROW 51300,N'退款結構初始化取鎖失敗。',1;
    IF OBJECT_ID(N'dbo.orders',N'U') IS NULL OR OBJECT_ID(N'dbo.support_order_links',N'U') IS NULL
        THROW 51301,N'請先建立訂單與客服擴充結構。',1;
    IF COL_LENGTH(N'dbo.orders',N'payment_environment') IS NULL
        ALTER TABLE dbo.orders ADD payment_environment VARCHAR(10) NULL
            CONSTRAINT CK_orders_payment_environment CHECK(payment_environment IN('TEST','PROD'));
    IF OBJECT_ID(N'dbo.refund_requests',N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.refund_requests(
            refund_id INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_refund_requests PRIMARY KEY,
            order_id INT NOT NULL CONSTRAINT UQ_refund_request_order UNIQUE REFERENCES dbo.orders(order_id),
            member_id INT NOT NULL REFERENCES dbo.members(member_id),
            ticket_id INT NOT NULL CONSTRAINT UQ_refund_request_ticket UNIQUE REFERENCES dbo.customer_service_tickets(ticket_id),
            reason NVARCHAR(2000) NOT NULL,
            status VARCHAR(12) NOT NULL DEFAULT('PENDING') CHECK(status IN('PENDING','REFUNDED','REJECTED')),
            requested_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
            processed_at DATETIME2 NULL,
            processed_by_admin_id INT NULL REFERENCES dbo.admin(admin_id),
            decision_reason NVARCHAR(1000) NULL,
            refund_mode VARCHAR(10) NULL CHECK(refund_mode IN('TEST','MANUAL')),
            refund_reference NVARCHAR(100) NULL,
            CONSTRAINT CK_refund_processed CHECK(
                (status='PENDING' AND processed_at IS NULL AND processed_by_admin_id IS NULL AND refund_mode IS NULL) OR
                (status='REJECTED' AND processed_at IS NOT NULL AND processed_by_admin_id IS NOT NULL AND decision_reason IS NOT NULL AND LEN(LTRIM(RTRIM(decision_reason)))>0 AND refund_mode IS NULL) OR
                (status='REFUNDED' AND processed_at IS NOT NULL AND processed_by_admin_id IS NOT NULL AND decision_reason IS NOT NULL AND LEN(LTRIM(RTRIM(decision_reason)))>0 AND
                    (refund_mode IS NOT NULL AND (refund_mode='TEST' OR (refund_mode='MANUAL' AND refund_reference IS NOT NULL AND LEN(LTRIM(RTRIM(refund_reference)))>0)))))
        );
        CREATE INDEX IX_refund_requests_status_time ON dbo.refund_requests(status,requested_at DESC,refund_id DESC);
    END;
    IF OBJECT_ID(N'dbo.payment_attempts',N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.payment_attempts(
            attempt_id INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
            order_id INT NOT NULL REFERENCES dbo.orders(order_id),
            merchant_trade_no VARCHAR(20) NOT NULL UNIQUE,
            payment_method VARCHAR(10) NOT NULL CHECK(payment_method IN('Credit','ATM','CVS')),
            payment_environment VARCHAR(10) NOT NULL CHECK(payment_environment IN('TEST','PROD')),
            status VARCHAR(12) NOT NULL DEFAULT('CREATED') CHECK(status IN('CREATED','PENDING','PAID','FAILED','REVIEW')),
            ecpay_trade_no VARCHAR(20) NULL,
            created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
            updated_at DATETIME2 NULL
        );
        CREATE INDEX IX_payment_attempts_order ON dbo.payment_attempts(order_id,attempt_id DESC);
    END;
    -- 只補入原本由「會員訂單退款申請」入口建立、且唯一關聯該訂單的案件。
    INSERT INTO dbo.refund_requests(order_id,member_id,ticket_id,reason,requested_at)
    SELECT order_id,member_id,ticket_id,LEFT(content,2000),created_at FROM (
        SELECT l.order_id,t.member_id,t.ticket_id,t.content,t.created_at,
            ROW_NUMBER() OVER(PARTITION BY l.order_id ORDER BY t.created_at,t.ticket_id) AS rn
        FROM dbo.support_order_links l
        JOIN dbo.customer_service_tickets t ON t.ticket_id=l.ticket_id AND t.member_id=l.member_id
        JOIN dbo.customer_service_categories c ON c.category_id=t.category_id
        JOIN dbo.orders o ON o.order_id=l.order_id AND o.member_id=t.member_id
        WHERE l.removed_at IS NULL AND c.category_name=N'退款問題'
          AND t.subject=CONCAT(N'退款申請｜訂單 #',l.order_id) AND o.order_status='PAID'
          AND NOT EXISTS(SELECT 1 FROM dbo.support_order_links x WHERE x.ticket_id=t.ticket_id AND x.removed_at IS NULL AND x.order_id<>l.order_id)
    ) c
    WHERE rn=1 AND NOT EXISTS(SELECT 1 FROM dbo.refund_requests r WHERE r.order_id=c.order_id OR r.ticket_id=c.ticket_id);
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
