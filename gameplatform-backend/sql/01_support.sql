-- 在 SSMS 選擇小組既有資料庫後執行；不建立、不刪除其他模組資料表。
-- 前置條件：members、games、admin、admin_roles 已由小組建好；請先備份。
SET XACT_ABORT ON;
GO
IF OBJECT_ID(N'dbo.members',N'U') IS NULL OR OBJECT_ID(N'dbo.games',N'U') IS NULL
 OR OBJECT_ID(N'dbo.admin',N'U') IS NULL OR OBJECT_ID(N'dbo.admin_roles',N'U') IS NULL
    THROW 50001,N'請先建立小組的 members、games、admin、admin_roles 資料表。',1;
GO

IF OBJECT_ID(N'dbo.customer_service_categories',N'U') IS NULL
BEGIN
CREATE TABLE dbo.customer_service_categories (
    category_id         INT IDENTITY(1,1) NOT NULL,
    category_name       NVARCHAR(50)      NOT NULL,
    description         NVARCHAR(200)     NULL,
    CONSTRAINT PK_customer_service_categories PRIMARY KEY CLUSTERED (category_id)
);
END;
GO

IF OBJECT_ID(N'dbo.customer_service_tickets',N'U') IS NULL
BEGIN
CREATE TABLE dbo.customer_service_tickets (
    ticket_id           INT IDENTITY(1,1) NOT NULL,
    ticket_no           VARCHAR(30)       NOT NULL, -- 外部案件流水號 (例如 CS0000000001；由後端 sequence 產生)
    member_id           INT               NOT NULL,
    category_id         INT               NOT NULL,
    game_id             INT               NULL,
    admin_id            INT               NULL, -- 負責管理員
    subject             NVARCHAR(100)     NOT NULL,
    content             NVARCHAR(MAX)     NOT NULL,
    status              VARCHAR(20)       NOT NULL DEFAULT ('OPEN'),
    created_at          DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),
    updated_at          DATETIME2         NULL,
    CONSTRAINT PK_customer_service_tickets PRIMARY KEY CLUSTERED (ticket_id),
    CONSTRAINT UQ_cst_ticket_no UNIQUE (ticket_no),
    CONSTRAINT FK_cst_members FOREIGN KEY (member_id) REFERENCES dbo.members(member_id),
    CONSTRAINT FK_cst_categories FOREIGN KEY (category_id) REFERENCES dbo.customer_service_categories(category_id),
    CONSTRAINT FK_cst_games FOREIGN KEY (game_id) REFERENCES dbo.games(game_id),
    CONSTRAINT FK_cst_admins FOREIGN KEY (admin_id) REFERENCES dbo.admin(admin_id),
    CONSTRAINT CK_cst_status CHECK (status IN ('OPEN','IN_PROGRESS','CLOSED')),
    CONSTRAINT UQ_cst_owner UNIQUE (ticket_id, member_id)
);
END;
GO

IF OBJECT_ID(N'dbo.customer_service_messages',N'U') IS NULL
BEGIN
CREATE TABLE dbo.customer_service_messages (
    message_id          INT IDENTITY(1,1) NOT NULL,
    ticket_id           INT               NOT NULL,
    sender_member_id    INT               NULL,
    admin_id            INT               NULL,
    sender_type         VARCHAR(10)       NOT NULL, -- 'MEMBER' 或 'ADMIN'
    message_content     NVARCHAR(MAX)     NOT NULL,
    sent_at             DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),
    CONSTRAINT PK_customer_service_messages PRIMARY KEY CLUSTERED (message_id),
    CONSTRAINT FK_csm_tickets FOREIGN KEY (ticket_id) REFERENCES dbo.customer_service_tickets(ticket_id),
    CONSTRAINT FK_csm_members FOREIGN KEY (sender_member_id) REFERENCES dbo.members(member_id),
    CONSTRAINT FK_csm_admins FOREIGN KEY (admin_id) REFERENCES dbo.admin(admin_id),
    CONSTRAINT CK_csm_sender CHECK (
        (sender_type = 'MEMBER' AND sender_member_id IS NOT NULL AND admin_id IS NULL)
        OR (sender_type = 'ADMIN' AND sender_member_id IS NULL AND admin_id IS NOT NULL)
    ),
    CONSTRAINT FK_csm_ticket_owner FOREIGN KEY (ticket_id, sender_member_id) REFERENCES dbo.customer_service_tickets(ticket_id, member_id)
);
END;
GO

IF OBJECT_ID(N'dbo.chat_rooms',N'U') IS NULL
BEGIN
CREATE TABLE dbo.chat_rooms (
    support_room_id     INT IDENTITY(1,1) NOT NULL,
    member_id           INT               NOT NULL,
    ticket_id           INT               NULL, -- 轉案件後的關聯
    admin_id            INT               NULL,
    status              VARCHAR(20)       NOT NULL DEFAULT ('OPEN'),
    created_at          DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),
    closed_at           DATETIME2         NULL,
    CONSTRAINT PK_chat_rooms PRIMARY KEY CLUSTERED (support_room_id),
    CONSTRAINT FK_chat_rooms_members FOREIGN KEY (member_id) REFERENCES dbo.members(member_id),
    CONSTRAINT FK_chat_rooms_admins FOREIGN KEY (admin_id) REFERENCES dbo.admin(admin_id),
    CONSTRAINT UQ_chat_rooms_owner UNIQUE (support_room_id, member_id),
    CONSTRAINT FK_chat_rooms_ticket_owner FOREIGN KEY (ticket_id, member_id)
        REFERENCES dbo.customer_service_tickets(ticket_id, member_id),
    CONSTRAINT CK_chat_rooms_ticket_closed CHECK (ticket_id IS NULL OR status = 'CLOSED'),
    CONSTRAINT CK_chat_rooms_status CHECK (status IN ('OPEN','CLOSED')),
    CONSTRAINT CK_chat_rooms_closed CHECK (
        (status = 'OPEN' AND closed_at IS NULL)
        OR (status = 'CLOSED' AND closed_at IS NOT NULL AND closed_at >= created_at)
    )
);
END;
GO

IF OBJECT_ID(N'dbo.chat_messages',N'U') IS NULL
BEGIN
CREATE TABLE dbo.chat_messages (
    support_message_id  INT IDENTITY(1,1) NOT NULL,
    support_room_id     INT               NOT NULL,
    member_id           INT               NULL,
    admin_id            INT               NULL,
    sender_type         VARCHAR(10)       NOT NULL, -- 'MEMBER' 或 'ADMIN'
    content             NVARCHAR(MAX)     NOT NULL,
    sent_at             DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),
    CONSTRAINT PK_chat_messages PRIMARY KEY CLUSTERED (support_message_id),
    CONSTRAINT FK_chat_messages_room FOREIGN KEY (support_room_id) REFERENCES dbo.chat_rooms(support_room_id),
    CONSTRAINT FK_chat_messages_members FOREIGN KEY (member_id) REFERENCES dbo.members(member_id),
    CONSTRAINT FK_chat_messages_admins FOREIGN KEY (admin_id) REFERENCES dbo.admin(admin_id),
    CONSTRAINT CK_chat_messages_sender CHECK (
        (sender_type = 'MEMBER' AND member_id IS NOT NULL AND admin_id IS NULL)
        OR (sender_type = 'ADMIN' AND member_id IS NULL AND admin_id IS NOT NULL)
    ),
    CONSTRAINT FK_chat_messages_room_owner FOREIGN KEY (support_room_id, member_id) REFERENCES dbo.chat_rooms(support_room_id, member_id)
);
END;
GO

IF OBJECT_ID(N'dbo.customer_service_attachments',N'U') IS NULL
BEGIN
CREATE TABLE dbo.customer_service_attachments (
    attachment_id INT IDENTITY(1,1) NOT NULL,
    message_id INT NOT NULL,
    file_name NVARCHAR(120) NOT NULL,
    content_type VARCHAR(20) NOT NULL,
    size_bytes INT NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    image_data VARBINARY(MAX) NOT NULL,
    created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT PK_customer_service_attachments PRIMARY KEY (attachment_id),
    CONSTRAINT FK_csa_message FOREIGN KEY (message_id)
        REFERENCES dbo.customer_service_messages(message_id),
    CONSTRAINT CK_csa_content_type CHECK(content_type IN ('image/jpeg','image/png')),
    CONSTRAINT CK_csa_size CHECK(size_bytes BETWEEN 1 AND 5242880 AND DATALENGTH(image_data)=size_bytes),
    CONSTRAINT CK_csa_dimensions CHECK(width BETWEEN 1 AND 4096 AND height BETWEEN 1 AND 4096
        AND CONVERT(BIGINT,width)*height<=12000000)
);
END;
GO

IF OBJECT_ID(N'dbo.customer_service_ticket_games',N'U') IS NULL
BEGIN
CREATE TABLE dbo.customer_service_ticket_games (
    ticket_id           INT NOT NULL,
    game_id             INT NOT NULL,
    sort_order          INT NOT NULL DEFAULT (0),
    CONSTRAINT PK_customer_service_ticket_games PRIMARY KEY (ticket_id, game_id),
    CONSTRAINT FK_cstg_ticket FOREIGN KEY (ticket_id)
        REFERENCES dbo.customer_service_tickets(ticket_id),
    CONSTRAINT FK_cstg_game FOREIGN KEY (game_id)
        REFERENCES dbo.games(game_id),
    CONSTRAINT CK_cstg_sort CHECK (sort_order >= 0)
);
END;
GO


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


IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='UX_chat_rooms_one_open_member' AND object_id=OBJECT_ID('dbo.chat_rooms'))
CREATE UNIQUE INDEX UX_chat_rooms_one_open_member
    ON dbo.chat_rooms(member_id)
    WHERE status = 'OPEN';
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_csa_message' AND object_id=OBJECT_ID('dbo.customer_service_attachments'))
CREATE INDEX IX_csa_message ON dbo.customer_service_attachments(message_id,attachment_id);
GO

-- HERO 通知系統：既有 GamePlatformDB 升級使用，不會重建資料庫。

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

-- 僅補缺少的客服分類。
INSERT INTO dbo.customer_service_categories(category_name)
SELECT v.name FROM (VALUES (N'帳號問題'),(N'遊戲問題'),(N'購買問題'),(N'其他問題')) v(name)
WHERE NOT EXISTS (SELECT 1 FROM dbo.customer_service_categories c WHERE c.category_name=v.name);
GO

-- 沿用案件流水號；缺少時從現有最大 CS 編號之後開始，不重設既有序號。
IF OBJECT_ID(N'dbo.customer_service_ticket_no_seq',N'SO') IS NULL
BEGIN
    DECLARE @next BIGINT;
    SELECT @next=COALESCE(MAX(TRY_CONVERT(BIGINT,SUBSTRING(ticket_no,3,28))),0)+1
    FROM dbo.customer_service_tickets WHERE ticket_no LIKE 'CS%';
    DECLARE @sql NVARCHAR(300)=N'CREATE SEQUENCE dbo.customer_service_ticket_no_seq AS BIGINT START WITH '
        +CONVERT(NVARCHAR(30),@next)+N' INCREMENT BY 1 NO CYCLE';
    EXEC sys.sp_executesql @sql;
END;
GO
