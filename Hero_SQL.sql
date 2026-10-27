-- 全新 GamePlatformDB 完整建庫（2026-10-27）。
-- 已有資料庫請使用 Support_Update_20261027.sql，不要執行本檔的 CREATE DATABASE。
-- 包含基礎資料表、原第 10／11 段、優惠券、客服擴充及八種案件分類。
-- 使用 SSMS／sqlcmd 執行（含 GO）；不會自動匯入 Test_Data.sql 的示範資料。
-- 下方第 10 段保留目前 ADMIN 權限初始化；既有資料庫的欄位整合請看第 11 段。
-- 權限模型：ADMIN 最高、STAFF 個別授權、REVIEWER/SUPPORT 固定角色。

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
-- 紀錄 Super Admin 分配給每一位普通管理員的個別功能權限
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

    -- 使用原角色主鍵改名，帳號、既有授權與歷史外鍵不變；成功後可重複執行。
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
      ('PROMOTION_MANAGE',N'優惠活動管理',N'管理優惠活動'),
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
    PRINT N'角色與權限已初始化；尚無 ADMIN。請註冊 PENDING 後設定 @InitialAdminAccount，再執行第 10 段。';
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
