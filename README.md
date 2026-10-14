# HERO 僅客服版：功能與程式檔對照

本說明對應 `HERO_僅客服_GitHub_20261014.zip`，使用該版本的檔名與目錄。

## 功能範圍

- **客服工作總覽**：待接手、自己負責、其他客服負責的聊天與案件。
- **即時客服**：會員右下角客服視窗、歷史訊息、接手、關閉、轉正式案件。
- **客服案件**：搜尋、分類、優先度、案件內容、回覆、圖片附件、多遊戲關聯、結案。
- **客服通知與 SSE 即時更新**。

## 程式位置

| 類型 | 根目錄 |
| --- | --- |
| 前端 Vue／JavaScript | `gameplatform-frontend/src/` |
| 後端 Java | `gameplatform-backend/src/main/java/gameplatform/support/` |
| 資料庫補充腳本 | `gameplatform-backend/sql/01_support.sql` |

以下表格的檔案路徑皆相對於各自根目錄。

## 1. 客服工作總覽

| 類型 | 程式檔 | 用途 |
| --- | --- | --- |
| 前端 | `views/AdminDashboard.vue` | 顯示工作數量、聊天／案件佇列；切換待接手、自己負責、其他客服負責；搜尋、接手及開啟工作。 |
| 前端 | `components/SearchBar.vue` | 共用搜尋框，處理輸入、送出與清除。 |
| 後端 | `controller/SupportController.java` | 接收工作總覽、工作佇列與接手 API 請求。 |
| 後端 | `service/SupportService.java` | 查詢工作統計、篩選佇列及處理接手操作。 |

### SupportService.java 主要方法

| 方法 | 用途 |
| --- | --- |
| `workload()` | 統計客服工作數量。 |
| `workQueue()` | 依聊天／案件、負責範圍與關鍵字查詢工作佇列。 |
| `claimRoom()` | 接手聊天室。 |
| `claimTicket()` | 接手案件。 |

## 2. 即時客服

| 類型 | 程式檔 | 用途 |
| --- | --- | --- |
| 前端 | `components/ChatWidget.vue` | 會員右下角客服視窗：開始聊天、查詢歷史聊天室、搜尋、切換對話及關閉。 |
| 前端 | `views/Admin.vue` | 客服端即時聊天工作台：聊天室列表、搜尋、接手、回覆、關閉及轉正式案件。 |
| 前端 | `components/Conversation.vue` | 雙方共用訊息區：顯示訊息、傳送訊息及載入較早紀錄。 |
| 前端 | `views/MemberSupportHome.vue` | 會員客服首頁，提供開啟客服視窗與查看案件入口。 |
| 前端 | `App.vue` | 網站外框，掛載右下角客服視窗。 |
| 後端 | `controller/SupportController.java` | 接收聊天室、歷史訊息、傳送、接手、關閉與轉案 API 請求。 |
| 後端 | `service/SupportService.java` | 處理聊天室、訊息、操作權限與轉案的資料庫邏輯。 |
| 資料表對應 | `entity/ChatRoom.java` | 對應客服聊天室 `chat_rooms`。 |
| 資料表對應 | `entity/ChatMessage.java` | 對應客服聊天訊息 `chat_messages`。 |

### SupportService.java 主要方法

| 方法 | 用途 |
| --- | --- |
| `latestRoom()` | 取得會員最近的聊天室。 |
| `startRoom()` | 開始客服對話。 |
| `rooms()` | 查詢、搜尋及篩選聊天室列表。 |
| `getRoom()` | 取得聊天室詳細資料。 |
| `messages()` | 查詢聊天或案件訊息，支援較早／較新的訊息。 |
| `send()` | 傳送聊天或案件文字訊息。 |
| `claimRoom()` | 客服接手聊天室。 |
| `closeRoom()` | 關閉聊天室。 |
| `escalate()` | 將聊天室轉成正式案件，寫入分類、優先度與遊戲關聯。 |

## 3. 客服案件

| 類型 | 程式檔 | 用途 |
| --- | --- | --- |
| 前端 | `views/Tickets.vue` | 案件列表、搜尋及狀態／分類／優先度／等待回覆方等篩選。 |
| 前端 | `views/TicketDetail.vue` | 顯示案件內容、回覆、關聯遊戲與原始聊天；提供接手、調整優先度及結案。 |
| 前端 | `views/Admin.vue` | 轉案表單：填寫內容、分類、優先度與選擇多款遊戲。 |
| 前端 | `components/Conversation.vue` | 案件文字回覆、圖片選取、預覽與上傳。 |
| 前端 | `components/TicketImage.vue` | 帶入登入憑證取得並顯示圖片附件。 |
| 前端 | `components/SearchBar.vue` | 案件搜尋框。 |
| 前端 | `utils/ticketEnhancements.js` | 顯示優先度、等待回覆方與經過時間的工具。 |
| 後端 | `controller/SupportController.java` | 接收案件、分類、遊戲選項、回覆與附件 API 請求。 |
| 後端 | `service/SupportService.java` | 處理案件查詢、回覆、接手、優先度、附件與結案。 |
| 後端 | `service/SupportImageValidator.java` | 檢查 JPG／PNG、檔案大小及圖片尺寸，重新編碼後儲存。 |

### SupportService.java 主要方法

| 方法 | 用途 |
| --- | --- |
| `tickets()` | 搜尋、篩選與分頁查詢案件。 |
| `getTicket()` | 取得案件詳細資料。 |
| `categories()` | 取得客服案件分類。 |
| `games()` | 查詢轉案時可選擇的遊戲 ID／名稱。 |
| `escalate()` | 建立轉出的正式案件與多遊戲關聯。 |
| `updateTicketPriority()` | 修改案件優先度。 |
| `claimTicket()` | 接手案件。 |
| `messages()`／`send()` | 查詢案件回覆與傳送文字回覆。 |
| `sendTicketImages()` | 傳送含圖片附件的案件回覆。 |
| `ticketImage()` | 取得案件圖片內容。 |
| `origins()` | 查詢轉入案件的原始聊天室。 |
| `closeTicket()` | 結案。 |

### 案件 Entity 與資料表

| 程式檔 | 對應資料表／用途 |
| --- | --- |
| `entity/CustomerServiceTicket.java` | `customer_service_tickets`：案件主資料。 |
| `entity/CustomerServiceCategory.java` | `customer_service_categories`：案件分類。 |
| `entity/CustomerServiceMessage.java` | `customer_service_messages`：案件回覆。 |
| `entity/CustomerServiceAttachment.java` | `customer_service_attachments`：圖片附件。 |
| `entity/CustomerServiceTicketGame.java` | `customer_service_ticket_games`：案件與多款遊戲的關聯。 |
| `entity/CustomerServiceTicketGameId.java` | 案件編號＋遊戲編號的複合主鍵，不是另一張資料表。 |

## 4. 客服通知與 SSE 即時更新

| 類型 | 程式檔 | 用途 |
| --- | --- | --- |
| 前端 | `components/GlobalNotificationBell.vue` | 通知鈴鐺、未讀數量、單筆／全部已讀，以及跳轉至相關頁面。 |
| 前端 | `api.js` | 建立 SSE 連線、斷線重連及發出 `support-refresh` 事件。 |
| 前端 | `App.vue` | 掛載通知鈴鐺。 |
| 後端 | `controller/NotificationController.java` | 提供通知列表、單筆已讀與全部已讀 API。 |
| 後端 | `service/NotificationService.java` | 建立通知、決定收件者、查詢通知與更新已讀狀態。 |
| 後端 | `controller/SupportController.java` | 提供 SSE 事件連線 API。 |
| 後端 | `model/EventHub.java` | 管理 SSE 連線、送出更新訊號、心跳與斷線清理。 |
| 後端 | `service/SupportService.java` | 在聊天、回覆、接手與轉案等操作完成後觸發通知及即時更新。 |

### 主要方法與更新流程

| 程式檔／方法 | 用途 |
| --- | --- |
| `api.js` → `connectEvents()` | 建立 SSE 連線，收到更新後發出 `support-refresh`。 |
| `api.js` → `stopEvents()` | 停止 SSE 連線。 |
| `EventHub.java` → `subscribe()` | 建立並登記 SSE 連線。 |
| `EventHub.java` → `changed()` | 通知相關會員與客服重新查詢資料。 |
| `EventHub.java` → `notification()` | 通知指定收件者更新通知列表。 |
| `EventHub.java` → `heartbeat()` | 定期心跳，檢查連線及登入憑證。 |
| `NotificationService.java` → `list()` | 查詢通知與未讀數量。 |
| `NotificationService.java` → `read()`／`readAll()` | 標記單筆／全部通知已讀。 |
| `NotificationService.java` → `create()` | 寫入通知並觸發更新訊號。 |

SSE 傳送的是「資料已變更」的訊號。前端收到後，由各頁面的 `support-refresh` 監聽器重新呼叫 API 取得最新內容；聊天內容仍透過有權限檢查的 API 讀取。

## 四項功能共用的程式

| 程式檔 | 用途 |
| --- | --- |
| `controller/SupportController.java` | 工作總覽、即時聊天與案件共用的 API 入口。 |
| `service/SupportService.java` | 客服核心邏輯；主要使用 `JdbcTemplate` 執行 SQL 查詢與寫入。 |
| `model/Actor.java` | 目前登入者的身分及客服操作權限判斷。 |
| `model/Models.java` | API 的聊天室、案件、訊息、附件與輸入資料格式。 |
| `model/Problem.java` | 統一客服錯誤回應。 |
| 前端 `api.js` | 共用 API 請求、登入狀態與 SSE。 |
| 前端 `permissions.js` | 前端角色判斷與頁面存取規則。 |
| 前端 `components/ManagementSidebar.vue` | 客服總覽、即時客服與案件的側邊導覽。 |
| 前端 `components/UiIcon.vue` | 共用圖示。 |

Entity 是資料表的 JPA 對應類別，目前主要的資料存取實作仍在 `SupportService.java` 與 `NotificationService.java`。

## 登入與執行設定

本版保留獨立客服登入所需的 `AuthController.java`、`FrontAccountService.java`、`JwtService.java`、`SecurityConfig.java`，以及 `MemberLogin.vue`、`StaffLogin.vue`、`LoginForm.vue`。

啟動、資料庫與本機設定請沿用原壓縮檔的操作說明。本文件只補充功能與程式對照。合併到小組專案時，仍需整合共用登入及安全設定，不能直接同時掛載兩套驗證。
