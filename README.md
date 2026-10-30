ngrok部屬後的網址
平台端
https://possibly-gulf-straining.ngrok-free.dev/?utm_source=chatgpt.com#/member/login

系統端
https://possibly-gulf-straining.ngrok-free.dev/?utm_source=chatgpt.com#/admin


## 本次更新功能與頁面

| 模組／頁面 | 內容 |
| --- | --- |
| 後台共用樣式 | 加深文字與表頭、加強白色卡片邊框和陰影，放大按鈕及側欄字體。 |
| 優惠活動 `/admin/promotions` | 顯示發行商、遊戲折扣、折扣價格、起訖時間及「尚未開始／進行中／冷卻中／已結束」狀態；優惠券仍獨立管理。 |
| 評論與檢舉 `/admin/comments` | 整合待處理檢舉、全部檢舉、全部評論；可查看未被檢舉的評論，保留軟刪除、恢復與累犯禁言規則。 |
| 退款申請管理 | 會員申請直接進退款列表，由 `REFUND_MANAGE` 人員查閱訂單、遊玩時數、原因並核准或拒絕；需要補件／有疑問才建立客服案件。 |
| 客服退款訊息 | 修正 `customer_service_messages` 的寫入欄位：`sender_admin_id` → `admin_id`。 |
| 會員訂單與付款 | 保留「已退款」分頁、待付款續付及綠界新分頁付款。 |

## SQL：依序執行

**全新建庫**須先在 SSMS 啟用「查詢 → SQLCMD 模式」：

1. `SQL/01_Hero_Create_Database.sql`：建立 `GamePlatformDB`（61 張表），包含角色／權限、優惠券、客服、評論、折扣、退款及付款重試結構。若同名資料庫已存在，會停止而不自動刪除。
2. `SQL/02_Hero_Cart_Minigames.sql`：建立 `demo_studio` 和六款小遊戲（五款上架、一款草稿）。若前端不是 `http://127.0.0.1:5173`，先修改 `@FrontendBase`。
3. `SQL/03_Hero_Demo_Data.sql`：建立測試角色與會員、訂單、遊玩時數、評論／檢舉、優惠券、四種折扣狀態、退款及標示 `[DEMO]` 的操作紀錄。

## 示範帳號

本包新建帳號的密碼：`HeroDemo2026!`（**僅供本機測試，不可用於正式環境**）。

| 帳號 | 用途 |
| --- | --- |
| `hero_admin` | 唯一最高管理員、完整管理權限 |
| `demo_staff` | 營運、客服、退款、評論、優惠與紀錄查詢 |
| `demo_reviewer` | 遊戲／發行商審核、會員與評論管理 |
| `demo_support` | 客服接手與回覆，不具有退款決策權 |
| `demo_studio` | 六款示範小遊戲的發行商 |
| `demo_aurora` | 有遊戲庫、評論與遊玩時數的會員 |
| `demo_lin` | 有已退款訂單的會員 |
| `demo_miko` | 有待處理退款申請的會員 |
| `demo_kai` | 有兩則待處理廣告檢舉的會員 |
| `demo_neo` | 有兩則確認違規，示範禁言六小時的會員 |
| `demo_sora` | 有待付款訂單，可測試新分頁續付的會員 |

五款已上架遊戲有示範購買及時數。待付款和已退款遊戲不計入有效遊戲權益；草稿遊戲沒有購買、評論或遊玩紀錄。示範登入、操作與付款紀錄不代表實際發信或綠界交易。

## 火柴人格鬥完整流程測試

1. `demo_studio` 開啟草稿「火柴人格鬥：霓虹對決」，確認版本、封面、宣傳素材並送審。預設發售日為建立資料兩天後；不足 24 小時先調整。
2. `hero_admin` 或 `demo_reviewer` 核准後，遊戲先處於「即將發售」，不可購買。
3. 若要加速，核准後使用 `02` SQL 末尾註解的專用時間加速語句，等待每分鐘上架排程；不會跳過審核。
4. 一般會員購買後，於遊戲庫測試線上遊玩、全螢幕與遊玩時數。
5. 發行商申請下架，可測試 24 小時內取消及期限到期後下架。重新送審需重新設定未來發售日期。

ZIP 位於 `gameplatform-frontend/public/minigames/lifecycle-demo.zip`，版本管理可填 `http://127.0.0.1:5173/minigames/lifecycle-demo.zip`（按實際連接埠修改）。ZIP 解壓後須透過 HTTP 靜態伺服器開啟 `index.html`；亦可檢查 `/minigames/lifecycle-build/index.html`。
