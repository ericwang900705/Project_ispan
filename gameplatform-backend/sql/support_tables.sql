-- 客服所需資料表；僅建立缺少的表，不建立會員、管理員、遊戲或登入資料。
-- 先確認主系統已有 dbo.members、dbo.admin、dbo.games。
-- 既有同名表不覆蓋，欄位差異請比對交接文件。

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
CREATE INDEX IX_csa_message ON dbo.customer_service_attachments(message_id,attachment_id);
END;
GO
