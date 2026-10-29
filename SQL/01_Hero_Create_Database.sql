-- 01｜GamePlatformDB 完整建庫（2026-10-29：全新安裝版）
-- SSMS 請先啟用「查詢 → SQLCMD 模式」，整份執行；遇錯即停止。
-- 本檔只供全新建庫。既有 GamePlatformDB 會停止，不會自動刪除你的舊資料庫。
-- 順序：01 建庫 → 02 六款 CART + 十二款 AI 遊戲 → 03 示範帳號與資料。
-- 01 會初始化角色、權限及客服 FAQ；03 才建立固定最高管理員 hero_admin。
-- UTF-8 不含 BOM；不用再另外選第 10、11 段重跑。
:ON ERROR EXIT
USE [master];
GO
IF DB_ID(N'GamePlatformDB') IS NOT NULL
    THROW 51200, N'GamePlatformDB 已存在。本檔僅供全新建庫；既有資料庫請使用增量更新 SQL。', 1;
GO

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
GO

-- =============================================================================
-- 遊戲平台資料庫建立腳本 (SQL Server T-SQL)
-- =============================================================================

CREATE DATABASE GamePlatformDB;
GO

USE GamePlatformDB;
GO

-- =============================================================================
-- 1. 會員與認證模組
-- =============================================================================

-- 會員主表
CREATE TABLE dbo.members (
    member_id           INT IDENTITY(1,1) NOT NULL,
    username            NVARCHAR(30)      NOT NULL,
    national_id         VARCHAR(10)       NULL,
    email               VARCHAR(100)      NOT NULL,
    birth_date          DATE              NULL,
    address             NVARCHAR(500)     NULL,
    avatar_url          VARCHAR(500)      NULL,
    failed_login_count  INT               NOT NULL DEFAULT (0),
    locked_until        DATETIME2         NULL,
    total_points        INT               NOT NULL DEFAULT (0),
    balance             INT               NOT NULL DEFAULT (0),
    is_publisher        BIT               NOT NULL DEFAULT (0),
    company_name        NVARCHAR(100)     NULL,
    tax_id              VARCHAR(10)       NULL,
    website_url         VARCHAR(255)      NULL,
    bank_name           NVARCHAR(50)      NULL,
    bank_account        VARCHAR(50)       NULL,
    publisher_logo_url  VARCHAR(500)      NULL,
    publisher_description NVARCHAR(MAX)   NULL,
    created_at          DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),
    CONSTRAINT PK_members PRIMARY KEY CLUSTERED (member_id),
    CONSTRAINT UQ_members_username UNIQUE (username),
    CONSTRAINT UQ_members_email UNIQUE (email),
    CONSTRAINT CK_members_points_non_negative CHECK (total_points >= 0 AND balance >= 0),
    CONSTRAINT CK_members_failed_login_count CHECK (failed_login_count >= 0)
);
GO

-- 會員註冊驗證紀錄表
CREATE TABLE dbo.member_verifications (
    verification_id     INT IDENTITY(1,1) NOT NULL,
    email               VARCHAR(100)      NOT NULL,
    verification_token  VARCHAR(200)      NOT NULL,
    sent_at             DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),
    expires_at          DATETIME2         NOT NULL,
    is_verified         BIT               NOT NULL DEFAULT (0),
    verified_at         DATETIME2         NULL,
    CONSTRAINT PK_member_verifications PRIMARY KEY CLUSTERED (verification_id),
    CONSTRAINT CK_member_verifications_expiry CHECK (expires_at >= sent_at)
);
GO

-- 密碼重設紀錄表
CREATE TABLE dbo.password_resets (
    reset_id            INT IDENTITY(1,1) NOT NULL,
    member_id           INT               NOT NULL,
    reset_token         VARCHAR(200)      NOT NULL,
    sent_at             DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),
    expires_at          DATETIME2         NOT NULL,
    is_reset            BIT               NOT NULL DEFAULT (0),
    reset_at            DATETIME2         NULL,
    CONSTRAINT PK_password_resets PRIMARY KEY CLUSTERED (reset_id),
    CONSTRAINT FK_password_resets_members FOREIGN KEY (member_id) REFERENCES dbo.members(member_id),
    CONSTRAINT CK_password_resets_expiry CHECK (expires_at >= sent_at)
);
GO

-- 歷史密碼紀錄表
CREATE TABLE dbo.password_history (
    password_history_id INT IDENTITY(1,1) NOT NULL,
    member_id           INT               NOT NULL,
    password_hash       VARCHAR(255)      NOT NULL,
    created_at          DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),
    CONSTRAINT PK_password_history PRIMARY KEY CLUSTERED (password_history_id),
    CONSTRAINT FK_password_history_members FOREIGN KEY (member_id) REFERENCES dbo.members(member_id)
);
GO

-- 會員登錄紀錄表
CREATE TABLE dbo.member_login_logs (
    login_log_id        BIGINT IDENTITY(1,1) NOT NULL,
    member_id           INT                  NULL,
    ip_address          VARCHAR(45)          NOT NULL,
    login_status        VARCHAR(20)          NOT NULL,
    login_at            DATETIME2            NOT NULL DEFAULT (SYSDATETIME()),
    CONSTRAINT PK_member_login_logs PRIMARY KEY CLUSTERED (login_log_id),
    CONSTRAINT FK_member_login_logs_members FOREIGN KEY (member_id) REFERENCES dbo.members(member_id),
    CONSTRAINT CK_member_login_logs_status CHECK (login_status IN ('SUCCESS', 'FAILED', 'LOCKED'))
);
GO

--串google登入
CREATE TABLE dbo.member_oauth_accounts (
    oauth_id        INT IDENTITY(1,1) NOT NULL,
    member_id       INT               NOT NULL, -- 關聯到 members.member_id
    provider        VARCHAR(20)       NOT NULL, -- 'GOOGLE', 'STEAM', 'DISCORD', 'APPLE'
    provider_user_id VARCHAR(100)     NOT NULL, -- Google 的 sub / 各平台唯一 ID
    email           VARCHAR(100)      NULL,     -- 該第三方回傳的 Email
    created_at      DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_member_oauth_accounts PRIMARY KEY CLUSTERED (oauth_id),
    CONSTRAINT FK_oauth_members FOREIGN KEY (member_id) REFERENCES dbo.members(member_id),
    -- 確保同一個第三方平台的同一個帳號只能綁定一次
    CONSTRAINT UQ_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT CK_member_oauth_provider CHECK (provider IN ('GOOGLE'))
);

-- =============================================================================
-- 2. 管理者與權限模組
-- =============================================================================

-- 管理者角色表
CREATE TABLE dbo.admin_roles (
    admin_role_id       INT IDENTITY(1,1) NOT NULL,
    role_code           VARCHAR(50)       NOT NULL,
    role_name           NVARCHAR(100)     NOT NULL,
    description         NVARCHAR(255)     NULL,
    CONSTRAINT PK_admin_roles PRIMARY KEY CLUSTERED (admin_role_id),
    CONSTRAINT UQ_admin_roles_role_code UNIQUE (role_code)
);
GO

-- 管理者主表(1007_7調整)
CREATE TABLE dbo.admin (
    admin_id            INT IDENTITY(1,1) NOT NULL,
    admin_role_id       INT               NOT NULL,
    admin_account       VARCHAR(50)       NOT NULL,
    admin_name          NVARCHAR(50)      NOT NULL,
    email               VARCHAR(100)      NOT NULL,
    password_hash       VARCHAR(255)      NOT NULL,
    status              VARCHAR(20)       NOT NULL DEFAULT ('ACTIVE'),
    last_login_at       DATETIME2         NULL,
    created_at          DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),
    updated_at          DATETIME2         NULL,
    CONSTRAINT PK_admin PRIMARY KEY CLUSTERED (admin_id),
    CONSTRAINT UQ_admin_admin_account UNIQUE (admin_account),
    CONSTRAINT FK_admin_admin_roles FOREIGN KEY (admin_role_id) REFERENCES dbo.admin_roles(admin_role_id)
);
GO

-- 管理者權限表
CREATE TABLE dbo.admin_permissions (
    permission_id       INT IDENTITY(1,1) NOT NULL,
    permission_code     VARCHAR(100)      NOT NULL,
    permission_name     NVARCHAR(100)     NOT NULL,
    description         NVARCHAR(255)     NULL,
    CONSTRAINT PK_admin_permissions PRIMARY KEY CLUSTERED (permission_id),
    CONSTRAINT UQ_admin_permissions_code UNIQUE (permission_code)
);
GO

-- 管理者角色權限關聯表
CREATE TABLE dbo.admin_role_permissions (
    admin_role_id       INT       NOT NULL,
    permission_id       INT       NOT NULL,
    created_by_admin_id INT       NULL,
    created_at          DATETIME2 NOT NULL DEFAULT (SYSDATETIME()),
    CONSTRAINT PK_admin_role_permissions PRIMARY KEY CLUSTERED (admin_role_id, permission_id),
    CONSTRAINT FK_arp_admin_roles FOREIGN KEY (admin_role_id) REFERENCES dbo.admin_roles(admin_role_id),
    CONSTRAINT FK_arp_admin_permissions FOREIGN KEY (permission_id) REFERENCES dbo.admin_permissions(permission_id),
    CONSTRAINT FK_arp_created_by FOREIGN KEY (created_by_admin_id) REFERENCES dbo.admin(admin_id)
);
GO

-- 新增發行商申請紀錄表
CREATE TABLE dbo.publisher_applications (
    application_id      INT IDENTITY(1,1) NOT NULL,
    member_id           INT               NOT NULL,
    company_name        NVARCHAR(100)     NOT NULL,
    tax_id              VARCHAR(10)       NOT NULL,
    document_path       VARCHAR(500)      NOT NULL,
    approval_status     VARCHAR(20)       NOT NULL DEFAULT ('PENDING'),
    admin_id            INT               NULL,
    applied_at          DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),
    reviewed_at         DATETIME2         NULL,
    review_comment      NVARCHAR(500)     NULL,
    CONSTRAINT PK_publisher_applications PRIMARY KEY CLUSTERED (application_id),
    CONSTRAINT FK_publisher_applications_members FOREIGN KEY (member_id) REFERENCES dbo.members(member_id),
    CONSTRAINT FK_publisher_applications_admin FOREIGN KEY (admin_id) REFERENCES dbo.admin(admin_id),
    CONSTRAINT CK_publisher_apps_status CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED'))
);
GO
-- 紀錄 最高管理員 分配給每一位普通管理員的個別功能權限
CREATE TABLE dbo.admin_user_permissions (
    admin_id INT NOT NULL,
    permission_id INT NOT NULL,
    granted_by_admin_id INT NOT NULL,
    granted_at DATETIME2 NOT NULL
        DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_admin_user_permissions
        PRIMARY KEY (admin_id, permission_id),

    CONSTRAINT FK_aup_admin
        FOREIGN KEY (admin_id)
        REFERENCES dbo.admin(admin_id),

    CONSTRAINT FK_aup_permission
        FOREIGN KEY (permission_id)
        REFERENCES dbo.admin_permissions(permission_id),

    CONSTRAINT FK_aup_granted_by
        FOREIGN KEY (granted_by_admin_id)
        REFERENCES dbo.admin(admin_id)
);

-- =============================================================================
-- 3. 好友與社交模組
-- =============================================================================

-- 好友關係表
CREATE TABLE dbo.friendships (
    friendship_id       INT IDENTITY(1,1) NOT NULL,
    requester_member_id INT               NOT NULL,
    receiver_member_id  INT               NOT NULL,
    friend_status       VARCHAR(20)       NOT NULL DEFAULT ('PENDING'),
    created_at          DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),
    updated_at          DATETIME2         NULL,
    CONSTRAINT PK_friendships PRIMARY KEY CLUSTERED (friendship_id),
    CONSTRAINT FK_friendships_requester FOREIGN KEY (requester_member_id) REFERENCES dbo.members(member_id),
    CONSTRAINT FK_friendships_receiver FOREIGN KEY (receiver_member_id) REFERENCES dbo.members(member_id),
    CONSTRAINT CK_friendships_no_self_friend CHECK (requester_member_id <> receiver_member_id),
    CONSTRAINT CK_friendships_status CHECK (friend_status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'BLOCKED')),
    CONSTRAINT UQ_friendships_pair UNIQUE (requester_member_id, receiver_member_id)
);
GO

-- 好友聊天室(調整)
CREATE TABLE dbo.friend_rooms (
    friend_room_id      INT IDENTITY(1,1) NOT NULL,
    room_type           VARCHAR(20)       NOT NULL DEFAULT ('chatroom'),
    room_name           NVARCHAR(100)     NULL,
    creator_member_id           INT               NOT NULL,
    created_at          DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),
    CONSTRAINT PK_friend_rooms PRIMARY KEY CLUSTERED (friend_room_id),
    CONSTRAINT FK_friend_rooms_creator FOREIGN KEY (creator_member_id) REFERENCES dbo.members(member_id),
    CONSTRAINT CK_friend_rooms_type CHECK (room_type IN ('DIRECT', 'GROUP', 'chatroom'))
);
GO

-- 好友聊天室參與成員；不限制一人能參與幾個好友聊天室。
-- 建立房間時，後端須在同一交易內將建立者及受邀成員加入此表。
-- 有歷史訊息的成員以 left_at 標記離開，勿直接刪除；發送前檢查 left_at IS NULL。
CREATE TABLE dbo.friend_room_members (
    friend_room_id INT NOT NULL,
    member_id INT NOT NULL,
    joined_at DATETIME2 NOT NULL DEFAULT (SYSDATETIME()),
    left_at DATETIME2 NULL,
    CONSTRAINT PK_friend_room_members PRIMARY KEY (friend_room_id, member_id),
    CONSTRAINT FK_friend_room_members_room FOREIGN KEY (friend_room_id) REFERENCES dbo.friend_rooms(friend_room_id),
    CONSTRAINT FK_friend_room_members_member FOREIGN KEY (member_id) REFERENCES dbo.members(member_id),
    CONSTRAINT CK_friend_room_members_period CHECK (left_at IS NULL OR left_at >= joined_at)
);
GO
CREATE INDEX IX_friend_room_members_member ON dbo.friend_room_members(member_id, friend_room_id);
GO

-- 聊天室訊息表OK
CREATE TABLE dbo.friend_messages (
    friend_message_id   BIGINT IDENTITY(1,1) NOT NULL,
    friend_room_id      INT                  NOT NULL,
    member_id           INT                  NOT NULL,
    message_type        VARCHAR(20)          NOT NULL DEFAULT ('TEXT'),
    content             NVARCHAR(MAX)        NOT NULL,
    created_at          DATETIME2            NOT NULL DEFAULT (SYSDATETIME()),
    CONSTRAINT PK_friend_messages PRIMARY KEY CLUSTERED (friend_message_id),
    CONSTRAINT FK_friend_messages_room FOREIGN KEY (friend_room_id) REFERENCES dbo.friend_rooms(friend_room_id),
    CONSTRAINT FK_friend_messages_sender FOREIGN KEY (member_id) REFERENCES dbo.members(member_id)
);
GO

-- =============================================================================
-- 4. 遊戲、媒體與標籤模組
-- =============================================================================

-- 遊戲種類標籤表
CREATE TABLE dbo.tags (
    tag_id              INT IDENTITY(1,1) NOT NULL,
    tag_name            NVARCHAR(50)      NOT NULL,
    CONSTRAINT PK_tags PRIMARY KEY CLUSTERED (tag_id),
    CONSTRAINT UQ_tags_name UNIQUE (tag_name)
);
GO

-- CART 五種標籤 + AI 遊戲五種新增標籤；本檔僅供從零建庫
INSERT INTO dbo.tags (tag_name)
SELECT v.tag_name
FROM (VALUES
    (N'動作冒險'),
    (N'角色扮演'),
    (N'競速遊戲'),
    (N'射擊'),
    (N'策略'),
    (N'Roguelike'),
    (N'冒險'),
    (N'格鬥'),
    (N'經營養成'),
    (N'運動')
) AS v(tag_name)
WHERE NOT EXISTS (SELECT 1 FROM dbo.tags AS t WHERE t.tag_name = v.tag_name);
GO

-- 遊戲主表(1006新增狀態欄位,上下架,草稿狀態,更改release_date型別)
CREATE TABLE dbo.games (
    game_id             INT IDENTITY(1,1) NOT NULL,
    member_id           INT               NOT NULL, -- 發行商會員
    game_name           NVARCHAR(150)     NOT NULL,
    release_date        DATETIME2         NULL,
    price               DECIMAL(10,2)     NOT NULL DEFAULT (0.00),
    description         NVARCHAR(MAX)     NULL,
    cover_url           VARCHAR(500)      NULL,
    status              VARCHAR(20)       NOT NULL DEFAULT ('DRAFT'),
    scheduled_off_shelf_at DATETIME2       NULL, -- 申請下架後預定執行的時間
    created_at          DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),
    CONSTRAINT PK_games PRIMARY KEY CLUSTERED (game_id),
    CONSTRAINT FK_games_publishers FOREIGN KEY (member_id) REFERENCES dbo.members(member_id),
    CONSTRAINT UQ_game_name UNIQUE (game_name),
    CONSTRAINT CK_games_price CHECK (price >= 0),
    CONSTRAINT CK_games_status CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'COMING_SOON', 'PUBLISHED', 'PENDING_OFF_SHELF', 'OFF_SHELF'))
);
GO

-- 已上架遊戲標籤修改紀錄：每款遊戲獨立記錄最後一次修改時間。
-- 後端依 last_changed_at 檢查是否已間隔滿 7 天；首次修改不需要預先建立紀錄。
CREATE TABLE dbo.publisher_game_tag_changes (
    game_id INT NOT NULL,
    last_changed_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_publisher_game_tag_changes_time DEFAULT (SYSDATETIME()),
    CONSTRAINT PK_publisher_game_tag_changes PRIMARY KEY (game_id),
    CONSTRAINT FK_publisher_game_tag_changes_game FOREIGN KEY (game_id) REFERENCES dbo.games(game_id)
);
GO

-- 遊戲與種類標籤關聯表 (多對多)
CREATE TABLE dbo.game_tags (
    game_id             INT       NOT NULL,
    tag_id              INT       NOT NULL,
    created_at          DATETIME2 NOT NULL DEFAULT (SYSDATETIME()),
    CONSTRAINT PK_game_tags PRIMARY KEY CLUSTERED (game_id, tag_id),
    CONSTRAINT FK_game_tags_game FOREIGN KEY (game_id) REFERENCES dbo.games(game_id),
    CONSTRAINT FK_game_tags_tag FOREIGN KEY (tag_id) REFERENCES dbo.tags(tag_id)
);
GO

-- 遊戲媒體資料表
CREATE TABLE dbo.game_media (
    media_id            INT IDENTITY(1,1) NOT NULL,
    game_id             INT               NOT NULL,
    media_type          VARCHAR(20)       NOT NULL,
    media_url           VARCHAR(500)      NOT NULL,
    display_order       INT               NOT NULL DEFAULT (1),
    created_at          DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),
    CONSTRAINT PK_game_media PRIMARY KEY CLUSTERED (media_id),
    CONSTRAINT FK_game_media_game FOREIGN KEY (game_id) REFERENCES dbo.games(game_id),
    CONSTRAINT CK_game_media_type CHECK (media_type IN ('IMAGE', 'VIDEO')),
    CONSTRAINT CK_game_media_order CHECK (display_order >= 1)
);
GO

-- AI 遊戲標籤表
CREATE TABLE dbo.ai_game_tags (
    tag_id              INT IDENTITY(1,1) NOT NULL,
    tag_name            NVARCHAR(50)      NOT NULL,
    description         NVARCHAR(255)     NULL,
    is_active           BIT               NOT NULL DEFAULT (1),
    CONSTRAINT PK_ai_game_tags PRIMARY KEY CLUSTERED (tag_id)
);
GO

-- AI 遊戲標籤判斷結果表
CREATE TABLE dbo.ai_game_tag_results (
    result_id           INT IDENTITY(1,1) NOT NULL,
    game_id             INT               NOT NULL,
    tag_id              INT               NOT NULL,
    confidence_score    DECIMAL(5,4)      NOT NULL,
    reason              NVARCHAR(500)     NULL,
    model_version       VARCHAR(50)       NULL,
    evaluated_at        DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),
    CONSTRAINT PK_ai_game_tag_results PRIMARY KEY CLUSTERED (result_id),
    CONSTRAINT FK_ai_results_game FOREIGN KEY (game_id) REFERENCES dbo.games(game_id),
    CONSTRAINT FK_ai_results_tag FOREIGN KEY (tag_id) REFERENCES dbo.ai_game_tags(tag_id),
    CONSTRAINT CK_ai_results_confidence CHECK (confidence_score BETWEEN 0 AND 1)
);
GO
-- 遊戲程式建立表(1007新增)
CREATE TABLE dbo.game_builds (
    build_id        INT IDENTITY(1,1) NOT NULL,
    game_id         INT NOT NULL,
    version         VARCHAR(30) NOT NULL,
    file_url        VARCHAR(500) NOT NULL,
    file_size       BIGINT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT ('ACTIVE'),
    uploaded_at     DATETIME2 NOT NULL DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_game_builds
        PRIMARY KEY (build_id),

    CONSTRAINT FK_game_builds_games
        FOREIGN KEY (game_id)
        REFERENCES dbo.games(game_id),

    CONSTRAINT CK_game_builds_status
        CHECK (status IN ('ACTIVE', 'INACTIVE')),

    CONSTRAINT CK_game_builds_file_size
        CHECK (file_size IS NULL OR file_size >= 0)
);
GO
-- 新增AI個人化遊戲推薦表(1012)
CREATE TABLE dbo.ai_game_recommendations (
    recommendation_id INT IDENTITY(1,1) PRIMARY KEY,

    member_id INT NOT NULL,
    game_id INT NOT NULL,

    recommendation_score DECIMAL(5,4) NOT NULL,

    reason NVARCHAR(500) NULL,

    model_version VARCHAR(50) NULL,

    created_at DATETIME2 NOT NULL
        DEFAULT SYSDATETIME(),

    CONSTRAINT FK_ai_recommendations_member
        FOREIGN KEY (member_id)
        REFERENCES dbo.members(member_id),

    CONSTRAINT FK_ai_recommendations_game
        FOREIGN KEY (game_id)
        REFERENCES dbo.games(game_id),

    CONSTRAINT CK_ai_recommendations_score
        CHECK (
            recommendation_score >= 0
            AND recommendation_score <= 1
        ),

    CONSTRAINT UQ_ai_recommendations_member_game
        UNIQUE (member_id, game_id)
);
GO
-- 新增AI 推薦更新狀態表(1013)
CREATE TABLE dbo.ai_recommendation_refresh_status (
    member_id INT NOT NULL PRIMARY KEY,

    refresh_status VARCHAR(20) NOT NULL
        DEFAULT 'PENDING',

    last_attempt_at DATETIME2 NULL,

    last_success_at DATETIME2 NULL,

    next_refresh_at DATETIME2 NULL,

    last_error NVARCHAR(500) NULL,


    -- AI 推薦更新任務識別碼(1013)
    refresh_token UNIQUEIDENTIFIER NULL,

    CONSTRAINT FK_ai_refresh_member
        FOREIGN KEY (member_id)
        REFERENCES dbo.members(member_id),

    CONSTRAINT CK_ai_refresh_status
        CHECK (
            refresh_status IN (
                'PENDING',
                'PROCESSING',
                'SUCCESS',
                'FAILED'
            )
        )
);
GO
-- AI推薦快取狀態初始化(1013)
INSERT INTO dbo.ai_recommendation_refresh_status (
    member_id,
    refresh_status,
    last_attempt_at,
    last_success_at,
    next_refresh_at
)
SELECT
    r.member_id,
    'SUCCESS',
    MAX(r.created_at),
    MAX(r.created_at),
    DATEADD(DAY, 7, MAX(r.created_at))
FROM dbo.ai_game_recommendations r
WHERE r.created_at IS NOT NULL
    AND NOT EXISTS (
        SELECT 1
        FROM dbo.ai_recommendation_refresh_status s
        WHERE s.member_id = r.member_id
    )
GROUP BY r.member_id;
GO

-- 會員遊戲瀏覽統計表（AI 個人化推薦1013）
CREATE TABLE dbo.member_game_view_stats (
    member_id           INT               NOT NULL,
    game_id             INT               NOT NULL,
    view_count          INT               NOT NULL DEFAULT (1),
    first_viewed_at     DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),
    last_viewed_at      DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_member_game_view_stats
        PRIMARY KEY CLUSTERED (member_id, game_id),

    CONSTRAINT FK_member_game_view_stats_member
        FOREIGN KEY (member_id)
        REFERENCES dbo.members(member_id),

    CONSTRAINT FK_member_game_view_stats_game
        FOREIGN KEY (game_id)
        REFERENCES dbo.games(game_id),

    CONSTRAINT CK_member_game_view_stats_count
        CHECK (view_count >= 1),

    CONSTRAINT CK_member_game_view_stats_time
        CHECK (last_viewed_at >= first_viewed_at)
);
GO
-- 會員遊戲標籤搜尋統計表（AI 個人化推薦1013）
CREATE TABLE dbo.member_tag_search_stats (
    member_id           INT               NOT NULL,
    tag_id              INT               NOT NULL,
    search_count        INT               NOT NULL DEFAULT (1),
    first_searched_at   DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),
    last_searched_at    DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_member_tag_search_stats
        PRIMARY KEY CLUSTERED (member_id, tag_id),

    CONSTRAINT FK_member_tag_search_stats_member
        FOREIGN KEY (member_id)
        REFERENCES dbo.members(member_id),

    CONSTRAINT FK_member_tag_search_stats_tag
        FOREIGN KEY (tag_id)
        REFERENCES dbo.tags(tag_id),

    CONSTRAINT CK_member_tag_search_stats_count
        CHECK (search_count >= 1),

    CONSTRAINT CK_member_tag_search_stats_time
        CHECK (last_searched_at >= first_searched_at)
);
GO


-- 遊戲遊玩紀錄表
CREATE TABLE dbo.play_records (
    play_record_id      INT IDENTITY(1,1) NOT NULL,
    member_id           INT               NOT NULL,
    game_id             INT               NOT NULL,
    started_at          DATETIME2         NOT NULL,
    ended_at            DATETIME2         NULL,
    play_seconds        INT               NOT NULL DEFAULT (0),
    CONSTRAINT PK_play_records PRIMARY KEY CLUSTERED (play_record_id),
    CONSTRAINT FK_play_records_members FOREIGN KEY (member_id) REFERENCES dbo.members(member_id),
    CONSTRAINT FK_play_records_games FOREIGN KEY (game_id) REFERENCES dbo.games(game_id),
    CONSTRAINT CK_play_records_seconds CHECK (play_seconds >= 0),
    CONSTRAINT CK_play_records_time CHECK (ended_at IS NULL OR ended_at >= started_at)
);
GO

-- 遊戲評論表
CREATE TABLE dbo.game_reviews (
    review_id           INT IDENTITY(1,1) NOT NULL,
    member_id           INT               NOT NULL,
    game_id             INT               NOT NULL,
    rating              INT               NOT NULL,
    is_recommended      BIT               NOT NULL DEFAULT (1),
    content             NVARCHAR(MAX)     NULL,
    status              VARCHAR(20)       NOT NULL DEFAULT ('NORMAL'),
    created_at          DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),
    updated_at          DATETIME2         NULL,
    CONSTRAINT PK_game_reviews PRIMARY KEY CLUSTERED (review_id),
    CONSTRAINT FK_game_reviews_members FOREIGN KEY (member_id) REFERENCES dbo.members(member_id),
    CONSTRAINT FK_game_reviews_games FOREIGN KEY (game_id) REFERENCES dbo.games(game_id),
    CONSTRAINT UQ_game_reviews_member_game UNIQUE (member_id, game_id),
    CONSTRAINT CK_game_reviews_rating CHECK (rating BETWEEN 1 AND 5) -- 採 1～5 分
);
GO

--新增遊戲審核紀錄表(1007)(1007_4移位置FK才能抓到)
CREATE TABLE dbo.game_audit_logs (
    game_audit_id  INT IDENTITY(1,1) NOT NULL,
    game_id        INT NOT NULL,
    admin_id       INT NOT NULL,
    review_type    VARCHAR(20) NOT NULL,
    decision       VARCHAR(20) NOT NULL,
    comment        NVARCHAR(500) NULL,
    reviewed_at    DATETIME2 NOT NULL DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_game_audit_logs
        PRIMARY KEY (game_audit_id),

    CONSTRAINT FK_game_audit_logs_game
        FOREIGN KEY (game_id)
        REFERENCES dbo.games(game_id),

    CONSTRAINT FK_game_audit_logs_admin
        FOREIGN KEY (admin_id)
        REFERENCES dbo.admin(admin_id),

    CONSTRAINT CK_game_audit_logs_type
        CHECK (review_type IN ('PUBLISH', 'OFF_SHELF')),

    CONSTRAINT CK_game_audit_logs_decision
        CHECK (decision IN ('APPROVED', 'REJECTED'))
);
GO
-- =============================================================================
-- 5. 商城、購物車、訂單與優惠券模組
-- =============================================================================

-- 1. 優惠券表
CREATE TABLE dbo.coupons (
    coupon_id            INT IDENTITY(1,1) NOT NULL,
    coupon_name          NVARCHAR(100)     NOT NULL,
    discount_type        VARCHAR(10)       NOT NULL,         
    discount_value       DECIMAL(10,2)     NOT NULL,         
    min_spend            DECIMAL(10,2)     NOT NULL DEFAULT (0.00),
    total_limit          INT               NULL,             
    per_member_limit     INT               NOT NULL DEFAULT (1),
    used_count           INT               NOT NULL DEFAULT (0),
    status               VARCHAR(20)       NOT NULL DEFAULT ('ACTIVE'),
    start_at             DATETIME2         NOT NULL,         
    expires_at           DATETIME2         NOT NULL,
    created_at           DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),
    CONSTRAINT PK_coupons PRIMARY KEY CLUSTERED (coupon_id),
    CONSTRAINT CK_coupons_type   CHECK (discount_type IN ('RATE','AMOUNT')),
    CONSTRAINT CK_coupons_value  CHECK (
        (discount_type = 'RATE'   AND discount_value > 0 AND discount_value < 1)
     OR (discount_type = 'AMOUNT' AND discount_value > 0)
    ),
    CONSTRAINT CK_coupons_status CHECK (status IN ('ACTIVE','INACTIVE','EXPIRED')),
    CONSTRAINT CK_coupons_period CHECK (expires_at > start_at),
    CONSTRAINT CK_coupons_limit  CHECK (total_limit IS NULL OR used_count <= total_limit),
    CONSTRAINT CK_coupons_per_member CHECK (per_member_limit > 0),
    CONSTRAINT CK_coupons_nonnegative CHECK (used_count >= 0 AND min_spend >= 0 AND (total_limit IS NULL OR total_limit >= 0))
);
GO
 
-- 2. 購物車表
CREATE TABLE dbo.cart_items (
    cart_item_id         INT IDENTITY(1,1) NOT NULL,
    member_id            INT               NOT NULL,
    game_id              INT               NOT NULL,
    is_gift              BIT               NOT NULL DEFAULT (0),
    recipient_member_id  INT               NULL,
    coupon_id            INT               NULL,
    created_at           DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),
    CONSTRAINT PK_cart_items PRIMARY KEY CLUSTERED (cart_item_id),
    CONSTRAINT FK_cart_items_member    FOREIGN KEY (member_id)           REFERENCES dbo.members(member_id),
    CONSTRAINT FK_cart_items_game      FOREIGN KEY (game_id)             REFERENCES dbo.games(game_id),
    CONSTRAINT FK_cart_items_recipient FOREIGN KEY (recipient_member_id) REFERENCES dbo.members(member_id),
    CONSTRAINT FK_cart_items_coupon    FOREIGN KEY (coupon_id)           REFERENCES dbo.coupons(coupon_id),
    CONSTRAINT UQ_cart_items_unique UNIQUE (member_id, game_id, recipient_member_id),
    CONSTRAINT CK_cart_items_gift CHECK (
        (is_gift = 0 AND recipient_member_id IS NULL)
     OR (is_gift = 1 AND recipient_member_id IS NOT NULL AND recipient_member_id <> member_id)
    )
);
GO
-- 依 member_id 查詢已被 UQ_cart_items_unique 的最左欄位涵蓋，不需另建索引
 
-- 3. 訂單主表
CREATE TABLE dbo.orders (
    order_id             INT IDENTITY(1,1) NOT NULL,
    order_no             VARCHAR(30)       NOT NULL,
    member_id            INT               NOT NULL,
    subtotal_amount      DECIMAL(10,2)     NOT NULL DEFAULT (0.00),   
    discount_amount      DECIMAL(10,2)     NOT NULL DEFAULT (0.00),   
    total_amount         DECIMAL(10,2)     NOT NULL DEFAULT (0.00),   
    order_status         VARCHAR(20)       NOT NULL DEFAULT ('PENDING'),
    points_earned        INT               NOT NULL DEFAULT (0),
    created_at           DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),
    paid_at              DATETIME2         NULL,
    ecpay_trade_no      VARCHAR(20)       NULL, -- 綠界交易編號（付款後由綠界回傳）
    payment_type        VARCHAR(50)       NULL, -- 綠界付款方式（如 Credit、ATM、CVS）
    cancelled_at         DATETIME2         NULL,
    updated_at           DATETIME2         NULL,
    CONSTRAINT PK_orders PRIMARY KEY CLUSTERED (order_id),
    CONSTRAINT FK_orders_members FOREIGN KEY (member_id) REFERENCES dbo.members(member_id),
    CONSTRAINT UQ_orders_order_no UNIQUE (order_no),
    CONSTRAINT CK_orders_status CHECK (order_status IN ('PENDING','PAID','CANCELLED','REFUNDED')),
    CONSTRAINT CK_orders_amount CHECK (
        subtotal_amount >= 0 AND discount_amount >= 0
        AND total_amount = subtotal_amount - discount_amount
        AND total_amount >= 0
    ),
    CONSTRAINT CK_orders_points CHECK (points_earned >= 0),
    CONSTRAINT CK_orders_paid   CHECK (order_status <> 'PAID' OR paid_at IS NOT NULL)
);
GO
CREATE NONCLUSTERED INDEX IX_orders_member_created ON dbo.orders (member_id, created_at DESC);
GO
 
-- 4. 訂單明細表（金額為下單當下快照）
CREATE TABLE dbo.order_items (
    order_item_id        INT IDENTITY(1,1) NOT NULL,
    order_id             INT               NOT NULL,
    game_id              INT               NOT NULL,
    quantity             INT               NOT NULL DEFAULT (1),
    unit_price           DECIMAL(10,2)     NOT NULL,                  
    discount_amount      DECIMAL(10,2)     NOT NULL DEFAULT (0.00),   
    final_amount         AS (unit_price * quantity - discount_amount) PERSISTED,
    is_gift              BIT               NOT NULL DEFAULT (0),
    recipient_member_id  INT               NULL,
    coupon_id            INT               NULL,
    CONSTRAINT PK_order_items PRIMARY KEY CLUSTERED (order_item_id),
    CONSTRAINT FK_order_items_orders    FOREIGN KEY (order_id)            REFERENCES dbo.orders(order_id),
    CONSTRAINT FK_order_items_games     FOREIGN KEY (game_id)             REFERENCES dbo.games(game_id),
    CONSTRAINT FK_order_items_recipient FOREIGN KEY (recipient_member_id) REFERENCES dbo.members(member_id),
    CONSTRAINT FK_order_items_coupon    FOREIGN KEY (coupon_id)           REFERENCES dbo.coupons(coupon_id),
    CONSTRAINT CK_order_items_qty      CHECK (quantity > 0),
    CONSTRAINT CK_order_items_price    CHECK (unit_price >= 0),
    CONSTRAINT CK_order_items_discount CHECK (discount_amount >= 0 AND discount_amount <= unit_price * quantity),
    CONSTRAINT CK_order_items_coupon   CHECK (coupon_id IS NOT NULL OR discount_amount = 0),
    CONSTRAINT CK_order_items_gift CHECK (
        (is_gift = 0 AND recipient_member_id IS NULL)
     OR (is_gift = 1 AND recipient_member_id IS NOT NULL)
    ),
    CONSTRAINT UQ_order_items_id_game UNIQUE (order_item_id, game_id)
);
GO
CREATE NONCLUSTERED INDEX IX_order_items_order_id ON dbo.order_items (order_id);
GO

-- 優惠券使用紀錄沿用 order_items.coupon_id + orders.member_id/order_status。
-- 此索引協助按優惠券查詢使用明細；每人上限不是 CHECK 能單獨保證的跨列規則。
CREATE INDEX IX_order_items_coupon_order ON dbo.order_items(coupon_id, order_id) WHERE coupon_id IS NOT NULL;
GO
-- 後端結帳須以同一交易鎖定優惠券列，檢查 total_limit / per_member_limit，
-- 再建立訂單與明細、更新 used_count；付款回呼須防止重複計數。
-- 本設計以一筆使用優惠券的訂單明細計一次；PENDING 是否預留、取消／退款是否返還須統一實作。

-- 5. 會員遊戲庫表 (Member Games)
-- 發放遊戲前，後端仍須驗證訂單 PAID、會員為買家或受贈人；跨表身分規則不由一般 CHECK 保證。
-- 同一會員同一遊戲只保留一筆；撤銷後重新取得須更新原列，歷史來源另由訂單查詢。
CREATE TABLE dbo.member_games (
    library_id           INT IDENTITY(1,1) NOT NULL,
    member_id            INT               NOT NULL,
    game_id              INT               NOT NULL,
    order_item_id        INT               NULL,          
    obtained_source      VARCHAR(20)       NOT NULL DEFAULT ('PURCHASE'),
    total_play_minutes   INT               NOT NULL DEFAULT (0),
    is_active            BIT               NOT NULL DEFAULT (1),
    obtained_at          DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),
    revoked_at           DATETIME2         NULL,          
    last_played_at       DATETIME2         NULL,
    CONSTRAINT PK_member_games PRIMARY KEY CLUSTERED (library_id),
    CONSTRAINT FK_member_games_members FOREIGN KEY (member_id)     REFERENCES dbo.members(member_id),
    CONSTRAINT FK_member_games_games   FOREIGN KEY (game_id)       REFERENCES dbo.games(game_id),
    CONSTRAINT FK_member_games_item    FOREIGN KEY (order_item_id) REFERENCES dbo.order_items(order_item_id),
    CONSTRAINT UQ_member_games_member_game UNIQUE (member_id, game_id),
    CONSTRAINT CK_member_games_source  CHECK (obtained_source IN ('PURCHASE','GIFT','FREE','ADMIN')),
    CONSTRAINT CK_member_games_order   CHECK (obtained_source NOT IN ('PURCHASE','GIFT') OR order_item_id IS NOT NULL),
    CONSTRAINT CK_member_games_minutes CHECK (total_play_minutes >= 0),
    CONSTRAINT CK_member_games_active  CHECK (
        (is_active = 1 AND revoked_at IS NULL)
     OR (is_active = 0 AND revoked_at IS NOT NULL)
    ),
    CONSTRAINT FK_member_games_item_game FOREIGN KEY (order_item_id, game_id) REFERENCES dbo.order_items(order_item_id, game_id)
);
GO
-- =============================================================================
-- 6. 點數商品與兌換模組
-- =============================================================================

-- 點數商品表
CREATE TABLE dbo.point_products (
    product_id          INT IDENTITY(1,1) NOT NULL,
    product_name        NVARCHAR(100)     NOT NULL,
    description         NVARCHAR(500)     NULL,
    point_price         INT               NOT NULL,
    image_url           VARCHAR(500)      NULL,
    status              VARCHAR(20)       NOT NULL DEFAULT ('ACTIVE'),
    created_at          DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),
    CONSTRAINT PK_point_products PRIMARY KEY CLUSTERED (product_id),
    CONSTRAINT CK_point_products_price CHECK (point_price >= 0)
);
GO

-- 點數商品兌換訂單表
CREATE TABLE dbo.point_exchange_orders (
    exchange_order_id   INT IDENTITY(1,1) NOT NULL,
    member_id           INT               NOT NULL,
    product_id          INT               NOT NULL,
    points_spent        INT               NOT NULL,
    exchange_status     VARCHAR(20)       NOT NULL DEFAULT ('COMPLETED'),
    created_at          DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),
    CONSTRAINT PK_point_exchange_orders PRIMARY KEY CLUSTERED (exchange_order_id),
    CONSTRAINT FK_peo_members FOREIGN KEY (member_id) REFERENCES dbo.members(member_id),
    CONSTRAINT FK_peo_products FOREIGN KEY (product_id) REFERENCES dbo.point_products(product_id),
    CONSTRAINT CK_peo_points CHECK (points_spent >= 0)
);
GO

-- 會員點數異動紀錄表
CREATE TABLE dbo.member_point_logs (
    log_id              BIGINT IDENTITY(1,1) NOT NULL,
    member_id           INT                  NOT NULL,
    points_change       INT                  NOT NULL,
    balance             INT                  NOT NULL,
    change_reason       VARCHAR(30)          NOT NULL,
    created_at          DATETIME2            NOT NULL DEFAULT (SYSDATETIME()),
    CONSTRAINT PK_member_point_logs PRIMARY KEY CLUSTERED (log_id),
    CONSTRAINT FK_member_point_logs_members FOREIGN KEY (member_id) REFERENCES dbo.members(member_id),
    CONSTRAINT CK_member_point_logs_balance CHECK (balance >= 0) -- points_change 可為負，代表扣點
);
GO

-- =============================================================================
-- 7. 客服工單與即時聊天模組
-- =============================================================================

-- 客服問題分類表
CREATE TABLE dbo.customer_service_categories (
    category_id         INT IDENTITY(1,1) NOT NULL,
    category_name       NVARCHAR(50)      NOT NULL,
    description         NVARCHAR(200)     NULL,
    CONSTRAINT PK_customer_service_categories PRIMARY KEY CLUSTERED (category_id)
);
GO

-- 客服分類：與前後端的「遊戲問題」判斷一致；保留既有分類編號。
INSERT INTO dbo.customer_service_categories(category_name)
SELECT v.name FROM (VALUES (N'帳號問題'),(N'遊戲問題'),(N'購買問題'),(N'其他問題')) v(name)
WHERE NOT EXISTS (
    SELECT 1 FROM dbo.customer_service_categories c
    WHERE LTRIM(RTRIM(c.category_name))=v.name
       OR (v.name=N'遊戲問題' AND LTRIM(RTRIM(c.category_name))=N'遊戲')
);
GO

-- 客服工單主表
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
GO

-- 客服工單訊息歷程表
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
GO

-- 即時客服聊天室表
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
GO

-- 僅限制客服聊天室：每位會員最多一個 OPEN，CLOSED 可有多個。
-- 不作用於好友聊天室 friend_rooms 或 friend_room_members。
CREATE UNIQUE INDEX UX_chat_rooms_one_open_member
    ON dbo.chat_rooms(member_id)
    WHERE status = 'OPEN';
GO

-- 即時客服聊天訊息表
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
GO

-- 案件編號流水號，對應 SupportService 的 NEXT VALUE FOR。
CREATE SEQUENCE dbo.customer_service_ticket_no_seq AS BIGINT
    START WITH 1 INCREMENT BY 1 NO CYCLE;
GO

-- 案件訊息圖片附件
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
GO

-- 客服案件與遊戲多對多關聯（遊戲多選）
-- customer_service_tickets.game_id 保留第一款遊戲，全部遊戲儲存於本表。
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
GO


-- =============================================================================
-- 9. 審核模組（接在客服模組後；2026-10-07 新增）
-- =============================================================================

-- 使用者內容檢舉紀錄。

CREATE TABLE dbo.ugc_reports (
    report_id           INT IDENTITY(1,1) NOT NULL,
    reporter_member_id  INT               NOT NULL,
    target_type         VARCHAR(20)       NOT NULL,
    target_id           INT               NOT NULL,
    snapshot_text       NVARCHAR(MAX)     NOT NULL,
    content_hash        VARCHAR(64)       NULL, -- 預留檢查來源版本，避免誤刪更新後內容
    reason              NVARCHAR(500)     NOT NULL,
    status              VARCHAR(20)       NOT NULL DEFAULT ('PENDING'),
    created_at          DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),
    admin_id            INT               NULL,
    reviewed_at         DATETIME2         NULL,
    review_comment      NVARCHAR(500)     NULL,
    CONSTRAINT PK_ugc_reports PRIMARY KEY (report_id),
    CONSTRAINT FK_ugc_reporter FOREIGN KEY (reporter_member_id) REFERENCES dbo.members(member_id),
    CONSTRAINT FK_ugc_admin FOREIGN KEY (admin_id) REFERENCES dbo.admin(admin_id),
    CONSTRAINT CK_ugc_type CHECK (target_type IN ('REVIEW','POST','AVATAR','NICKNAME')),
    CONSTRAINT CK_ugc_target CHECK (target_id > 0),
    CONSTRAINT CK_ugc_status CHECK (status IN ('PENDING','REMOVED','DISMISSED')),
    CONSTRAINT CK_ugc_review CHECK (
        (status = 'PENDING' AND admin_id IS NULL AND reviewed_at IS NULL)
        OR
        (status IN ('REMOVED','DISMISSED') AND admin_id IS NOT NULL
         AND reviewed_at IS NOT NULL AND reviewed_at >= created_at
         AND review_comment IS NOT NULL AND LEN(LTRIM(RTRIM(review_comment))) > 0)
    ),
    CONSTRAINT CK_ugc_reason CHECK (LEN(LTRIM(RTRIM(reason))) > 0),
    CONSTRAINT CK_ugc_snapshot CHECK (LEN(LTRIM(RTRIM(snapshot_text))) > 0),
    CONSTRAINT CK_ugc_hash CHECK (content_hash IS NULL OR LEN(content_hash) = 64)
);
GO

CREATE INDEX IX_ugc_status_time ON dbo.ugc_reports(status, created_at, report_id);
CREATE INDEX IX_ugc_target ON dbo.ugc_reports(target_type, target_id);
GO

GO

-- 客服補充：多遊戲案件、案件優先度、客服通知；以下均包含在本建庫檔內。
USE GamePlatformDB;
GO
-- 客服案件：一個案件關聯多款遊戲
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

-- 客服案件：優先度
-- Execute against existing GamePlatformDB; does not rebuild other tables.
USE GamePlatformDB;
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

-- 客服通知：目前僅供客服事件使用
-- 保存客服聊天室／案件通知；其他模組目前不串接。
USE GamePlatformDB;
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
        related_game_id INT NULL, -- 上架審核結果通知所關聯的遊戲 ID
        created_at DATETIME2 NOT NULL CONSTRAINT DF_notifications_created DEFAULT SYSDATETIME(),
        read_at DATETIME2 NULL,
        CONSTRAINT CK_notifications_recipient_type CHECK (recipient_type IN ('MEMBER','ADMIN'))
    );
    CREATE INDEX IX_notifications_inbox ON dbo.notifications(recipient_type,recipient_id,notification_id DESC) INCLUDE (read_at);
END
GO
-- recipient_type=MEMBER -> members.member_id；ADMIN -> admin.admin_id（客服及管理者）。
-- 客服通知事件：NEW_CHAT、TICKET_CREATED、CHAT_REPLY、TICKET_REPLY、CHAT_MESSAGE、TICKET_MESSAGE。
-- 多型 recipient_id 由後端驗證，無法建立單一 FK 指向兩張主表。

GO

-- =============================================================================
-- 10. ADMIN／STAFF 權限初始化與既有資料原地轉換（2026-10-19）
-- 已有資料庫：停止後端、備份，只選取本段到檔案結尾執行。
-- 不刪除帳號、不重設密碼、不重建資料表；舊 JWT 需重新登入。
-- 初次無最高帳號：先執行本段初始化角色，從註冊頁建立 PENDING，
-- 再把 @InitialAdminAccount 填成該帳號並重跑本段。已有 ADMIN 時不用填。
-- =============================================================================
USE [GamePlatformDB];
GO
SET NOCOUNT ON;
SET XACT_ABORT ON;
DECLARE @InitialAdminAccount VARCHAR(50) = NULL;
BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @LockResult INT;
    EXEC @LockResult=sys.sp_getapplock @Resource='HERO_STAFF_ADMIN',
        @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
    IF @LockResult < 0 THROW 51000,N'人員管理忙碌，請稍後重試。',1;
    IF OBJECT_ID(N'dbo.admin',N'U') IS NULL OR OBJECT_ID(N'dbo.admin_roles',N'U') IS NULL
       OR OBJECT_ID(N'dbo.admin_permissions',N'U') IS NULL
       OR OBJECT_ID(N'dbo.admin_user_permissions',N'U') IS NULL
       OR OBJECT_ID(N'dbo.admin_role_permissions',N'U') IS NULL
        THROW 51001,N'缺少管理員資料表；全新資料庫請先執行本檔前面的建庫段落。',1;

    -- 初始化目前角色模型；依 role_code 補齊角色，不轉換舊角色。
    INSERT dbo.admin_roles(role_code,role_name,description)
    SELECT v.code,v.name,v.description FROM (VALUES
      ('ADMIN',N'最高管理員',N'完整功能，唯一可管理人員角色與 STAFF 授權的角色'),
      ('STAFF',N'Staff 人員',N'只依 admin_user_permissions 使用業務功能，不可轉授'),
      ('REVIEWER',N'審核人員',N'固定遊戲審核、發行商審核、會員管理及評論審核'),
      ('SUPPORT',N'客服人員',N'固定客服工作總覽、聊天室與案件'),
      ('PENDING',N'待授權',N'尚未授權，不可登入後台')
    ) v(code,name,description)
    WHERE NOT EXISTS(SELECT 1 FROM dbo.admin_roles r WHERE r.role_code=v.code);
    UPDATE r SET role_name=v.name,description=v.description
    FROM dbo.admin_roles r JOIN (VALUES
      ('ADMIN',N'最高管理員',N'完整功能，唯一可管理人員角色與 STAFF 授權的角色'),
      ('STAFF',N'Staff 人員',N'只依 admin_user_permissions 使用業務功能，不可轉授'),
      ('REVIEWER',N'審核人員',N'固定遊戲審核、發行商審核、會員管理及評論審核'),
      ('SUPPORT',N'客服人員',N'固定客服工作總覽、聊天室與案件'),
      ('PENDING',N'待授權',N'尚未授權，不可登入後台')
    ) v(code,name,description) ON r.role_code=v.code;

    -- 權限代碼轉換。新舊代碼同時存在時，合併關聯；不移除任何有效業務授權。
    DECLARE @Aliases TABLE(old_code VARCHAR(100),new_code VARCHAR(100));
    INSERT @Aliases VALUES ('MEMBER_MANAGE','MEMBER_SUSPEND'),('SUPPORT_CASES','TICKET_HANDLE');
    DECLARE @OldCode VARCHAR(100),@NewCode VARCHAR(100),@OldId INT,@NewId INT;
    DECLARE aliases CURSOR LOCAL FAST_FORWARD FOR SELECT old_code,new_code FROM @Aliases;
    OPEN aliases;
    FETCH NEXT FROM aliases INTO @OldCode,@NewCode;
    WHILE @@FETCH_STATUS=0
    BEGIN
        SET @OldId=NULL;SET @NewId=NULL;
        SELECT @OldId=permission_id FROM dbo.admin_permissions WHERE permission_code=@OldCode;
        SELECT @NewId=permission_id FROM dbo.admin_permissions WHERE permission_code=@NewCode;
        IF @OldId IS NOT NULL AND @NewId IS NULL
            UPDATE dbo.admin_permissions SET permission_code=@NewCode WHERE permission_id=@OldId;
        ELSE IF @OldId IS NOT NULL AND @NewId IS NOT NULL
        BEGIN
            INSERT dbo.admin_user_permissions(admin_id,permission_id,granted_by_admin_id,granted_at)
            SELECT u.admin_id,@NewId,u.granted_by_admin_id,u.granted_at FROM dbo.admin_user_permissions u
            WHERE u.permission_id=@OldId AND NOT EXISTS(
              SELECT 1 FROM dbo.admin_user_permissions n WHERE n.admin_id=u.admin_id AND n.permission_id=@NewId);
            INSERT dbo.admin_role_permissions(admin_role_id,permission_id,created_by_admin_id,created_at)
            SELECT u.admin_role_id,@NewId,u.created_by_admin_id,u.created_at FROM dbo.admin_role_permissions u
            WHERE u.permission_id=@OldId AND NOT EXISTS(
              SELECT 1 FROM dbo.admin_role_permissions n WHERE n.admin_role_id=u.admin_role_id AND n.permission_id=@NewId);
            DELETE dbo.admin_user_permissions WHERE permission_id=@OldId;
            DELETE dbo.admin_role_permissions WHERE permission_id=@OldId;
            DELETE dbo.admin_permissions WHERE permission_id=@OldId;
        END;
        FETCH NEXT FROM aliases INTO @OldCode,@NewCode;
    END;
    CLOSE aliases;DEALLOCATE aliases;

    DECLARE @Catalog TABLE(code VARCHAR(100) PRIMARY KEY,name NVARCHAR(100),description NVARCHAR(255));
    INSERT @Catalog VALUES
      ('DASHBOARD_VIEW',N'平台主頁',N'查看平台統計'),
      ('STAFF_MANAGE',N'後台人員管理',N'僅 ADMIN 使用，不可授予 STAFF'),
      ('MEMBER_SUSPEND',N'會員管理',N'查詢會員、停權與復權'),
      ('PUBLISHER_REVIEW',N'發行商管理與審核',N'查詢申請、審核及歷史'),
      ('ORDER_VIEW',N'訂單列表',N'查詢訂單與明細'),
      ('REFUND_MANAGE',N'退款管理',N'處理退款'),
      ('GAME_REVIEW',N'遊戲管理與審核',N'查詢、審核遊戲與歷史'),
      ('TAG_MANAGE',N'平台標籤維護',N'維護遊戲標籤'),
      ('TICKET_HANDLE',N'客服管理',N'工作總覽、聊天室與案件'),
      ('COMMENT_REVIEW',N'評論審核',N'查詢與處理評論'),
      ('PROMOTION_MANAGE',N'優惠活動列表',N'查看發行商遊戲折扣、活動時間與冷卻狀態'),
      ('COUPON_MANAGE',N'優惠券管理',N'管理優惠券'),
      ('PERMISSION_MANAGE',N'管理員權限管理',N'僅 ADMIN 使用，不可授予 STAFF'),
      ('OPERATION_LOG_VIEW',N'操作紀錄',N'查看人員操作紀錄'),
      ('LOGIN_LOG_VIEW',N'登入紀錄',N'查看登入紀錄');
    INSERT dbo.admin_permissions(permission_code,permission_name,description)
    SELECT c.code,c.name,c.description FROM @Catalog c
    WHERE NOT EXISTS(SELECT 1 FROM dbo.admin_permissions p WHERE p.permission_code=c.code);
    UPDATE p SET permission_name=c.name,description=c.description
    FROM dbo.admin_permissions p JOIN @Catalog c ON c.code=p.permission_code;

    -- 固定角色白名單；程式也依相同固定集合判斷，不將角色權限當成 STAFF 個別授權。
    DELETE rp FROM dbo.admin_role_permissions rp JOIN dbo.admin_roles r ON r.admin_role_id=rp.admin_role_id
    WHERE r.role_code IN ('REVIEWER','SUPPORT');
    INSERT dbo.admin_role_permissions(admin_role_id,permission_id)
    SELECT r.admin_role_id,p.permission_id FROM (VALUES
      ('REVIEWER','GAME_REVIEW'),('REVIEWER','PUBLISHER_REVIEW'),
      ('REVIEWER','MEMBER_SUSPEND'),('REVIEWER','COMMENT_REVIEW'),('SUPPORT','TICKET_HANDLE')
    ) v(role_code,permission_code)
    JOIN dbo.admin_roles r ON r.role_code=v.role_code
    JOIN dbo.admin_permissions p ON p.permission_code=v.permission_code;

    -- 保留舊 STAFF 的管理功能授權紀錄，但新版會忽略，不再允許轉授。
    -- 不為既有 STAFF 自動補回任何業務授權，撤銷結果保持不變。
    IF @InitialAdminAccount IS NOT NULL
    BEGIN
        IF EXISTS(SELECT 1 FROM dbo.admin a JOIN dbo.admin_roles r ON r.admin_role_id=a.admin_role_id
                  WHERE a.status='ACTIVE' AND r.role_code='ADMIN')
            THROW 51003,N'已有啟用中的 ADMIN；請清空 @InitialAdminAccount，由 ADMIN 在頁面管理人員。',1;
        IF NOT EXISTS(SELECT 1 FROM dbo.admin a JOIN dbo.admin_roles r ON r.admin_role_id=a.admin_role_id
                      WHERE a.admin_account=@InitialAdminAccount AND a.status='ACTIVE' AND r.role_code IN ('PENDING','STAFF'))
            THROW 51004,N'初始帳號必須是啟用中的 PENDING 或 STAFF，不可選擇審核或客服。',1;
        UPDATE dbo.admin SET admin_role_id=(SELECT admin_role_id FROM dbo.admin_roles WHERE role_code='ADMIN'),
            updated_at=SYSDATETIME() WHERE admin_account=@InitialAdminAccount;
    END;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
SELECT a.admin_id,a.admin_account,a.admin_name,a.status,r.role_code
FROM dbo.admin a JOIN dbo.admin_roles r ON r.admin_role_id=a.admin_role_id ORDER BY a.admin_id;
SELECT permission_code,permission_name FROM dbo.admin_permissions ORDER BY permission_code;
IF NOT EXISTS(SELECT 1 FROM dbo.admin a JOIN dbo.admin_roles r ON r.admin_role_id=a.admin_role_id
              WHERE a.status='ACTIVE' AND r.role_code='ADMIN')
    PRINT N'角色與權限已初始化；請繼續執行第 03 份示範資料 SQL 建立固定最高管理員。';
GO

-- =============================================================================
-- 11. 既有資料庫補齊舊版已有的遊戲／通知結構（2026-10-20）
-- 只處理兩個欄位與遊戲狀態；不變更人員角色、帳密、權限或訂單資料。
-- 全新建庫執行至此亦可重跑；與前方 CREATE TABLE 中的定義一致。
-- =============================================================================
USE GamePlatformDB;
GO
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF OBJECT_ID(N'dbo.games', N'U') IS NULL OR OBJECT_ID(N'dbo.notifications', N'U') IS NULL
        THROW 51011, N'缺少 games 或 notifications；請先確認目前資料庫及基礎資料表。', 1;
    IF COL_LENGTH(N'dbo.games', N'scheduled_off_shelf_at') IS NULL
        ALTER TABLE dbo.games ADD scheduled_off_shelf_at DATETIME2 NULL;
    IF COL_LENGTH(N'dbo.notifications', N'related_game_id') IS NULL
        ALTER TABLE dbo.notifications ADD related_game_id INT NULL;
    IF EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE parent_object_id=OBJECT_ID(N'dbo.games') AND name=N'CK_games_status')
        ALTER TABLE dbo.games DROP CONSTRAINT CK_games_status;
    ALTER TABLE dbo.games WITH CHECK ADD CONSTRAINT CK_games_status
        CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'COMING_SOON', 'PUBLISHED', 'PENDING_OFF_SHELF', 'OFF_SHELF'));
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO

-- =============================================================================
-- 12. 發行商遊戲折扣管理模組（2026-10-27 新增）
-- 既有資料庫升級：只新增 game_discounts，不重建或修改其他資料表。
-- 折扣百分比限定 1～99 的整數；每次折扣固定 7 天。
-- 折扣結束後 30 天內不可再次設定，由 Spring Boot 後端驗證。
-- 折扣後實際成交價由後端四捨五入至整數元，訂單保留成交金額。
-- 執行前請先備份 GamePlatformDB；此段可重複執行。
-- =============================================================================

USE [GamePlatformDB];
GO

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;
GO

-- 遊戲折扣紀錄表：保留歷史紀錄，供 30 天冷卻期判斷。
IF OBJECT_ID(N'dbo.game_discounts', N'U') IS NULL
BEGIN
    IF OBJECT_ID(N'dbo.games', N'U') IS NULL
        THROW 51021, N'缺少 games 資料表，請先建立遊戲主表。', 1;

    CREATE TABLE dbo.game_discounts (
        discount_id        INT IDENTITY(1,1) NOT NULL,
        game_id            INT               NOT NULL,
        discount_percent   INT               NOT NULL,
        start_at           DATETIME2         NOT NULL,
        end_at             DATETIME2         NOT NULL,
        created_at         DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),

        CONSTRAINT PK_game_discounts
            PRIMARY KEY CLUSTERED (discount_id),

        CONSTRAINT FK_game_discounts_game
            FOREIGN KEY (game_id) REFERENCES dbo.games(game_id),

        CONSTRAINT CK_game_discounts_percent
            CHECK (discount_percent BETWEEN 1 AND 99),

        CONSTRAINT CK_game_discounts_duration
            CHECK (end_at = DATEADD(DAY, 7, start_at))
    );
END;
GO

-- 依遊戲查詢最新折扣紀錄及冷卻期。
IF OBJECT_ID(N'dbo.game_discounts', N'U') IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM sys.indexes
       WHERE object_id = OBJECT_ID(N'dbo.game_discounts')
         AND name = N'IX_game_discounts_game_end'
   )
BEGIN
    CREATE NONCLUSTERED INDEX IX_game_discounts_game_end
        ON dbo.game_discounts (game_id, end_at DESC);
END;
GO

-- 檢查折扣資料表結構。
SELECT
    c.name AS column_name,
    TYPE_NAME(c.user_type_id) AS data_type,
    c.is_nullable
FROM sys.columns c
WHERE c.object_id = OBJECT_ID(N'dbo.game_discounts')
ORDER BY c.column_id;
GO


-- =============================================================================
-- 13. 完整建庫補齊：coupon-schema.sql
-- 與專題內後端初始化資源保持一致；已存在的結構會略過。
-- =============================================================================
USE [GamePlatformDB];
GO
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

 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;

GO

-- =============================================================================
-- 14. 完整建庫補齊：support-extension.sql
-- 與專題內後端初始化資源保持一致；已存在的結構會略過。
-- =============================================================================
USE [GamePlatformDB];
GO
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

GO

-- =============================================================================
-- 15. 完整建庫補齊：support-account-help.sql
-- 與專題內後端初始化資源保持一致；已存在的結構會略過。
-- =============================================================================
USE [GamePlatformDB];
GO
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

GO

-- =============================================================================
-- 16. 完整建庫補齊：review-moderation-schema.sql
-- 與專題內後端初始化資源保持一致；已存在的結構會略過。
-- =============================================================================
USE [GamePlatformDB];
GO
-- 評論管理增量更新：只建立缺少的資料表，可重跑，不補造歷史違規。
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @LockResult INT;
 EXEC @LockResult=sys.sp_getapplock @Resource='HERO_REVIEW_MODERATION_SCHEMA',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
 IF @LockResult < 0
 BEGIN
  DECLARE @Message NVARCHAR(2048)=CONCAT(N'評論管理初始化取鎖失敗，代碼：',@LockResult);
  THROW 51140,@Message,1;
 END;
 IF OBJECT_ID(N'dbo.members',N'U') IS NULL OR OBJECT_ID(N'dbo.game_reviews',N'U') IS NULL OR OBJECT_ID(N'dbo.admin',N'U') IS NULL
  THROW 51141,N'請先建立會員、評論與管理員基礎資料表。',1;
 IF OBJECT_ID(N'dbo.review_violations',N'U') IS NULL
 BEGIN
  CREATE TABLE dbo.review_violations (
   violation_id BIGINT IDENTITY(1,1) PRIMARY KEY,
   review_id INT NOT NULL UNIQUE REFERENCES dbo.game_reviews(review_id),
   member_id INT NOT NULL REFERENCES dbo.members(member_id),
   report_id INT NULL,
   admin_id INT NOT NULL REFERENCES dbo.admin(admin_id),
   reason NVARCHAR(500) NOT NULL,
   confirmed_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
   reversed_at DATETIME2 NULL,
   reversed_by_admin_id INT NULL REFERENCES dbo.admin(admin_id)
  );
  CREATE INDEX IX_review_violations_member_time ON dbo.review_violations(member_id,confirmed_at) INCLUDE(reversed_at);
 END;
 IF OBJECT_ID(N'dbo.member_review_mutes',N'U') IS NULL
 BEGIN
  CREATE TABLE dbo.member_review_mutes (
   member_id INT PRIMARY KEY REFERENCES dbo.members(member_id),
   muted_until DATETIME2 NOT NULL,
   reason NVARCHAR(500) NOT NULL,
   updated_at DATETIME2 NOT NULL DEFAULT SYSDATETIME()
  );
 END;
 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;

GO

-- 建庫完成確認：列出所有資料表，並驗證整合功能的必要結構。
IF OBJECT_ID(N'dbo.game_discounts',N'U') IS NULL
 OR OBJECT_ID(N'dbo.member_coupon_claims',N'U') IS NULL
 OR OBJECT_ID(N'dbo.support_faqs',N'U') IS NULL
 OR OBJECT_ID(N'dbo.member_review_mutes',N'U') IS NULL
 THROW 51201,N'必要整合資料表尚未建立，請檢查前面的錯誤。',1;
SELECT s.name AS schema_name,t.name AS table_name FROM sys.tables t
JOIN sys.schemas s ON s.schema_id=t.schema_id ORDER BY s.name,t.name;
PRINT N'GamePlatformDB 完整建庫完成。';
GO

-- 18. 退款申請與付款重試交易結構
USE [GamePlatformDB];
GO
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
            ticket_id INT NULL REFERENCES dbo.customer_service_tickets(ticket_id),
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
    -- 案件關聯為選填；多筆一般退款可沒有案件，已關聯案件則不可重複。
    CREATE UNIQUE INDEX UX_refund_ticket_not_null ON dbo.refund_requests(ticket_id) WHERE ticket_id IS NOT NULL;
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

GO
SELECT COUNT(*) AS table_count FROM sys.tables;
PRINT N'完整建庫完成；接著依序執行 02 及 03。';
GO

-- 常見問題擴充：與後端初始化使用同一個執行標記。
USE [GamePlatformDB];
GO
-- 客服 FAQ 擴充：只初始化一次，保留後台編輯、封存及刪除結果；不新增資料表。
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @FaqLock INT;
 EXEC @FaqLock=sys.sp_getapplock @Resource='HERO_SUPPORT_FAQ_CATALOG',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
 IF @FaqLock<0
 BEGIN
  DECLARE @FaqLockMessage NVARCHAR(2048)=CONCAT(N'常見問題初始化取鎖失敗，sp_getapplock=',@FaqLock,N'。');
  THROW 51064,@FaqLockMessage,1;
 END;
 IF OBJECT_ID(N'dbo.support_faqs',N'U') IS NULL OR OBJECT_ID(N'dbo.customer_service_categories',N'U') IS NULL
  THROW 51065,N'缺少 FAQ 或客服分類表，請先初始化客服結構。',1;
 IF NOT EXISTS(SELECT 1 FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.support_faqs') AND minor_id=0 AND name=N'HERO_FAQ_CATALOG_20261028')
 BEGIN
  DECLARE @FaqCatalog TABLE(category_name NVARCHAR(100),question NVARCHAR(200),answer NVARCHAR(MAX),sort_order INT);
  INSERT @FaqCatalog(category_name,question,answer,sort_order) VALUES
  (N'遊戲問題',N'怎麼更新已下載的 ZIP 遊戲檔案？',N'1. 登入後開啟「遊戲庫」，找到遊戲並確認「下載版本」顯示的版本號。
2. 關閉正在執行的舊版遊戲，先備份舊 ZIP 與遊戲存檔。
3. 重新下載最新版 ZIP，解壓縮到新的資料夾，不要直接覆蓋存檔或混用新舊檔案。
4. 依版本說明啟動新版，例如開啟 index.html、遊戲名稱.html 或其他啟動檔。
5. 確認新版正常後再整理舊檔。存檔能否移轉依各遊戲支援情況而定；需要額外啟動環境時，請依該版本說明操作。',100),
  (N'遊戲問題',N'下載 ZIP 後要怎麼開始遊玩？',N'先將 ZIP 完整解壓縮，再依遊戲內附說明啟動。HTML 遊戲通常會提供 index.html 或遊戲名稱.html；其他版本請使用對應啟動檔。不要直接在壓縮檔預覽視窗中執行。若版本要求本機伺服器或其他執行環境，請依說明設定。',110),
  (N'遊戲問題',N'更新 ZIP 後，線上遊玩也會一起更新嗎？',N'下載版與網站的線上遊玩內容可能分開更新。下載新的 ZIP 不會自動替換網站中的遊戲。可在遊戲庫確認可下載的版本；線上內容以平台目前提供的版本為準。',120),
  (N'遊戲問題',N'更新或換資料夾後，原本的遊戲存檔還在嗎？',N'先保留舊版資料夾與存檔備份。下載版的存檔位置及相容性由各遊戲決定，不能保證直接覆蓋就會保留。網站線上遊玩的進度保存在目前瀏覽器，和下載版的存檔不一定共用；請確認新版正常再移除舊檔。',130),
  (N'遊戲問題',N'遊戲庫沒有下載按鈕，或看不到新版怎麼辦？',N'先確認登入的是購買遊戲的帳號，並重新載入遊戲庫。只有平台已提供的版本或發行商已發布的下載檔才會出現下載入口；尚未發布的版本不會顯示。若訂單已付款仍沒有遊戲或下載入口，請附上訂單編號聯絡客服。',140),
  (N'遊戲問題',N'ZIP 下載失敗或解壓縮時顯示損壞怎麼辦？',N'刪除未完成的下載檔後，重新登入並下載一次。確認裝置有足夠空間，且網路連線穩定。若仍無法解壓縮，請記錄遊戲名稱、版本號與錯誤訊息，再聯絡客服；不要自行改動 ZIP 副檔名。',150),
  (N'遊戲問題',N'線上遊戲不能啟動或畫面空白怎麼辦？',N'先重新整理頁面，確認訂單已付款、遊戲仍在有效遊戲庫中。可嘗試關閉影響頁面的瀏覽器擴充功能，或換一個瀏覽器再測試。若仍有問題，請提供遊戲名稱、瀏覽器版本及錯誤畫面，由客服協助核對。',160),
  (N'遊戲問題',N'線上遊戲存檔可以換電腦或瀏覽器繼續用嗎？',N'目前線上遊戲進度主要保存在使用中的瀏覽器，依會員與遊戲分開保存，沒有自動跨裝置同步。更換瀏覽器、使用無痕模式或清除網站資料，可能無法讀到原進度。若遊戲本身提供匯出存檔功能，可先依遊戲說明備份。',170),
  (N'遊戲問題',N'遊玩時數怎麼計算，為什麼沒有立即增加？',N'線上遊玩會在遊戲分頁可見時累計時間，再以分鐘同步到遊戲庫；切到其他分頁時會暫停計時。短時間遊玩或同步尚未完成時，不一定立即顯示增加。請稍後重新載入遊戲庫；下載版離線遊玩不會自動同步網站時數。',180),
  (N'遊戲問題',N'要怎麼切換全螢幕遊玩？',N'開啟網站的遊玩頁後，使用「全螢幕」按鈕。瀏覽器支援時會切換顯示，可按 Esc 或畫面上的結束全螢幕按鈕離開。若裝置或瀏覽器不支援，仍可在一般畫面遊玩。',190),
  (N'訂單問題',N'已付款的遊戲在哪裡查看？',N'登入購買時使用的帳號，從頂欄進入「遊戲庫」。可搜尋名稱、查看遊玩時數，並使用平台提供的線上遊玩或下載入口。若訂單已付款卻沒有遊戲，請從「我的訂單」確認付款狀態，再聯絡客服核對。',200),
  (N'訂單問題',N'我的訂單顯示待付款，要怎麼繼續付款？',N'開啟會員下拉選單的「我的訂單」，找到待付款訂單，按「前往付款」。系統會另開綠界付款分頁，請依付款頁完成操作。避免同時開多個付款頁或重複繳款；若已付款但尚未更新，先聯絡客服核對。',210),
  (N'訂單問題',N'要怎麼取消尚未付款的訂單？',N'在「我的訂單」展開待付款訂單，按「取消訂單」並確認。取消後不要再使用原本的繳費資訊付款；如需購買，請重新加入購物車。若你已完成付款但狀態尚未更新，請先聯絡客服，不要直接當成未付款處理。',220),
  (N'付款問題',N'按前往付款沒有跳出綠界頁面怎麼辦？',N'付款頁會另開分頁。請確認瀏覽器允許本站開啟彈出式視窗或新分頁，再重新操作。若已產生待付款訂單，請從「我的訂單」按「前往付款」，不要為同一筆需求連續重複建立訂單。',230),
  (N'付款問題',N'付款完成後，訂單還是待付款怎麼辦？',N'先回到「我的訂單」重新整理並稍候核對付款結果，避免再次付款。若仍未更新，請保留訂單編號、付款時間及交易紀錄，從該筆訂單的「聯絡客服」入口請客服查核。',240),
  (N'付款問題',N'ATM 或超商付款的期限在哪裡看？',N'繳費期限以綠界付款頁、通知及訂單明細顯示的資訊為準。請勿使用已取消、已失效或不確定狀態的繳費資訊；超過期限或付款狀態有疑問時，先到我的訂單確認，再請客服協助。',250),
  (N'付款問題',N'重複付款或收到額外付款待核對提示怎麼辦？',N'先停止再次付款，保留每筆交易的時間、金額及交易編號。從對應訂單聯絡客服，說明可能的重複付款情況。客服會協助核對，是否退款及後續處理由有退款權限的人員確認。',260),
  (N'退款問題',N'如何提出遊戲退款申請？',N'登入後到「我的訂單」，展開符合申請條件的已付款訂單，按「申請退款」並填寫原因。系統目前提供付款後 14 天內的申請入口；送出後會直接進入後台退款管理，不會每筆自動建立客服案件。送出申請不代表退款一定通過。',270),
  (N'退款問題',N'退款申請結果與拒絕原因在哪裡看？',N'在「我的訂單」展開原訂單，可查看退款處理中、已退款或申請未通過等狀態，以及處理說明和處理時間。系統也會提供退款通知。若需要補充資料，會另外建立補件案件並通知你。',280),
  (N'退款問題',N'客服請我補充退款資料，要到哪裡回覆？',N'開啟退款補件通知，或從客服中心進入「我的案件」，找到對應案件並回覆客服要求的資料，例如遊戲錯誤畫面及操作步驟。補件案件用來收集資料；退款決定仍由退款管理人員處理，不必重複提出新的退款申請。',290),
  (N'退款問題',N'退款完成後還能玩遊戲或使用優惠券嗎？',N'整筆訂單退款完成後，該筆訂單取得的遊戲權益會被撤銷；其他訂單取得的權益不會因此一併取消。優惠券使用次數會依處理結果釋放，但再次使用仍需符合有效期限、適用遊戲與額度。測試退款會特別標示，不代表真實金流退刷。',300),
  (N'優惠券問題',N'優惠券在哪裡領取？每個人都能領嗎？',N'登入後從右上角會員下拉選單進入「優惠券」。開放領取的優惠券會先出現在「未領取」，領取後移到「已領取」。每位會員對同一張優惠券只能領取一次，能否使用仍依活動期間、適用遊戲及使用規則判斷。',310),
  (N'優惠券問題',N'已領取的優惠券為什麼不能使用？',N'請確認優惠券仍在有效期間、購物車有符合發行商或遊戲類型的商品，且達到最低消費等條件。已達使用上限或適用範圍不符時無法使用；實際折抵金額以購物車與結帳頁計算為準。',320),
  (N'優惠券問題',N'優惠券標示的發行商、遊戲類型與低消是什麼？',N'發行商規則代表僅適用指定發行商旗下的遊戲；類型規則代表僅適用指定標籤／類型的遊戲。低消是使用優惠券需要達到的消費門檻，符合範圍的商品及折抵方式以該券說明與結帳結果為準。',330),
  (N'優惠券問題',N'還沒付款，想改用另一張優惠券怎麼辦？',N'已建立的訂單會保留建立時的價格及優惠內容，不能直接改成另一張券。若確定尚未付款，可先取消原待付款訂單，回到購物車選擇優惠券，再重新結帳。已拿到繳費代碼時不要再繳舊訂單；已付款則先聯絡客服核對。',340),
  (N'帳號與登入',N'忘記密碼或無法登入時，還能找客服嗎？',N'登入頁的「登入遇到問題？」可前往公開常見問題，使用忘記密碼、重寄驗證信及已開放的客服信箱。線上聊天室需要登入後使用。請勿提供密碼、驗證碼或其他登入憑證給客服。',350),
  (N'帳號與登入',N'沒有收到信箱驗證信怎麼辦？',N'確認註冊信箱是否正確，並查看垃圾郵件或其他分類收件匣。可從驗證信頁面重新寄送，避免短時間連續操作。若一直無法收到，請使用客服中心提供的帳號協助入口或已開放的客服信箱。',360),
  (N'帳號與登入',N'一般帳號登入後可以綁定 Google 嗎？',N'可以。先用原本的會員帳號登入，到個人資料的 Google 綁定區連接 Google 帳號。綁定前請確認選擇的是你自己的 Google 帳號；已綁定其他會員的 Google 帳號不能直接重複綁定。',370),
  (N'帳號與登入',N'Google 登入後找不到原本購買的遊戲怎麼辦？',N'先確認是否登入了另一個會員帳號。原帳號購買紀錄不會因為使用不同 Google 帳號登入就自動合併。請使用原本購買時的帳號登入，必要時再進行 Google 綁定；資料仍不一致時請附訂單編號聯絡客服。',380),
  (N'網站操作異常',N'找不到客服回覆或我的案件在哪裡？',N'從頂欄「客服中心」下拉選單進入「我的案件」，查看案件狀態及客服回覆。收到補件或案件通知時也可直接點擊通知。案件需要登入後才能查看，並且只能存取自己的資料。',390),
  (N'網站操作異常',N'線上客服顯示重新連線，我送的訊息會不見嗎？',N'先確認網路狀態，等候重新連線後再查看對話紀錄。送出時若出現失敗或沒有出現在對話中，請確認紀錄後再重送，避免重複留言。你也可以從我的案件追蹤已轉成案件的問題。',400),
  (N'網站操作異常',N'網頁按鈕沒反應或畫面顯示異常怎麼辦？',N'先重新整理頁面，確認網路正常並重新登入。可嘗試使用其他瀏覽器，或暫時關閉影響頁面的擴充功能。聯絡客服時請提供操作步驟、發生時間及截圖，避免直接清除所有網站資料，以免影響保存在瀏覽器的遊戲進度。',410),
  (N'其他問題',N'聯絡客服時需要提供什麼資料？',N'請說明問題類型、發生時間、操作步驟與看到的錯誤訊息。訂單問題可附訂單編號；遊戲問題可附遊戲名稱、版本及瀏覽器資訊。客服可能請你補充截圖，但不需要你的密碼、驗證碼或完整信用卡資料。',420),
  (N'其他問題',N'客服案件已結案，還可以繼續回覆嗎？',N'結案後可保留查看對話紀錄，但不能在已結案案件繼續新增回覆。若還有新的問題，請另行聯絡線上客服，說明原案件編號，讓客服核對後續處理。',430);
  INSERT dbo.customer_service_categories(category_name,description)
  SELECT DISTINCT v.category_name,N'常見問題與客服案件分類' FROM @FaqCatalog v
  WHERE NOT EXISTS(SELECT 1 FROM dbo.customer_service_categories c WHERE c.category_name=v.category_name);
  INSERT dbo.support_faqs(category_id,question,answer,status,sort_order,published_at)
  SELECT (SELECT MIN(c.category_id) FROM dbo.customer_service_categories c WHERE c.category_name=v.category_name),v.question,v.answer,'PUBLISHED',v.sort_order,SYSDATETIME()
  FROM @FaqCatalog v WHERE NOT EXISTS(SELECT 1 FROM dbo.support_faqs f WHERE f.question=v.question);
  EXEC sys.sp_addextendedproperty @name=N'HERO_FAQ_CATALOG_20261028',@value=N'1',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'support_faqs';
 END;
 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;

GO
