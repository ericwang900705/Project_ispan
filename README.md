# HERO 已串接／未串接功能

更新日期：2026-10-14。資料庫基準：使用者最新提供的 `SQL_server資料庫_1013-2.txt`。依據目前專案前端呼叫、後端 Controller／Service 與 SQL 操作判斷。本次商城來源為 [cart 分支](https://github.com/ericwang900705/Project_ispan/tree/cart)，固定比對 commit `11d6653c77bdcd218cec35370f90317e8b65405f`。其他分支目前僅檢查，尚未併入。

「已串接」表示程式內已有 API 與資料存取流程，不表示已在使用者 SQL Server、SMTP 或綠界外部環境完成實測。

## 已串接

| 功能 | 內容 |
|---|---|
| 會員註冊／驗證 | 無頂欄註冊頁、成功後驗證信頁、重寄驗證信、驗證成功轉登入 |
| 會員帳號 | SQL 登入、登入失敗鎖定、忘記／重設／修改密碼、個人資料讀取／編輯 |
| JWT／角色 | 統一 Token、前後台登入分離、角色授權、登出撤銷；商城核對 JWT 會員身分 |
| 商城查詢 | 公開遊戲列表、搜尋、詳情 |
| 購物車（本次新增） | 加入、移除、清空、已擁有／重複／未上架遊戲檢查；後端也支援送禮，前端尚無受贈人選擇介面 |
| 優惠券（本次新增） | 可用券查詢、整車套用／取消、期限與最低金額、總量／每會員上限；待付款時預留，取消／失敗時釋放 |
| 訂單（本次新增） | 原價／折扣／總額快照、我的訂單頁、待付款取消、ATM／超商資訊持久保存 |
| 綠界金流（本次新增） | 待付款訂單、付款表單、簽章／特店／訂單／金額核對、付款／取號回呼、付款後返回頁面；需自行設定後啟用 |
| 會員遊戲庫（本次新增） | 真實資料查詢、付款成功後以 order_item_id 發放遊戲；重複通知不重複發放 |
| 玩家評論（本次新增） | 公開列表、擁有遊戲者新增／修改／刪除、作者權限；被隱藏評論不能自行恢復 |
| 發行商管理 | 遊戲新增／編輯、封面上傳與預覽、標籤／媒體／版本資料、概覽與銷售查詢 |
| 上下架審核 | 申請、審核、紀錄與恢復流程 |
| 即時客服與案件 | 右下角聊天、歷史訊息、接手／關閉／轉案件、搜尋、優先度、圖片附件、關聯遊戲 |
| 通知與平台統計 | 通知查詢／已讀、SSE 事件、平台統計 |
| SQL Server 設定 | 正式 SQL 模式、設定範本與啟動工具；不使用示範登入或 H2 執行資料 |

## 未串接／未完整串接

| 功能 | 狀態 |
|---|---|
| 好友名單／新增好友／好友聊天 | 前端仍有固定示範內容，沒有本版可用的好友後端；與已接上的客服聊天不同 |
| 點數商店／兌換／購物回饋點數 | 商品兌換與點數餘額流程未整合；本次訂單不發回饋點數，pointsEarned 為 0 |
| Google／第三方登入 | member/friend 有候選程式；尚未接入目前 JWT 與會員資料規則 |
| 發行商申請／會員升級審核 | member/friend 有候選程式；本版發行商仍以既有 members.is_publisher 為準 |
| AI 推薦／熱門遊戲／行為紀錄 | game/admin 有候選程式；尚未整合到目前商城 |
| 退款／對帳／付款後補償 | 未實作自動退款；取消後仍付款或授權衝突時須人工核對，後端拒絕自動發放 |
| 免費遊戲領取 | 目前 checkout 僅支援正整數新台幣；免費領取 API 尚未實作 |
| 綠界外部環境 | 程式已接好；尚未實際送出測試付款與接受綠界伺服器通知 |

## 本次啟用方式

1. 使用現有 1013-2 資料庫，先備份，再在 SSMS 選擇自己的資料庫執行 `gameplatform-backend/sql/HERO_1014_商城串接補充.sql`。先前的 HERO_1013_既有資料庫補充.sql 也需已套用。不要重建已有資料庫。
2. 本機展示版編輯 `gameplatform-backend/config/local.properties`。GitHub 版先執行 setup-local.ps1 再編輯；檔案不含組員的 SQL／SMTP 帳密與 JWT 金鑰。SQL 帳號、密碼、資料庫名稱必須填自己實際可登入的設定。
3. 未設定金流時，購物車、優惠券、訂單／遊戲庫查詢仍可用，結帳停用。要測試付款，填官方測試特店 MerchantID／HashKey／HashIV、公開 HTTPS 回呼網址 `ecpay.backend-url`，以及瀏覽器使用的 `ecpay.frontend-url`，再將 `ecpay.enabled=true`。不要用正式商戶來做示範付款。
4. 後端 JDK 25 執行 start.cmd；前端 Node >=22.12 執行 npm ci、npm run dev。升級舊專案需先清理 target，避免殘留已移除的示範設定。

## 驗證

本版已移除 H2 依賴、H2 整合測試、測試資料庫設定與資料初始化檔。移除後 Java 25 重新編譯及剩餘 10 項後端測試通過。前端上次 36 項測試與建置通過；本次未修改前端。

SQL Server／SMTP／綠界外部環境未實測。多實例 JWT 撤銷仍需共享儲存。本檔集中說明串接狀態、使用方式與主要 API，不另附舊說明或更新文件。

## 會員介面更新

- 註冊頁背景修正上邊距塌陷，保持淺藍到深藍漸層，無頂欄，底部只有會員登入／返回商城。
- 個人資料改為左側「個人資料／更改密碼」選單與較小卡片，不顯示會員編號；支援頭像網址預覽與無圖片時的預設頭像。
- 生日首次儲存後鎖定，原本已有生日也視為已設定；後續以數字日期顯示。後端原子檢查，不能透過修改 API 再次變更或清空。地址與頭像仍可修改。
- 原「已發送驗證信」頁每 3 秒用獨立等待憑證向後端確認狀態。信箱完成驗證後，原頁自動轉會員登入。憑證只能查狀態，不能啟用帳號，資料庫只存其雜湊。
- 郵件服務／瀏覽器決定連結是否開新分頁，網站不能強制阻止；新驗證分頁顯示完成與返回原頁提示，不再自動開另一個登入頁。直接在原頁開驗證連結仍可轉登入。
- 既有帳號在此更新前寄出的驗證信沒有等待憑證；如需原頁自動轉登入，更新後重寄一次驗證信。

## 必要 SQL

全新資料庫可執行根目錄 `SQL_server資料庫_完整建庫_20261014.txt`，內含 1013-2 與目前必要補充內容。已有資料庫不要重跑建庫 TXT。

已有資料庫先備份，在你的資料庫執行未套用的補充：

1. `gameplatform-backend/sql/HERO_1013_既有資料庫補充.sql`（若資料庫名稱不同，改開頭 USE）。
2. `gameplatform-backend/sql/HERO_1014_商城串接補充.sql`。
3. **本次新增** `gameplatform-backend/sql/HERO_1014_會員介面補充.sql`，補 member_verifications.watch_token_hash；註冊及重寄驗證信前需套用。

## 主要會員 API

註冊 POST /api/auth/register：username、email、nationalId、rawPassword。重寄 POST /api/auth/resend-verification：email。兩者成功回傳 message、watchTicket；watchTicket 留在原頁 sessionStorage，不是 JWT、也不是驗證連結 token。

GET /api/auth/verification-status?ticket=... 回傳 verified；只接受秘密等待憑證，不接受公開 email 查詢。GET /api/auth/verify?token=... 使用郵件中的 token；成功才啟用帳號。

GET /api/members/me 讀取本人資料。PATCH /api/members/profile 接收 birthDate（僅未設定時）、address、avatarUrl。POST /api/members/change-password 接收 oldPassword、newPassword。受保護 API 都帶 `Authorization: Bearer <accessToken>`。

本次移除 H2 後，剩餘 10 項後端測試通過。SQL Server／SMTP／綠界外部環境仍需實測；原 H2 資料庫整合測試已移除，未改為連線正式資料庫。
