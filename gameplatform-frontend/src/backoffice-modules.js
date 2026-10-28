const col = (key, label) => ({ key, label })
export const backofficeModules = {
    orders: {
        title: '訂單管理',
        permission: 'ORDER_VIEW',
        icon: 'cart',
        note: '查詢平台訂單與下單當下的遊戲、金額和贈禮明細。',
        id: 'order_id',
        states: [
            'PENDING',
            'PAID',
            'CANCELLED',
            'REFUNDED',
            'FAILED',
            'EXPIRED'
        ],
        columns: [
            col('order_no', '訂單編號'),
            col('username', '會員'),
            col('order_status', '狀態'),
            col('total_amount', '金額'),
            col('created_at', '下單時間')
        ]
    },
    refunds: {
        title: '退款訂單查詢',
        permission: 'REFUND_MANAGE',
        icon: 'cart',
        note: '僅查詢已標記退款的訂單。本頁不執行金流退款，也不修改付款狀態。',
        id: 'order_id',
        columns: [
            col('order_no', '訂單編號'),
            col('username', '會員'),
            col('total_amount', '金額'),
            col('order_status', '狀態'),
            col('paid_at', '付款時間')
        ]
    },
    reviews: {
        title: '評論審核',
        permission: 'COMMENT_REVIEW',
        icon: 'chat',
        note: '查看玩家評論，填寫處理原因後隱藏或恢復。會員自行刪除的評論不可恢復。', id: 'review_id',
        states: [
            'NORMAL',
            'HIDDEN',
            'DELETED'
        ],
        columns: [
            col('game_name', '遊戲'),
            col('username', '會員'),
            col('rating', '評分'),
            col('content', '評論'),
            col('status', '狀態')
        ]
    },
    'review-reports': {
        title: '評論檢舉處理',
        permission: 'COMMENT_REVIEW',
        icon: 'chat',
        note: '核對檢舉快照與目前評論；內容已變更時請先重新確認，避免誤刪。',
        id: 'report_id',
        states: [
            'PENDING',
            'REMOVED',
            'DISMISSED'
        ],
        columns: [
            col('report_id', '檢舉編號'),
            col('target_id', '評論編號'),
            col('username', '檢舉會員'),
            col('reason', '檢舉原因'),
            col('status', '狀態')
        ]
    },
    tags: {
        title: '平台標籤維護',
        permission: 'TAG_MANAGE',
        icon: 'tag',
        note: '新增與編輯遊戲標籤。已被使用的標籤不可刪除。',
        id: 'tag_id',
        columns: [
            col('tag_id', '標籤編號'),
            col('tag_name', '名稱'),
            col('game_count', '使用遊戲數')
        ]
    },
    coupons: {
        title: '優惠券管理',
        permission: 'COUPON_MANAGE',
        icon: 'tag',
        note: '設定券名、折抵金額或折數、低消、起訖時間及適用發行商／遊戲類型。',
        id: 'coupon_id',
        states: [
            'ACTIVE',
            'INACTIVE',
            'EXPIRED'
        ],
        columns: [
            col('coupon_name', '名稱'),
            col('discount_summary', '優惠內容與低消'),
            col('scope_summary', '使用規則'),
            col('start_at', '開始時間'),
            col('used_count', '已使用次數'),
            col('status', '狀態'),
            col('expires_at', '到期時間')
        ]
    },
    'login-logs': {
        title: '會員登入紀錄',
        permission: 'LOGIN_LOG_VIEW',
        icon: 'file',
        note: '查看會員密碼及 Google 登入的成功、失敗與鎖定紀錄；不包含後臺人員登入。',
        id: 'login_log_id',
        states: ['SUCCESS', 'FAILED', 'LOCKED'],
        columns: [
            col('login_log_id', '紀錄編號'),
            col('username', '會員'),
            col('ip_address', '連線 IP'),
            col('login_status', '結果'),
            col('login_at', '時間')
        ]
    },
    operations: {
        title: '後臺操作紀錄',
        permission: 'OPERATION_LOG_VIEW',
        icon: 'file',
        note: '彙整後台操作、遊戲審核、發行商審核與客服操作紀錄。',
        id: 'operation_id',
        columns: [
            col('operation_id', '紀錄編號'),
            col('source_name', '來源'),
            col('admin_account', '操作人員'),
            col('action_code', '操作'),
            col('target_id', '對象編號'),
            col('detail', '原因／說明'),
            col('created_at', '時間')
        ]
    }
}

export const managementLabels = {
    PENDING: '待處理',
    PAID: '已付款',
    CANCELLED: '已取消',
    REFUNDED: '已退款',
    FAILED: '失敗',
    EXPIRED: '已到期',
    NORMAL: '正常',
    HIDDEN: '已軟刪除',
    DELETED: '會員已刪除',
    REMOVED: '已軟刪除',
    DISMISSED: '不成立',
    ACTIVE: '啟用',
    INACTIVE: '停用',
    SUCCESS: '成功',
    LOCKED: '鎖定',
    RATE: '折扣率',
    AMOUNT: '折抵金額',
    TAG_CREATE: '新增標籤',
    TAG_UPDATE: '修改標籤',
    TAG_DELETE: '刪除標籤',
    COUPON_CREATE: '新增優惠券',
    COUPON_UPDATE: '修改優惠券',
    REVIEW_MUTE: '評論累犯禁言',
    REVIEW_UNMUTE: '撤銷評論禁言',
    MEMBER_LOGIN_SUCCESS: '會員登入成功',
    MEMBER_LOGIN_FAILED: '會員登入失敗',
    MEMBER_LOGIN_LOCKED: '會員登入受限',
    REVIEW_NORMAL: '恢復評論',
    REVIEW_HIDDEN: '軟刪除評論',
    REPORT_REMOVE: '檢舉成立／軟刪除',
    REPORT_DISMISS: '檢舉不成立',
    PUBLISHER_APPROVED: '核准發行商申請',
    PUBLISHER_REJECTED: '退回發行商申請',
    SUPPORT_ROOM_CREATED: '建立客服聊天室',
    SUPPORT_ROOM_CLAIMED: '接手聊天室',
    SUPPORT_ROOM_CLOSED: '結束聊天室',
    SUPPORT_TICKET_CREATED_FROM_ROOM: '聊天室轉案件',
    SUPPORT_TICKET_CLAIMED: '接手客服案件',
    SUPPORT_TICKET_CLOSED: '案件結案',
    SUPPORT_ORDER_LINKED: '關聯訂單',
    SUPPORT_ORDER_UNLINKED: '解除訂單關聯',
    SUPPORT_COUPON_LINKED: '關聯優惠券',
    SUPPORT_COUPON_UNLINKED: '解除優惠券關聯',
    SUPPORT_NOTE_ADDED: '新增內部備註',
    GAME_PUBLISH_APPROVED: '核准遊戲上架',
    GAME_PUBLISH_REJECTED: '退回遊戲上架',
    GAME_OFF_SHELF_APPROVED: '遊戲下架',
    GAME_OFF_SHELF_REJECTED: '退回下架申請'
}
