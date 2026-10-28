# HERO 完整建庫、示範資料及後台更新

以 Project_ispan(3).zip 為基礎，包含前面已提供的退款、付款新分頁與後台背景更新；只需套本包。

## 本次更新

- 加深後台文字與表頭、加強白色卡片邊框和陰影，放大文字、按鈕與側欄字體。
- 優惠活動列表 /admin/promotions：顯示發行商既有遊戲折扣、發行商、目前折扣價格、起訖時間、尚未開始／進行中／冷卻中／已結束。優惠券仍獨立管理。
- 評論與檢舉整合到 /admin/comments：預設待處理檢舉，可切全部檢舉及全部評論。未被檢舉的評論也能查看；原軟刪除、恢復與累犯禁言規則保留。
- 修正退款回覆寫入欄位 sender_admin_id → admin_id，與 customer_service_messages 實際結構一致。
- 保留退款申請管理、我的訂單已退款分頁、待付款續付與綠界新分頁付款。

## 三份 SQL：依序執行

1. SQL/01_Hero_Create_Database.sql：完整建立 GamePlatformDB（61 張表），含角色／權限、優惠券、客服、評論、折扣、退款及付款重試結構。SSMS 必須啟用「查詢 → SQLCMD 模式」。只供全新建庫，已有同名資料庫會停止，不會自動刪舊庫；如要重建，請先備份並自行移除舊測試庫，或把三份 SQL 和後端連線一起改成新庫名。
2. SQL/02_Hero_Cart_Minigames.sql：建立 demo_studio 與六款小遊戲。五款已上架；「火柴人格鬥：霓虹對決」為草稿，具備標籤、封面、宣傳素材及啟用版本。預設前台網址 http://127.0.0.1:5173，如有不同請先改 @FrontendBase。
3. SQL/03_Hero_Demo_Data.sql：建立唯一最高管理員、不同權限帳號、示範會員、訂單、時數、評論／檢舉、優惠券、四種狀態的折扣與明確標示 [DEMO] 的操作紀錄。只供空的新庫第一次載入；不會清空已有的帳號／訂單。不要再執行舊 QA26 假資料 SQL。

新增表名稱（相較原始基礎結構）：coupon_rules、member_coupon_claims、admin_operation_logs、support_order_links、support_coupon_links、support_reply_templates、support_internal_notes、support_faqs、support_audit_logs、review_violations、member_review_mutes、game_discounts、refund_requests、payment_attempts。完整建庫也包含其他既有基礎表。

## 帳號與展示內容

所有本包新建帳號的密碼均為 **HeroDemo2026!**。

| 帳號 | 用途 |
| --- | --- |
| hero_admin | 唯一最高管理員，完整管理功能 |
| demo_staff | 營運、客服、退款、評論、優惠活動及紀錄查詢 |
| demo_reviewer | 固定遊戲／發行商審核、會員管理、評論處理 |
| demo_support | 客服接手與回覆；退款決定由 hero_admin 或 demo_staff 處理 |
| demo_studio | 發行商，持有六款小遊戲 |
| demo_aurora | 一般玩家，遊戲庫、評論與時數資料 |
| demo_lin | 一般玩家，含一筆已退款訂單 |
| demo_miko | 一般玩家，含一筆待處理退款申請 |
| demo_kai | 一般玩家，兩則待處理廣告檢舉可測試累犯判定 |
| demo_neo | 一般玩家，兩則已確認違規，示範禁言六小時 |
| demo_sora | 一般玩家，含一筆待付款訂單可測試新分頁續付 |

一般會員的遊戲庫含五款已上架遊戲的示範購買／時數；待付款及已退款遊戲不會當作有效遊戲權益。流程測試草稿完全沒有購買、評論或遊玩紀錄。操作、登入與付款紀錄都是明確的示範資料，沒有寄驗證信或建立真實綠界交易。

## 火柴人格鬥的完整流程測試

1. demo_studio 登入，選草稿「火柴人格鬥：霓虹對決」。檢查版本、封面及宣傳素材後送審；發售日期預設為建資料的兩天後。若已不足 24 小時，先在修改遊戲中調整到兩天後。
2. hero_admin 或 demo_reviewer 到遊戲審核頁核准。核准後先是「即將發售」，不能加入購物車。
3. 若不想等待，02 SQL 最後的註解區提供只作用於這款示範遊戲的時間加速 SQL。核准後選取對應 UPDATE 執行，等待後端每分鐘排程開賣；它不會跳過審核。
4. 一般會員從商店測試購買，再從遊戲庫開始遊玩、全螢幕及查看時數。
5. 發行商申請下架，可測試 24 小時內取消；另有註解 SQL 加速期限到期，測試正式下架。重新送審前，仍需設定未來的發售日期。

版本 ZIP 位於 gameplatform-frontend/public/minigames/lifecycle-demo.zip，可在版本管理直接填 http://127.0.0.1:5173/minigames/lifecycle-demo.zip，或下載後測試 ZIP 上傳。它是 CART 火柴人格鬥編譯出的可用獨立版本；解壓後以 HTTP 靜態伺服器開啟 index.html。也可直接開啟 /minigames/lifecycle-build/index.html 檢查獨立版本。

## 程式套用

- 備份原檔，把本包兩個 gameplatform-* 資料夾合併到專題同名資料夾，不要多建一層。
- 停止舊後端，執行 ./mvnw.cmd clean package -DskipTests，再用新檔啟動；否則舊的 sender_admin_id 程式仍會被執行。前端重啟 npm run dev，按 Ctrl+F5 並重新登入。
- 若網頁由後端打包提供，先在前端 npm run build:backend，再建置後端。
- 本包不覆蓋你的 application.properties、資料庫帳密、JWT 或 Google 設定；新資料庫名稱需與原後端連線一致。

## 驗證範圍

39 項後端隔離測試及 21 項新增畫面檢查通過，涵蓋退款實際欄位、四種折扣狀態、搜尋與權限、評論頁整合、文字／卡片對比與獨立遊戲版本。前後台建置成功。三份 SQL 已核對 61 張表、種子 INSERT 欄位、括號、無 BOM 及草稿排除條件；尚未在你的 SQL Server 從零實際建庫，也未對外部綠界付款。

退款仍為測試退款與已完成的人工退款登記，不會自動執行正式綠界退刷。ATM／超商付款通知仍需公開 HTTPS 後端回呼網址。
