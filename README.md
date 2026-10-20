# HERO 遊戲平台：更新內容與測試說明

更新日期：2026-10-20。本文件整理先前交付的整合版、後續更新包及測試資料使用方式。本次只整理文件，未重新執行程式測試或連線 SQL Server。

## 1. 已更新的功能

| 項目 | 更新內容 |
| --- | --- |
| 後臺管理 | 接上訂單明細、退款紀錄查詢、評論審核、評論檢舉、平台標籤、優惠券、會員登入紀錄與後臺操作紀錄 |
| Google 登入 | 僅提供前臺會員登入與帳號綁定；後臺人員仍使用帳號密碼。已有同信箱會員須先登入原帳號再綁定 |
| 姓名顯示 | 後臺優先顯示資料庫的 `admin_name`；登入帳號及 JWT 身分不變。會員表沒有額外真實姓名欄位，前臺沿用 `username` |
| 角色權限 | 身分 API 回傳個別權限；請求重新確認帳號狀態及角色，補上跨會員推薦／偏好查詢限制 |
| 好友與登入 | 修正新舊好友路由衝突、Google 新會員驗證時間、未驗證信箱登入限制及驗證信網址 |
| 前臺 CSS | 恢復商城導覽列、會員下拉選單、登入頁與發行商遊戲編輯排版，保留 Google 登入及姓名顯示 |
| 後臺按鈕 | 停權／退回紅色、核准／恢復綠色、查看藍色 |
| 發行商審核 | 申請與審核歷史可查看詳情；搜尋改為畫面內篩選，不重新載入頁面 |
| 搜尋列 | 調整訂單、退款、標籤、評論、檢舉、優惠活動與系統紀錄等管理頁的控制項對齊 |
| 日曆 | 優惠券起訖時間、會員生日、發行商報表起訖日期及遊戲預定發行時間改用共用日曆，需要時間的欄位保留時分選擇 |

退款目前只有紀錄查詢，不代表已提供實際退款；優惠券管理與尚未串接的「優惠活動」是不同功能。

## 2. 更新檔案及覆蓋順序

以先前完整整合專案為基礎，依下列順序解壓並合併至專案根目錄，覆蓋同名檔案。不可直接替換整個 `src` 資料夾。

| 順序 | 更新包 | 程式檔案 |
| --- | --- | --- |
| 1 | `Project_ispan_Frontend_CSS_Update_20261020.zip` | `MemberApp.vue`、`views/MemberLogin.vue`、`views/PublisherGames.vue`、`cart/components/FriendsList.vue`、`styles/cart-integration.css`、`styles/publisher.css` |
| 2 | `Project_ispan_Backoffice_UI_Update_20261020.zip` | `views/AdminMembers.vue`、`views/AdminPublishers.vue`、`views/AdminGames.vue`、`views/AdminOperations.vue`、`styles/admin-management.css` |
| 3 | `Project_ispan_Calendar_Update_20261020.zip` | `components/DatePicker.vue`、`views/AdminOperations.vue`、`views/MemberAccount.vue`、`views/PublisherSales.vue`、`components/PublisherStoreEditor.vue` |

表內程式路徑均相對於 `gameplatform-frontend/src/`。日曆包也包含 `AdminOperations.vue`，所以應最後覆蓋。這三包不修改 SQL。

## 3. 發行商選單清理（依前面說明手動套用）

以下是後續提出的修改步驟，不代表上述更新包已包含這些刪除。

### 移除獨立「審核通知」入口

檔案：`gameplatform-frontend/src/publisher-functions.js`。

1. 從 `publisherFunctions` 移除 `mode: 'notifications'` 的整筆設定（若已刪除可略過）。
2. 從 `publisherGroups` 各組的 `modes` 移除 `'notifications'`。
3. 從 `publisherIcons` 移除 `notifications: 'bell'`。
4. 將路由輔助函式改為：

```js
export const publisherLink = mode =>
  mode === 'sales'
    ? { path: '/publisher/sales' }
    : { path: '/publisher/games', query: { mode } }
```

右上角鈴鐺保留，不刪除通知資料或共用通知元件。

### 移除「優惠活動（尚未串接）」入口

檔案：`gameplatform-frontend/src/components/PublisherSidebar.vue`。

刪除下列整行。這個入口直接寫在側欄中，只改 `publisher-functions.js` 不會讓它消失。

```vue
<RouterLink to="/publisher/offers" class="publisher-offers-link"><UiIcon name="tag" />優惠活動（尚未串接）</RouterLink>
```

儲存後，若瀏覽器仍停在 `/#/publisher/offers`，請點「發行商工作區」返回 `/#/publisher/overview`。刪除入口不會自動變更已開啟的網址；該網址目前顯示 404。

## 4. SQL 更新內容

結構 SQL：`Hero_SQL_server1017_Integrated.sql`。原始上傳檔保留，測試資料另外使用 `Test_Data.sql`。

| 位置 | 變更 |
| --- | --- |
| `games` | 加入 `scheduled_off_shelf_at DATETIME2 NULL` |
| 遊戲狀態限制 | 加入 `PENDING_REVIEW`、`PENDING_OFF_SHELF` |
| `notifications` | 加入 `related_game_id INT NULL` |
| 第 11 段 | 讓既有資料庫套用上述遊戲／通知結構 |
| 第 12 段 | 補建 `dbo.admin_operation_logs`，供後臺操作紀錄查詢及管理異動寫入 |

保留目前 ADMIN／STAFF 個別授權模型，不改回舊 SUPERADMIN 模型。

### 匯入順序

1. 先備份資料庫。
2. 全新測試資料庫：依結構 SQL 的說明執行，完成基礎表及第 10 段權限初始化。
3. 已採 ADMIN 模型的既有資料庫：未套用遊戲／通知結構時執行第 11 段；缺少操作紀錄表時執行第 12 段。只選取需要的完整段落，不重跑前方建庫／建表內容。
4. 最後整份執行獨立的 `Test_Data.sql`。

若出現「無效的物件名稱 dbo.admin_operation_logs」，請確認使用的是含第 12 段的最新結構 SQL，並在程式連線的同一個資料庫執行該段。

### 尚有的資料庫差異

先前檢查指出，付款程式仍使用 `orders.payment_status`、`bank_code`、`virtual_account`、`payment_no`、`payment_expire_date` 五欄；本次從舊 SUPERADMIN 取回的內容沒有補入這五欄。不要把操作紀錄表補齊視為金流相容性也已完成。

目前付款失敗流程使用 `order_status = 'CANCELLED'` 與 `payment_status = 'FAILED'`，不等同於要求訂單狀態新增 `FAILED`。

## 5. 測試檔案

| 檔案／目錄 | 用途 |
| --- | --- |
| `sql/Test_Data.sql` | 完整專案包內的獨立測試資料；只新增資料，不建立或修改表與欄位 |
| `README_測試資料與流程.md` | 完整專案包內的詳細測試情境、帳號及操作說明 |
| `gameplatform-frontend/public/qa-assets/` | 示範圖片、客服附件及測試版本 ZIP |
| `gameplatform-backend/src/test/` | 後端自動化測試及隔離測試設定 |
| `HERO_UI_Screenshots_30.zip` | 先前交付的 30 張介面截圖與圖集；使用示範資料，不能代替實機驗收 |

測試資料涵蓋 7 個後臺帳號、11 個會員、9 款遊戲，以及訂單、優惠券、評論檢舉、客服、聊天、好友與通知。

匯入前可調整 `Test_Data.sql` 的 `@FrontendBase`；本機預設為 `http://localhost:5173`。需要實際收信時，首次匯入前將 `@RealTestEmail` 設成自己持有且平台未使用的信箱，只套用於 `qa26_mail`；預設 `@hero.example` 不會收信。

測試資料採條件新增，不會把已修改的密碼、權限或審核狀態重設成初始值。要重測完整流程，請還原測試備份。測試素材的 `qa-demo-build.zip` 只有說明文件，不是可執行遊戲。

## 6. 測試帳號

共同初始密碼：`HeroQA2026!`。僅供測試環境使用。

### 後臺

| 帳號 | 姓名 | 用途 |
| --- | --- | --- |
| `qa26_admin` | QA26 王管理 | 最高管理員、人員與授權管理 |
| `qa26_staff_all` | QA26 林全能 | STAFF 業務權限，不含人員授權 |
| `qa26_staff_limited` | QA26 陳限權 | 僅訂單與標籤權限 |
| `qa26_reviewer` | QA26 張審核 | 審核角色 |
| `qa26_support` | QA26 李客服 | 客服案件與聊天 |
| `qa26_pending` | QA26 周待審 | 待審，應拒絕登入工作區 |
| `qa26_disabled` | QA26 趙停用 | 停用，應拒絕登入 |

### 前臺

| 帳號 | 用途 |
| --- | --- |
| `qa26_member` | 一般會員，遊戲庫、購物車、訂單、評論、客服與通知 |
| `qa26_alice` | 好友、贈禮、隱藏評論及客服樣本 |
| `qa26_bob` | 收禮、已刪除評論及已離開群組情境 |
| `qa26_locked` | 鎖定會員 |
| `qa26_unverified` | 信箱未驗證 |
| `qa26_empty` | 空遊戲庫，用來測購買與收禮 |
| `qa26_publisher` | 已核准發行商 |
| `qa26_publisher2` | 第二發行商，用來測資料隔離 |
| `qa26_applicant` | 發行商申請待審 |
| `qa26_rejected` | 發行商申請曾被退回 |
| `qa26_mail` | 寄信及重設密碼 |

## 7. 建議測試流程

| 項目 | 操作 | 預期結果 |
| --- | --- | --- |
| 姓名 | 後臺登入並重整 | 顯示姓名，仍用帳號登入 |
| 權限 | limited 查看訂單／標籤，再直接輸入未授權頁網址 | 有權限可用；未授權頁面與 API 拒絕 |
| 停權 | 將已登入帳號停權，再用原 Token 請求 | API 拒絕存取 |
| 發行商審核 | 開 applicant 申請詳情、搜尋、核准或退回 | 可查看資料；搜尋不重整；退回須填原因 |
| 遊戲 | 草稿送審、核准、下架及取消下架；第二發行商嘗試修改 | 公開搜尋不顯示草稿；不能修改別人的遊戲 |
| 訂單／退款 | 搜尋 `QA26-ORDER-` 並看明細、退款紀錄 | 訂單資訊與授權狀態符合樣本；退款頁為查詢 |
| 優惠券 | 測八折、滿額折抵、停用、過期及額度滿 | 390 元八折為 312；1080 元折 100 為 980；無效券拒絕 |
| 評論／檢舉 | 先測 stale 快照，再測 pending；測隱藏與恢復 | 舊快照拒絕自動隱藏；已刪除評論不可恢復；已結案不可重審 |
| 標籤 | 刪未使用標籤，再刪使用中的標籤 | 未使用可刪，使用中拒絕 |
| 客服 | 測 waiting／mine／other／closed，接手、回覆、附件及轉案件 | 分派與資料隔離正確，案件狀態正確 |
| 好友／聊天 | 邀請、接受、拒絕及群聊 | 非群組成員不能讀取訊息 |
| 日曆 | 選日期、切年月、選時分、測手機版 | 正確回填；開關日曆不送出表單；保留必填與範圍限制 |
| 選單 | 完成第 3 節後重整發行商工作區 | 無獨立審核通知及優惠活動入口，鈴鐺仍在 |

`QA26-TREND-` 歷史訂單只供整單趨勢展示，沒有明細或遊戲授權，不用來驗收遊戲銷量與對帳。

## 8. 自動化測試與外部服務

在 `gameplatform-backend` 執行後端測試（需 Java 25 與 Maven 相依套件）：

```powershell
.\mvnw.cmd test
```

Linux／macOS：

```bash
./mvnw test
```

先前整合檢查記錄為 86 項後端測試通過，會員／後臺建置通過；後端整合測試使用隔離 H2，瀏覽器操作部分使用模擬 API。後臺 UI 及日曆更新先前完成建置與模擬操作檢查；CSS 更新當時僅完成建置，未完成瀏覽器畫面驗證。上述均為先前檢查結果，本次整理 README 沒有重新測試。

仍需實際環境驗收：

- SQL Server：結構 SQL 與測試資料尚未在你的伺服器執行；匯入成功也不代表全部程式功能相容。
- Google：設定前後端相同 Client ID 與授權來源，用真實帳號測登入、綁定及停權；測試資料不會假造 Google 綁定。
- 郵件：設定 SMTP 與對外可連線的會員網址；其他電腦不能用寄件主機的 localhost 開啟連結。
- 金流：先處理付款欄位差異，再設定綠界測試特店與公開 HTTPS 回呼，從前臺建立新訂單完成測試；不能直接把 SQL 改為 PAID 當作付款驗收。
- AI：真實推薦更新需要有效 Gemini 設定；測試設定不能代替正式設定。

先前報告另列生日「只能設定一次」的前後端規則差異、頭像上傳驗證及免費遊戲領取限制，尚未因本次文件整理而修正。未宣稱所有功能已完成端到端驗收。
