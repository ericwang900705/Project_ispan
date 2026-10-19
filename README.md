# 遊戲平台修改紀錄、套用方式與測試帳號

適用基底：`Project_ispan-main(4).zip` 與 `Hero_SQL_server1017(2).txt`。

本文件記錄本次 SUPERADMIN 權限調整、後續編譯／測試修正、SQL 初始化及測試帳號。程式與 SQL 已提供檔案，仍需在實際專案與資料庫套用；提供檔案不等於已在你的電腦部署或執行。

## 這次修改了什麼

| 項目 | 調整前 | 本次調整後 |
| --- | --- | --- |
| 最高管理員 | 程式將 ADMIN 視為最高權限 | 新增獨立 SUPERADMIN 角色，ADMIN 改依個別功能授權使用 |
| 人員授權 | ADMIN 可管理人員與權限 | 只有 SUPERADMIN 能查看人員列表、新增後台人員、變更角色與功能授權 |
| 最高帳號保護 | 沒有獨立最高角色保護 | 角色選單與 API 不提供升任第二位 SUPERADMIN；最高帳號不能由人員管理 API 降級或修改功能授權 |
| 普通管理員權限 | ADMIN 自動具有完整功能 | 四項業務功能由 admin_user_permissions 明確授權；其他未定義個別權限的系統管理 API 限 SUPERADMIN |
| 角色變更 | 自動補上角色預設授權 | 改成清除舊功能授權，由最高管理員重新勾選；角色不變時不補回已撤銷授權 |
| 個別授權介面 | 人員管理頁只有角色選單 | 增加功能權限勾選與儲存按鈕；最高帳號顯示受保護及完整權限 |
| 後端驗證 | 原本有 ADMIN 直接通過的分支 | 功能 API 查詢目前授權、帳號啟用狀態與角色；授權修改在交易鎖內再次確認最高管理員 |
| 前端入口 | 選單與路由主要依角色判斷 | 遊戲與客服入口、路由依個別授權判斷，人員管理僅向 SUPERADMIN 開放 |
| 登入身分 | 前後端僅接受既有管理角色 | JWT、後台帳號載入、身分驗證、前端登入與還原均加入 SUPERADMIN |
| 一般人員主頁 | 依角色直接導入業務頁面 | 增加共用工作後台首頁，只顯示可使用的已串接功能；沒有授權時顯示說明 |
| 後台登入檢查 | 舊登入服務只特別排除 SUSPENDED | 改為只允許 ACTIVE 且已授權角色登入，PENDING 不能登入 |
| 通知收件人 | 部分通知依 ADMIN 等角色發送 | 客服與遊戲送審通知依最高角色或對應功能授權挑選收件人 |
| 角色與權限初始資料 | SQL 缺少程式使用的初始資料 | 補齊 SUPERADMIN、ADMIN、REVIEWER、SUPPORT、PENDING 與四個功能代碼 |
| 最高管理員帳號 | 需手動指定既有帳號升任 | 完整 SQL 第 10 段可直接建立 superadmin，密碼以 BCrypt 雜湊儲存 |
| 測試資料 | 未提供整組測試帳號 | 新增管理員、審核、客服、會員與發行商測試帳號 SQL |

最高帳號的單一身分限制由程式及初始化 SQL 維護，沒有新增資料庫唯一限制或觸發器。具備資料庫直接寫入權限的維護者仍可修改資料。

## 新增的檔案

下列程式路徑皆相對於專案最外層的兩個同名模組資料夾。

| 新增檔案 | 用途 |
| --- | --- |
| gameplatform-frontend/src/views/StaffHome.vue | 一般後台人員首頁，依授權顯示可使用的功能 |
| gameplatform-backend/src/test/java/gameplatform/staff/StaffAuthorizationTests.java | 17 項隔離授權測試，涵蓋角色限制、最高帳號保護、授權與撤銷等 |
| 測試帳號_管理員審核客服會員發行商.sql | 建立七個測試帳號、登入資料與新後台人員的功能授權 |
| README_測試帳號.md | 本文件，整合變更、檔案、套用順序及測試帳號 |

修補包另含 `README_套用說明.md`、`變更檔案清單.txt`、`SQL/01_list_staff.sql` 與 `SQL/02_enable_superadmin.sql`。其中原版 `02_enable_superadmin.sql` 是指定既有帳號升任的做法；本次已另外更新完整 SQL 第 10 段，直接建立 superadmin。現在請依本文件使用更新後的完整 SQL，不需再執行舊的帳號指定流程。

## 修改的後端檔案

以下路徑皆位於 `gameplatform-backend/src/main/java/gameplatform/`。

| 檔案 | 修改內容 |
| --- | --- |
| staff/StaffService.java | 限最高管理員管理人員與授權；變更角色清除授權；保護最高帳號；移除原本自動建立 ADMIN 的 bootstrap 方法 |
| staff/StaffController.java | 新增人員入口限定最高管理員，調整角色變更與待授權提示 |
| admin/service/AdminService.java | ACTIVE 與已授權角色檢查，接受 SUPERADMIN 登入 |
| admin/service/AdminPublisherService.java | 審核通知寄信回呼改成 static 巢狀類別，透過建構子傳入 MailService |
| support/model/Actor.java | 新增 isSuperAdmin() 與最高角色權限身分；一般 ADMIN 不再繼承審核與客服 authority |
| support/config/SecurityConfig.java | 後台管理僅限最高角色，業務 API 改用功能權限判斷，後台登入加入 SUPERADMIN |
| support/config/StaffPermissionService.java | SUPERADMIN 完整功能、其他人員即時查詢個別授權；補上最高管理員判斷；修正三個 Lambda 的參數相容性 |
| support/config/SupportActorResolver.java | 允許 SUPERADMIN 身分，保留即時帳號與角色核對 |
| support/service/JwtService.java | JWT 身分解析加入 SUPERADMIN |
| support/service/NotificationService.java | 客服與遊戲審核通知收件人依個別授權判斷，加入最高角色通知類型 |

`MailService.java` 本次沒有修改。實際寄信仍由既有 MailService 負責。

## 修改的前端檔案

以下路徑皆位於 `gameplatform-frontend/src/`。

| 檔案 | 修改內容 |
| --- | --- |
| App.vue | 共用後台畫面判斷加入 SUPERADMIN |
| StaffApp.vue | 加入最高角色身分與畫面文字 |
| permissions.js | 新增功能代碼、角色可選權限、hasPermission 判斷；修改主頁及路由守衛 |
| api.js | 後台登入與會話還原接受 SUPERADMIN；提供刷新登入者與授權資料的方法 |
| main.js | 新增 /staff/home 路由；後台切換頁面時刷新授權，刷新失敗則清除登入 |
| views/PlatformDashboard.vue | 平台統計儀表板限定最高管理員載入 |
| views/StaffManagement.vue | 個別功能權限勾選、儲存授權、最高角色唯讀顯示及授權提示 |
| views/StaffLogin.vue | 更新最高角色、授權與登入後主頁說明 |
| views/StaffRegister.vue | 更新等待最高管理員授權的提示 |
| components/ManagementSidebar.vue | 人員管理只限最高角色；遊戲與客服等選單依個別授權顯示 |

## SQL 修改與新增資料

### 完整 SQL：Hero_SQL_server1017(2).txt

- 保留原本建庫內容及資料表結構，新增開頭的執行方式說明。
- 檔案最後新增「第 10 段：初始化權限與建立最高管理員」，可在既有資料庫獨立執行。
- 補齊五個角色與 GAME_REVIEW、PUBLISHER_REVIEW、MEMBER_MANAGE、SUPPORT_CASES 四個功能權限代碼。
- 建立最高管理員 superadmin，初始密碼改為 Hero12345678，資料庫儲存 BCrypt 雜湊。
- 重複執行保留原密碼；遇到其他最高帳號或同名一般帳號時中止，不自動覆寫或升權。
- 使用交易、錯誤回滾與人員管理應用程式鎖，避免重複初始化與部分寫入。

### 測試帳號 SQL

- 新增 admin01、reviewer01、support01、member01、member02、publisher01、publisher02。
- 只替本次新建立的後台人員寫入個別功能授權，授權者指向目前啟用的最高管理員。
- 只替新建立的會員與發行商新增 password_history 與已驗證的 member_verifications 紀錄。
- 發行商以既有 is_publisher 欄位建立測試身分，附測試公司名稱與統編。
- 不覆寫既有帳號、不重設密碼、不補回已撤銷的授權；信箱衝突時整筆回滾。

`admin_roles`、`admin_permissions`、`admin_user_permissions`、`password_history` 等資料表原本就已存在。本次新增的是角色、帳號與授權資料，不是重新建立這些表。

## 後續編譯與測試修正

原始修補包發布後，另提供以下三個修正版。**請先套用修補包，再以最新版單檔覆蓋，避免舊檔蓋回來。**

| 修正版 | 發現的問題 | 已提供的調整 |
| --- | --- | --- |
| AdminPublisherService.java | 編輯器顯示 No enclosing instance of the type AdminPublisherService | 將匿名交易回呼換成 static 巢狀類別，明確傳入 MailService，保留交易成功後才寄信的行為 |
| StaffPermissionService.java | 三處 Lambda 出現 incompatible parameter types | 移除明確寫死的 Supplier<Authentication> 參數型別，改由 AuthorizationManager 推導 |
| StaffAuthorizationTests.java | 單元測試出現 No DataSource set | FakeDb 補上沒有綁定參數的 queryForObject / queryForList 多載，保留全部 17 項授權測試 |

修正編輯器外層實例引用問題的檔案已提供；沒有足夠證據將該錯誤歸因於 Java 25 本身。專案 `pom.xml` 的 Java 25 設定維持不變。

## 完整套用順序

1. 停止舊版後端，備份目前專案與資料庫。
2. 解壓 `Project_ispan_SUPERADMIN_修補包_20261019.zip`，將後端與前端資料夾合併覆蓋到同名模組；保留本機連線、密碼與郵件設定。
3. 再覆蓋上述三個「後續修正版」。前兩個放入對應的 `src/main/java` 路徑；測試檔放入 `src/test/java/gameplatform/staff/`。
4. 已有資料庫只執行更新後完整 SQL 的第 10 段；資料庫不存在時才從頭建庫。
5. 執行測試帳號 SQL，確認結果顯示新增或保留既有帳號。
6. 在後端目錄，以 Java 25 執行 `mvnw.cmd clean test`；成功後啟動後端與前端，再依下方帳號登入。

Windows PowerShell 指令：

```powershell
.\mvnw.cmd clean test
```

SQL 詳細執行方式及帳號資料如下。

## 帳號與初始密碼

下表為 SQL「新建立」帳號的設定。若帳號已存在，SQL 會保留原本的密碼與資料。

**初始密碼統一為：`Hero12345678`**

| 身分 | 登入帳號 | 初始密碼 | 登入入口 |
| --- | --- | --- | --- |
| 最高管理員 | `superadmin` | `Hero12345678` | 後台登入 |
| 管理員 | `admin01` | `Hero12345678` | 後台登入 |
| 審核人員 | `reviewer01` | `Hero12345678` | 後台登入 |
| 客服人員 | `support01` | `Hero12345678` | 後台登入 |
| 會員一 | `member01` | `Hero12345678` | 會員登入 |
| 會員二 | `member02` | `Hero12345678` | 會員登入 |
| 發行商一 | `publisher01` | `Hero12345678` | 會員登入 |
| 發行商二 | `publisher02` | `Hero12345678` | 會員登入 |

最高管理員由完整 SQL 的第 10 段建立；其他七個帳號由測試帳號 SQL 建立。

## SQL 執行順序

### 1. 建立最高管理員

使用更新後的 `Hero_SQL_server1017(2).txt`。

- **已有 GamePlatformDB：只選取最後「第 10 段」到檔案結尾執行，不要從頭重跑。**
- 全新環境且資料庫尚不存在：才可從頭執行整份建庫 SQL。

第 10 段會初始化角色與四個功能權限，並建立 `superadmin`。

重複執行不會重設最高管理員的密碼。若已有其他最高管理員，或 `superadmin` 名稱已被一般帳號使用，會停止新增。

### 2. 建立其他測試帳號

在 SSMS 執行整份：

`測試帳號_管理員審核客服會員發行商.sql`

此檔需先有且只有一位啟用的 `SUPERADMIN`，會建立：

- 三個後台帳號與個別功能授權。
- 兩個會員帳號。
- 兩個發行商帳號。
- 新會員與新發行商的密碼歷史及已完成信箱驗證紀錄。

此檔不修改資料表結構，不需要重建資料庫。

### 3. 啟動專案並登入

後端使用專案要求的 **Java 25**，並套用 SUPERADMIN 修補版及後續修正檔。啟動前後端後，使用對應登入入口。

| 帳號類型 | 前端 Hash 路由 |
| --- | --- |
| 最高管理員、管理員、審核、客服 | `#/staff/login` |
| 會員、發行商 | `#/member/login` |

實際網址需加上你目前使用的前端主機與連接埠。會員與後台若分開啟動，請使用對應前端的網址。

## 後台功能權限

| 身分 | 初始可用功能 | 可授權他人 |
| --- | --- | --- |
| SUPERADMIN | 完整管理功能、人員與權限管理 | 可以 |
| ADMIN | 遊戲審核、發行商審核、會員管理、客服案件 | 不可以 |
| REVIEWER | 遊戲審核、發行商審核、會員管理 | 不可以 |
| SUPPORT | 客服聊天室與案件 | 不可以 |

上表的一般人員權限只會授予「本次 SQL 新建立」的帳號。最高管理員可在後台逐一修改授權。

一般 `ADMIN` 不再自動擁有所有功能，而是依 `admin_user_permissions` 的授權資料使用功能。只有 `SUPERADMIN` 能修改人員角色與功能權限。

會員管理與發行商審核目前已有後端授權；對應前端業務頁面仍待串接。取得權限不代表這些尚未完成的頁面已可操作。

## 會員與發行商資料

- 會員與發行商共用 `members` 表，以 `is_publisher` 區分。
- 登入密碼以 BCrypt 雜湊存放於 `password_history`，不是明文密碼。
- 新測試帳號已建立信箱驗證完成紀錄，不需收取驗證信。
- 測試信箱使用 `hero.example` 保留網域，無法實際收信。
- 發行商直接建立為測試身分，不建立假的申請文件或審核歷程；這不代表正式申請流程已通過測試。
- 不額外建立遊戲、訂單或購買紀錄。

## 重複執行的行為

- 同名帳號已存在：保留原角色、密碼、狀態及資料。
- 不替既有帳號解除鎖定或補回已撤銷的權限。
- 新帳號預定信箱已被其他帳號使用：整筆交易回滾，請依錯誤訊息修改測試信箱。
- 執行結果會顯示「本次新增」或「已存在，未變更」。只有「本次新增」者可直接依本文件的初始密碼登入。

## 建議操作確認

1. 使用 `superadmin` 登入，確認能開啟人員與權限管理。
2. 使用 `admin01` 登入，確認能使用已授權功能，但不能修改別人的權限。
3. 使用 `reviewer01`、`support01` 分別確認審核與客服的功能限制。
4. 使用 `member01`、`member02` 從會員入口登入，可用來測試雙方會員互動。
5. 使用 `publisher01`、`publisher02` 從會員入口登入，再開啟發行商工作區，可用來確認不同發行商的資料隔離。

SQL 已依目前資料表與登入程式整理，尚未連接你的 SQL Server 實際執行或驗證登入。


## 本次未新增或未完成的部分

- 未增加一般後台人員停用／刪除 API；最高帳號保護不代表新增了這些管理功能。
- 發行商申請、審核與歷程的前端業務頁面仍待串接；會員管理的對應頁面也未在本次補齊。
- `publisher_applications.document_path` 仍保留原本 NOT NULL 設定。先前發現的「申請程式可能寫入 NULL」問題尚未在本次 SQL 中調整，正式測試申請流程前仍需處理。
- 舊發行商遊戲 API 封鎖、申請歷程後端等三項整合內容，是基底專案已有的功能，本次未將它們列為新做完的功能。
- 未新增點數商店、內容檢舉、第三方帳號綁定、AI 標籤或遊玩紀錄等其他業務流程。
- 未變更 Java 25 版本要求、資料庫連線密碼、郵件帳密或金流設定，也未將程式推送到 GitHub。

## 驗證與目前限制

已完成的檢查：

- 前端 56 個 Vue 元件的解析與模板編譯、14 個 JS 檔案的語法解析。
- 27 項前端角色、功能授權與路由守衛檢查。
- 從授權服務取出的 SELECT 查詢，以 SQLite 測試資料確認 8 項授權與撤銷情況。這不是 SQL Server 實測。
- 帳號初始密碼與 BCrypt 雜湊的配對檢查。

尚未確認完成的檢查：

- 此工作環境缺少 Java 25 開發工具，未在此完成整個專案的 Java 25 編譯與 Maven 測試。
- 你提供的最近一次紀錄已進到測試階段，但當時 FakeDb 多載問題造成失敗；修正版已提供，尚未收到修正後全部測試通過的紀錄。
- 前端 production build 因原 ZIP 缺少此 Linux 環境的 Rolldown 原生套件而未完成；不等於已驗證完整打包成功。
- 未連接你的 SQL Server 執行初始化或新增帳號，也未完成前後端實際登入及授權的端到端測試。
