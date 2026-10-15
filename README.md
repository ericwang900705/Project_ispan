# HERO 客服系統

所有以下新建帳號密碼：Demo1234!
| 身分 | 帳號 | 入口 |
|---|---|---|
| 管理者 | `hero_demo_admin` | `http://localhost:5174` |
| 客服一 | `hero_demo_support1` | `http://localhost:5174` |
| 客服二 | `hero_demo_support2` | `http://localhost:5174` |
| 會員一 | `hero_demo_member1` | `http://localhost:5173` |
| 會員二 | `hero_demo_member2` | `http://localhost:5173` |

## 功能範圍

| 功能 | 目前程式內容 |
| --- | --- |
| 客服工作總覽 | 查詢待接手、自己負責、其他客服負責及全部未結束工作；切換聊天／案件、搜尋、分頁、接手。 |
| 會員即時客服 | 右下角客服視窗、開始對話、歷史聊天室、搜尋、訊息及關閉對話。 |
| 客服即時工作台 | 聊天室列表、接手、文字回覆、關閉，以及摘要後轉正式案件。 |
| 正式案件 | 列表、搜尋、狀態／分類／優先度／等待回覆方篩選、明細、回覆、接手、優先度調整及結案。 |
| 案件圖片 | 選圖、預覽、上傳及取得有權限的圖片內容。 |
| 多遊戲關聯 | 轉案可選多款遊戲並儲存關聯；標準分類「遊戲問題」與舊稱「遊戲」皆支援。 |
| 客服通知 | 通知列表、未讀數量、單筆／全部已讀與相關頁面跳轉。 |
| 即時更新 | SSE 更新訊號、斷線重連與後端心跳；前端收到訊號後重新查詢資料。 |
| 分開登入 | 會員與後台客服使用不同登入畫面、Port 與 API，共用 JwtTokenProvider。 |

本版只串接客服及必要登入；ADMIN 主頁與完整後台工作列已還原，其他模組只顯示入口名稱、尚未串接及負責模組，不提供資料操作。沒有串接商城、購物車、付款、好友、會員註冊、寄驗證信、密碼重設、遊戲維護或人員權限管理。既有會員與後台帳號由小組資料庫提供。


### 啟動兩個前端入口

| 入口 | 網址 | 支援身分 |
| --- | --- | --- |
| 會員登入 | http://127.0.0.1:5173/#/member/login | 會員、發行商 |
| 後台客服登入 | http://127.0.0.1:5174/#/staff/login | 客服、最高管理員 |
| 共用後端 | http://127.0.0.1:8080 | 兩個前端共用 |

目前前後端均以本機開發為預設；要給其他電腦連線，需要另行設定監聽位址、API 來源及 CORS。

## 現有前端指令
| `npm run dev` | 同時啟動會員 5173、後台 5174；不啟動後端。 |

## 頁面路由

路由採 Hash 模式，實際網址包含 `#`。

| 路徑 | 頁面／用途 |
| --- | --- |
| `/member/login` | 會員登入。 |
| `/staff/login` | 後台客服登入。 |
| `/member/support` | 會員客服首頁。 |
| `/admin/overview` | ADMIN 管理員主頁；未串接指標顯示 —，不呼叫其他模組 API。 |
| `/admin` | 客服工作總覽。 |
| `/admin/chats` | 即時客服工作台。 |
| `/tickets` | 會員自己的案件／客服可處理的案件。 |
| `/tickets/:id` | 案件詳細內容。 |
| `/staff/unavailable` | 審核工作區未包含的提示頁，不是完整審核功能。 |
| `/`、`/login`、`/guide` | 依入口或登入身分重新導向。 |

會員模式與後台模式各自限制登入入口；已登入者還會依角色與頁面規則檢查存取。後端仍需驗證操作權限，不能只靠前端路由或不同 Port。

## 登入與 JWT

### 登入 API

| 身分 | API | 請求欄位 | 成功回傳 |
| --- | --- | --- | --- |
| 會員 | `POST /api/auth/login` | `username`、`password` | JWT 純字串。 |
| 後台 | `POST /api/admin/auth/login` | `adminAccount`、`password` | `token`、`adminName`、`roleCode` 物件。 |
| 查詢目前身分 | `GET /api/auth/me` | Bearer Token | `id`、`role`、`username` 等。 |

## 客服流程與資料規則

1. 會員開始對話；若已存在未關閉聊天室，後端回傳同一間，不同時新增第二間未關閉聊天室。
2. 客服從工作總覽／聊天室列表接手後回覆；會員與客服可查看歷史訊息。
3. 可直接解決並關閉聊天，或由負責客服整理摘要轉正式案件。
4. 轉案時寫入分類、標題、摘要、優先度與遊戲關聯；建立案件後關閉原聊天室，保留來源關聯。
5. 會員在案件頁繼續補充，客服回覆、調整優先度及結案。

| 資料 | 狀態／限制 |
| --- | --- |
| 聊天室 | `OPEN`、`CLOSED`。 |
| 案件 | `OPEN`、`IN_PROGRESS`、`CLOSED`。 |
| 優先度 | `LOW`、`NORMAL`、`HIGH`、`URGENT`。 |
| 遊戲關聯 | 每案最多 20 款；遊戲須已存在；遊戲類案件至少選擇一款。 |
| 圖片格式 | JPG、PNG；後端解碼檢查並重新編碼。 |
| 圖片大小 | 每張最多 5 MiB，每則最多 3 張。 |
| 圖片尺寸 | 任一邊最多 4096 像素，總像素最多 12,000,000。 |
| 整體上傳 | `spring.servlet.multipart.max-request-size=16MB`。 |

`Admin.vue` 的轉案件表單是工作台右側 `transfer-panel`，並非另開遮罩彈窗。分類、優先度、遊戲選擇、標題、摘要與送出按鈕都放在該區塊。

## 前端 Vue 檔案對照（16 個）

以下路徑相對於 `gameplatform-frontend/src/`。

| 檔案 | 用途 |
| --- | --- |
| `App.vue` | 網站外框、頂欄、側邊選單、登出、通知與右下角客服掛載。 |
| `views/AdminDashboard.vue` | 客服工作統計、工作佇列、搜尋與接手入口。 |
| `views/Admin.vue` | 即時客服工作台與右側轉案表單。 |
| `views/Tickets.vue` | 案件列表、搜尋、篩選與分頁。 |
| `views/TicketDetail.vue` | 案件內容、回覆、附件、關聯遊戲、來源聊天、接手、優先度與結案。 |
| `views/MemberSupportHome.vue` | 會員客服首頁、開啟客服視窗與查看案件入口。 |
| `views/MemberLogin.vue` | 會員登入頁。 |
| `views/StaffLogin.vue` | 後台客服登入頁。 |
| `components/ChatWidget.vue` | 會員右下角客服視窗與歷史聊天室。 |
| `components/Conversation.vue` | 共用訊息顯示、傳送、較早訊息與案件圖片上傳。 |
| `components/GlobalNotificationBell.vue` | 通知鈴鐺、未讀、已讀與跳轉。 |
| `components/LoginForm.vue` | 兩個登入頁共用的表單與錯誤提示。 |
| `components/ManagementSidebar.vue` | 客服總覽、即時客服與案件的導覽。 |
| `components/SearchBar.vue` | 共用搜尋框。 |
| `components/TicketImage.vue` | 帶登入憑證讀取、顯示及放大案件圖片。 |
| `components/UiIcon.vue` | 共用 SVG 圖示。 |

### 其他前端檔案

| 檔案 | 用途 |
| --- | --- |
| `src/main.js` | Vue 啟動、Hash 路由、登入還原與頁面守衛。 |
| `src/api.js` | 登入、共用 API、Token、到期處理、還原登入、登出與 SSE。 |
| `src/permissions.js` | 前端角色名稱、導向與存取規則。 |
| `src/utils/ticketEnhancements.js` | 優先度、等待回覆方與等待時間顯示。 |
| `src/styles/core.css` | 共用基礎與控制項樣式。 |
| `src/styles/layout.css` | 外框、後台與響應式排版。 |
| `src/styles/support.css` | 聊天、案件、轉案、圖片與通知樣式。 |
| `src/styles/theme.css` | 顏色與視覺主題覆蓋。 |
| `vite.config.js` | 會員／後台 Port、Vue 外掛與 API proxy。 |
| `scripts/build-backend.mjs` | 建置前端並複製到後端靜態目錄。 |

## 後端 Java 檔案對照（19 個）

以下路徑相對於 `gameplatform-backend/src/main/java/gameplatform/`。

| 檔案 | 用途 |
| --- | --- |
| `GameplatformApplication.java` | Spring Boot 啟動入口與排程啟用。 |
| `ServletInitializer.java` | WAR 部署到 Servlet 容器的初始化入口。 |
| `config/SecurityConfig.java` | 共用安全規則、CORS、401／403、密碼編碼器與 JWT Filter 掛載。 |
| `config/JwtAuthenticationFilter.java` | 讀取 Bearer Token、驗證及建立 Spring Security 身分。 |
| `member/util/JwtTokenProvider.java` | 會員／後台 JWT 簽發、驗證及 Claims 解析。 |
| `support/config/SupportAccountConfig.java` | 獨立客服版的會員／後台帳號讀取器。 |
| `support/config/SupportActorResolver.java` | 將共同 JWT 轉為客服 Actor，查核帳號狀態與角色。 |
| `support/config/SupportWebConfig.java` | 註冊 Actor 參數轉接器並啟用排程。 |
| `support/controller/AuthController.java` | 獨立客服版的兩個登入 API，回傳共同 JWT 格式。 |
| `support/controller/SupportController.java` | 工作總覽、聊天、案件、分類、遊戲、附件與 SSE API。 |
| `support/controller/NotificationController.java` | 通知列表與已讀 API。 |
| `support/service/FrontAccountService.java` | 讀取既有會員密碼歷程、鎖定與驗證狀態。 |
| `support/service/SupportService.java` | 客服核心 SQL：聊天、接手、轉案、案件、回覆、附件與權限檢查。 |
| `support/service/NotificationService.java` | 建立通知、查詢收件者、未讀／已讀及通知更新訊號。 |
| `support/service/SupportImageValidator.java` | 圖片大小、格式、尺寸與重新編碼驗證。 |
| `support/model/Actor.java` | 目前登入者資料及會員／客服權限判斷。 |
| `support/model/Models.java` | 聊天室、案件、訊息、附件、分頁與輸入資料格式。 |
| `support/model/EventHub.java` | SSE 訂閱、更新訊號、交易完成後發送與 15 秒心跳。 |
| `support/model/Problem.java` | 統一客服錯誤回應與常見輸入／附件錯誤處理。 |

## API 對照

以下列出目前 Controller 內的路徑；除兩個登入 API 外，需要 Bearer Token。即使名稱含 `public`，`GET /api/public/config` 目前也要求登入。

| 方法 | 路徑 | 用途 |
| --- | --- | --- |
| POST | `/api/auth/login` | 會員登入。 |
| POST | `/api/admin/auth/login` | 後台登入。 |
| GET | `/api/auth/me` | 目前客服身分。 |
| GET | `/api/public/config` | 客服基本設定。 |
| GET | `/api/admin/workload` | 客服工作數量。 |
| GET | `/api/admin/queue` | 工作佇列；kind、scope、q、page。 |
| GET | `/api/categories` | 案件分類。 |
| GET | `/api/games` | 轉案遊戲選項，支援 q 搜尋。 |
| GET | `/api/member/rooms/latest` | 會員最近聊天室。 |
| POST | `/api/member/rooms` | 開始／沿用會員未關閉聊天室。 |
| GET | `/api/rooms` | 聊天室列表；status、mine、page、q。 |
| GET | `/api/rooms/{id}` | 聊天室明細。 |
| POST | `/api/rooms/{id}/close` | 關閉聊天室。 |
| POST | `/api/admin/rooms/{id}/claim` | 客服接手。 |
| POST | `/api/admin/rooms/{id}/escalate` | 聊天轉正式案件。 |
| GET | `/api/rooms/{id}/messages` | 查詢聊天訊息；before／after。 |
| POST | `/api/rooms/{id}/messages` | 傳送聊天文字。 |
| GET | `/api/tickets` | 案件列表；status、page、q、categoryId、priority、waitingFor。 |
| GET | `/api/tickets/{id}` | 案件明細。 |
| GET | `/api/tickets/{id}/origins` | 案件來源聊天。 |
| POST | `/api/admin/tickets/{id}/claim` | 接手案件。 |
| PUT | `/api/admin/tickets/{id}/priority` | 調整案件優先度。 |
| POST | `/api/admin/tickets/{id}/close` | 結案。 |
| GET | `/api/tickets/{id}/messages` | 查詢案件回覆；before／after。 |
| POST | `/api/tickets/{id}/messages` | JSON 文字回覆，或 multipart 圖片回覆。 |
| GET | `/api/tickets/{ticketId}/attachments/{attachmentId}` | 取得有權限的附件圖片。 |
| GET | `/api/notifications` | 通知列表與未讀數量。 |
| POST | `/api/notifications/{id}/read` | 單筆通知已讀。 |
| POST | `/api/notifications/read-all` | 所有通知已讀。 |
| GET | `/api/events` | SSE 即時更新連線。 |

工作佇列 kind 支援 `rooms`／`tickets`；scope 支援 `unassigned`／`mine`／`others`／`all`。具體操作仍由 Service 查核是否為會員本人、是否已接手與是否已結束。

## 客服資料表

| 資料表／物件 | 用途 |
| --- | --- |
| `chat_rooms` | 客服聊天室、負責人、狀態及轉出案件。 |
| `chat_messages` | 即時聊天訊息。 |
| `customer_service_categories` | 案件分類。 |
| `customer_service_tickets` | 案件主資料與優先度。 |
| `customer_service_messages` | 正式案件回覆。 |
| `customer_service_attachments` | 案件圖片的內容、格式與尺寸。 |
| `customer_service_ticket_games` | 案件與多款遊戲的關聯。 |
| `notifications` | 客服事件通知與已讀時間。 |
| `customer_service_ticket_no_seq` | 產生 CS 格式案件編號的 SQL Server Sequence。 |

分類 INSERT 已直接放入根目錄完整建庫 SQL 的客服分類表後方。標準名稱為「帳號問題、遊戲問題、購買問題、其他問題」；若只選取分類區塊對既有資料執行，已有舊稱「遊戲」時會沿用該筆，不重編既有分類 ID。完整腳本保留案件 Sequence；後方補充區塊僅保留多遊戲案件、案件優先度及客服通知。

## 與小組專案合併

目前是可獨立啟動的客服專案，包含供本機測試使用的登入轉接。JWT 採共同格式，不代表整包可以直接覆蓋小組網站。

1. 小組已有 `JwtTokenProvider`、`JwtAuthenticationFilter`、`SecurityConfig` 時，各保留一份；只合併需要的 CORS、API 錯誤回應及客服身分轉接。
2. 小組已有相同登入 API 時，移除此包的 `support/controller/AuthController.java`、`support/config/SupportAccountConfig.java`、`support/service/FrontAccountService.java`，改用組員實作，避免相同路徑重複註冊。
3. 此包客服仍使用 `/api/admin/...`、`/api/tickets`、`/api/games` 等原路徑；其中 `/api/games` 只是遊戲選項，合併時需與商城／遊戲模組協調路徑及回傳格式。
4. 將客服頁面、元件與路由加入組員網站；App.vue、main.js 及全站樣式需要整合，不能直接完整覆蓋組員外框。
5. 此包右下角元件名為 `ChatWidget.vue`；若商城使用 `CustomerService.vue`，由該入口引入／串接這個元件。
6. 登入、資料庫角色與 CORS 必須依合併後環境一致設定，並在同一套資料庫上驗證。


## 本次客服修正範圍

| 檔案 | 修改 |
| --- | --- |
| `gameplatform-frontend/src/views/Admin.vue` | 「遊戲問題」與「遊戲」都會顯示多遊戲選擇。 |
| `gameplatform-backend/src/main/java/gameplatform/support/service/SupportService.java` | 對上述兩種分類強制至少選一款遊戲。 |
| `gameplatform-backend/src/main/resources/application.properties` | 客服角色代碼對齊完整 SQL 的 ADMIN／SUPPORT。 |
| `gameplatform-backend/config/local.properties.example` | 更新角色設定說明。 |
| `SQL_server資料庫_完整建庫_20261014.sql` | 唯一建庫 SQL；保留客服分類、多遊戲、優先度及客服通知，移除非客服補充區塊。 |
| `gameplatform-backend/sql/` | 移除多餘的客服獨立 SQL；不再要求額外執行客服腳本。 |

完整建庫 SQL 保留原本共用建表內容與客服分類 INSERT，後方補充區塊僅保留多遊戲案件、案件優先度與客服通知。已移除角色／權限校正、發行商送審與下架、審核已讀與標籤、金流及會員驗證的補充區塊。共用 JWT 未修改，未操作真實資料庫。若本機 local.properties 已覆寫角色代碼，請自行確認為 ADMIN／SUPPORT；本次不覆蓋個人的本機設定。

## 目前限制與檔案現況

- **遊戲分類已修正**：前端 isGame 與後端 escalate 均辨識「遊戲問題」及舊稱「遊戲」，並忽略名稱前後空白；遊戲類案件未選遊戲時會被後端拒絕，其他分類仍可不關聯遊戲。
- **帳號資料**：本包有完整建庫 SQL，但不提供管理員測試帳密或重設密碼；會員、遊戲與後台帳號需由小組準備。
- **不含測試原始檔／測試指令**：目前壓縮檔沒有 src/test，也沒有前端 test scripts；不能從現有檔案宣稱完整測試已通過。
- **編譯產物**：本次交付不收錄來源壓縮檔的 target/classes；請依原始碼重新建置。
- **本次驗證**：會員／後台模式前端建置皆通過；前端 6 項分類判斷與後端 6 項轉案流程檢查通過。後端涵蓋新／舊分類必填、空白名稱、正常寫入多遊戲關聯與關閉來源聊天、非遊戲分類及無效遊戲拒絕。
- **驗證限制**：後端檢查使用帳號／資料存取替身，編譯與測試使用 Java 17；原 pom 仍要求 Java 25。沒有連線到 SQL Server 執行修改後的完整建庫腳本，沒有重跑完整網站端到端與 Java 25 Maven 啟動。測試檔與下載套件不收錄在交付包。

將本 README.md 放在 `HERO_customer_service` 根目錄，與兩個 gameplatform 目錄同層即可。


## 客服通知使用範圍與角色準備

通知目前僅用於客服，使用既有 notifications 表及 /api/notifications API，不另建通知表或 SQL 檔。
僅產生及查詢 NEW_CHAT、TICKET_CREATED、CHAT_REPLY、TICKET_REPLY、CHAT_MESSAGE、TICKET_MESSAGE；未讀數量、單筆及全部已讀也限定這六種事件。其他事件的通知資料不刪除、不標記已讀。
通知對象為使用客服功能的會員，以及對應客服／管理者；目前不串接發行商審核、付款或會員驗證通知。畫面標題為「客服通知」。

已移除補充區塊內的角色／權限預設資料及校正操作，保留原本共用角色資料表。新建資料庫後，由小組負責人準備 ADMIN／SUPPORT 角色及後台帳號；本包不再自動建立這些角色或更動角色權限對照。若小組使用其他角色代碼，依前述設定對齊。

本次驗證：客服通知四種查詢／已讀操作，在會員及客服身分下共 8 項範圍與收件人參數檢查通過；修改相關 Java 編譯與兩種前端模式建置通過。SQL 已檢查補充區塊及唯一檔案結構，未連線 SQL Server 實際建庫。


## ADMIN 工作列與串接分工（本次還原）

ADMIN 登入後進入 /admin/overview；客服帳號仍進入 /admin。主頁延用先前卡片與營收區塊版型，數據未串接時顯示 —。不使用展示資料，也不呼叫 /management/dashboard 或其他非客服功能 API。
左側工作列恢復原分類；非客服項目為停用按鈕，標示「尚未串接」及需串接的模組。實際人名未提供，以下依功能分工標示，供小組再確認。

| 工作列分類 | 需串接模組 |
| --- | --- |
| 後台人員、管理員權限管理 | 人員／權限組 |
| 會員管理 | 會員組 |
| 遊戲管理 | 遊戲組；上架審核由遊戲／審核組 |
| 發行商管理 | 發行商組 |
| 訂單管理 | 購物車／訂單組；退款由訂單／金流組 |
| 評論審核 | 評論組 |
| 優惠活動、優惠券 | 優惠活動組 |
| 系統紀錄 | 操作紀錄由各模組；登入紀錄由登入／會員組 |
| 客服工作總覽、即時客服、客服案件 | 已串接現有客服後端 |

必要的會員／後台登入、JWT、客服遊戲搜尋、圖片、通知及 SSE 保留原串接。沒有加入非客服後端、其他組 Controller 或修改資料庫。SUPPORT 只看得到客服選單；ADMIN 可看全部分類。
新增前端 scripts/dev-all.mjs 管理兩個 Vite 程序：任一入口啟動失敗會停止另一入口，避免誤以為兩端都已啟動。5173／5174 必須未被其他程序占用；後端 8080 需另行啟動。

本次前端驗證：會員與後台 production 建置通過；ADMIN 工作列的 17 個停用項目與負責模組、SUPPORT 選單隔離、主頁空值及管理頁路由限制的元件／邏輯檢查通過。npm run dev 的兩個 Port HTTP 200、Ctrl+C 同時關閉、單一 Port 占用時停止另一端均通過。後端與 SQL 比對未變動；未連線 SQL Server，未執行真實帳號登入的端到端測試。
