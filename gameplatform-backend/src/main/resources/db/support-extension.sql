-- 客服功能增量遷移；可重跑，不刪除既有資料。由後端啟動器於同一連線執行。
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @SupportLock INT;
 EXEC @SupportLock=sys.sp_getapplock @Resource='HERO_SUPPORT_SCHEMA',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
 IF @SupportLock<0
 BEGIN
  DECLARE @SupportLockMessage NVARCHAR(2048)=CONCAT(N'客服結構初始化無法取得交易鎖，sp_getapplock=',@SupportLock,N'，本次變更已回滾。');
  THROW 51060,@SupportLockMessage,1;
 END;
 IF OBJECT_ID(N'dbo.chat_rooms',N'U') IS NULL OR OBJECT_ID(N'dbo.member_coupon_claims',N'U') IS NULL
  THROW 51061,N'請先建立基礎資料表及優惠券結構。',1;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.orders') AND name='UQ_support_order_owner')
  CREATE UNIQUE INDEX UQ_support_order_owner ON dbo.orders(order_id,member_id);
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.chat_rooms') AND name='UQ_support_room_owner')
  CREATE UNIQUE INDEX UQ_support_room_owner ON dbo.chat_rooms(support_room_id,member_id);
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.customer_service_tickets') AND name='UQ_support_ticket_owner')
  CREATE UNIQUE INDEX UQ_support_ticket_owner ON dbo.customer_service_tickets(ticket_id,member_id);
 IF OBJECT_ID(N'dbo.support_order_links',N'U') IS NULL
 BEGIN
  CREATE TABLE dbo.support_order_links(
   link_id BIGINT IDENTITY PRIMARY KEY,support_room_id INT NULL,ticket_id INT NULL,member_id INT NOT NULL,order_id INT NOT NULL,
   linked_by_member_id INT NULL REFERENCES dbo.members(member_id),linked_by_admin_id INT NULL REFERENCES dbo.admin(admin_id),
   created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),removed_at DATETIME2 NULL,
   CHECK((support_room_id IS NOT NULL AND ticket_id IS NULL) OR (support_room_id IS NULL AND ticket_id IS NOT NULL)),
   CHECK((linked_by_member_id=member_id AND linked_by_admin_id IS NULL AND linked_by_member_id IS NOT NULL) OR (linked_by_member_id IS NULL AND linked_by_admin_id IS NOT NULL)),
   FOREIGN KEY(support_room_id,member_id) REFERENCES dbo.chat_rooms(support_room_id,member_id),
   FOREIGN KEY(ticket_id,member_id) REFERENCES dbo.customer_service_tickets(ticket_id,member_id),
   FOREIGN KEY(order_id,member_id) REFERENCES dbo.orders(order_id,member_id));
  CREATE UNIQUE INDEX UX_support_room_order ON dbo.support_order_links(support_room_id,order_id) WHERE support_room_id IS NOT NULL AND removed_at IS NULL;
  CREATE UNIQUE INDEX UX_support_ticket_order ON dbo.support_order_links(ticket_id,order_id) WHERE ticket_id IS NOT NULL AND removed_at IS NULL;
 END;
 IF OBJECT_ID(N'dbo.support_coupon_links',N'U') IS NULL
 BEGIN
  CREATE TABLE dbo.support_coupon_links(
   link_id BIGINT IDENTITY PRIMARY KEY,support_room_id INT NULL,ticket_id INT NULL,member_id INT NOT NULL,coupon_id INT NOT NULL,
   linked_by_member_id INT NULL REFERENCES dbo.members(member_id),linked_by_admin_id INT NULL REFERENCES dbo.admin(admin_id),
   created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),removed_at DATETIME2 NULL,
   CHECK((support_room_id IS NOT NULL AND ticket_id IS NULL) OR (support_room_id IS NULL AND ticket_id IS NOT NULL)),
   CHECK((linked_by_member_id=member_id AND linked_by_admin_id IS NULL AND linked_by_member_id IS NOT NULL) OR (linked_by_member_id IS NULL AND linked_by_admin_id IS NOT NULL)),
   FOREIGN KEY(support_room_id,member_id) REFERENCES dbo.chat_rooms(support_room_id,member_id),
   FOREIGN KEY(ticket_id,member_id) REFERENCES dbo.customer_service_tickets(ticket_id,member_id),
   FOREIGN KEY(member_id,coupon_id) REFERENCES dbo.member_coupon_claims(member_id,coupon_id));
  CREATE UNIQUE INDEX UX_support_room_coupon ON dbo.support_coupon_links(support_room_id,coupon_id) WHERE support_room_id IS NOT NULL AND removed_at IS NULL;
  CREATE UNIQUE INDEX UX_support_ticket_coupon ON dbo.support_coupon_links(ticket_id,coupon_id) WHERE ticket_id IS NOT NULL AND removed_at IS NULL;
 END;
 IF OBJECT_ID(N'dbo.support_reply_templates',N'U') IS NULL
 BEGIN
  CREATE TABLE dbo.support_reply_templates(template_id BIGINT IDENTITY PRIMARY KEY,category_id INT NULL REFERENCES dbo.customer_service_categories(category_id),
   title NVARCHAR(100) NOT NULL,content NVARCHAR(4000) NOT NULL,status VARCHAR(10) NOT NULL DEFAULT 'INACTIVE' CHECK(status IN('ACTIVE','INACTIVE')),
   sort_order INT NOT NULL DEFAULT 0 CHECK(sort_order>=0),version INT NOT NULL DEFAULT 0,
   created_by_admin_id INT NULL REFERENCES dbo.admin(admin_id),updated_by_admin_id INT NULL REFERENCES dbo.admin(admin_id),
   created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),updated_at DATETIME2 NOT NULL DEFAULT SYSDATETIME());
  CREATE INDEX IX_support_templates ON dbo.support_reply_templates(status,sort_order,template_id);
  INSERT dbo.support_reply_templates(title,content,status,sort_order) VALUES
   (N'優惠券適用範圍',N'您好，優惠券須符合指定發行商或遊戲類型、有效期限與低消，我會先協助您核對適用商品。','ACTIVE',10),
   (N'付款尚未入帳',N'您好，我會先確認訂單的付款狀態；為避免重複扣款，請先不要再次付款。','ACTIVE',20),
   (N'請提供錯誤畫面',N'請提供錯誤訊息或畫面，並遮蔽付款代碼、密碼及其他敏感資料，方便我們查核。','ACTIVE',30);
 END;
 IF OBJECT_ID(N'dbo.support_internal_notes',N'U') IS NULL
 BEGIN
  CREATE TABLE dbo.support_internal_notes(note_id BIGINT IDENTITY PRIMARY KEY,support_room_id INT NULL REFERENCES dbo.chat_rooms(support_room_id),
   ticket_id INT NULL REFERENCES dbo.customer_service_tickets(ticket_id),author_admin_id INT NOT NULL REFERENCES dbo.admin(admin_id),content NVARCHAR(2000) NOT NULL,
   created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
   CHECK((support_room_id IS NOT NULL AND ticket_id IS NULL) OR (support_room_id IS NULL AND ticket_id IS NOT NULL)));
  CREATE INDEX IX_support_room_notes ON dbo.support_internal_notes(support_room_id,note_id DESC);
  CREATE INDEX IX_support_ticket_notes ON dbo.support_internal_notes(ticket_id,note_id DESC);
 END;
 IF OBJECT_ID(N'dbo.support_faqs',N'U') IS NULL
 BEGIN
  CREATE TABLE dbo.support_faqs(faq_id BIGINT IDENTITY PRIMARY KEY,category_id INT NULL REFERENCES dbo.customer_service_categories(category_id),
   question NVARCHAR(200) NOT NULL,answer NVARCHAR(MAX) NOT NULL CHECK(LEN(answer)<=8000),status VARCHAR(12) NOT NULL DEFAULT 'DRAFT' CHECK(status IN('DRAFT','PUBLISHED','ARCHIVED')),
   sort_order INT NOT NULL DEFAULT 0 CHECK(sort_order>=0),version INT NOT NULL DEFAULT 0,published_at DATETIME2 NULL,
   created_by_admin_id INT NULL REFERENCES dbo.admin(admin_id),updated_by_admin_id INT NULL REFERENCES dbo.admin(admin_id),
   created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),updated_at DATETIME2 NOT NULL DEFAULT SYSDATETIME());
  CREATE INDEX IX_support_faq ON dbo.support_faqs(status,sort_order,faq_id);
  INSERT dbo.support_faqs(question,answer,status,sort_order,published_at) VALUES
   (N'優惠券領取後需要馬上使用嗎？',N'不用。領取後可在有效期限內，於符合適用商品、低消與額度的購物車選擇使用。','PUBLISHED',10,SYSDATETIME()),
   (N'付款尚未入帳怎麼辦？',N'請先查看我的訂單中的付款狀態，避免重複付款；如需查核，可從該筆訂單聯絡客服。','PUBLISHED',20,SYSDATETIME()),
   (N'如何綁定 Google 帳號？',N'請先使用原帳號登入，到會員個人資料的 Google 綁定區進行連接。','PUBLISHED',30,SYSDATETIME());
 END;
 IF OBJECT_ID(N'dbo.support_audit_logs',N'U') IS NULL
 BEGIN
  CREATE TABLE dbo.support_audit_logs(audit_id BIGINT IDENTITY PRIMARY KEY,support_room_id INT NULL REFERENCES dbo.chat_rooms(support_room_id),
   ticket_id INT NULL REFERENCES dbo.customer_service_tickets(ticket_id),action_code VARCHAR(40) NOT NULL,visibility VARCHAR(10) NOT NULL CHECK(visibility IN('MEMBER','INTERNAL')),
   actor_member_id INT NULL REFERENCES dbo.members(member_id),actor_admin_id INT NULL REFERENCES dbo.admin(admin_id),summary NVARCHAR(500) NOT NULL,
   created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
   CHECK((support_room_id IS NOT NULL AND ticket_id IS NULL) OR (support_room_id IS NULL AND ticket_id IS NOT NULL)));
  CREATE INDEX IX_support_room_audit ON dbo.support_audit_logs(support_room_id,audit_id DESC);
  CREATE INDEX IX_support_ticket_audit ON dbo.support_audit_logs(ticket_id,audit_id DESC);
 END;
 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
