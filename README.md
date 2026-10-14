# HERO 客服模組

此版本只保留客服功能，供 customer_service 分支使用。整理基準為 2026-10-14 更新的 HERO GitHub 串接版。

## 保留範圍

- 客服工作總覽：待接手、自己負責、其他客服負責的聊天與案件。
- 即時客服：會員右下角客服視窗、歷史訊息、接手、關閉、轉正式案件。
- 客服案件：搜尋、分類、優先度、案件內容、回覆、圖片附件、多遊戲關聯、結案。
- 客服通知與 SSE 即時更新。
- 必要共用程式：JWT、登入／登出、身分驗證、API 封裝、樣式、啟動與建置設定。

## 已移除的組員功能

| GitHub 分支 | 核對版本 | 移除的功能 |
| --- | --- | --- |
| member/friend | be7d347da7976e1c0ee4b660c40ce8b4d73b603f | 會員註冊／驗證信／密碼重設、個人資料、頭像、好友、好友聊天室、會員停復權與發行商申請審核 |
| cart | 11d6653c77bdcd218cec35370f90317e8b65405f | 商城、購物車、優惠券、訂單、綠界金流、遊戲庫、玩家評論、點數商店 |
| game/admin | b23906c036b9547c90af5508c2ffabce9d472acd | 遊戲管理、遊戲媒體、發行商遊戲／版本管理、上下架審核、遊戲搜尋後台與推薦模組 |
| 人員權限管理（依本次確認） | 本機整合內容 | 人員列表、新增人員、後台註冊、角色分派、首位管理員自動建立及相關 API |

參考倉庫：https://github.com/ericwang900705/Project_ispan
核對時 customer_service 分支為 2dc984fd4a881a2acbb87959af02e1ef152ee2a8；該分支已含其他模組，所以本次按功能與相依清理，並非直接保留整個分支。

## 為何還有登入與遊戲查詢

客服需要辨識會員與處理人員。MemberLogin、FrontAccountService 只提供沿用既有帳號的客服登入轉接；不包含會員建立、個資修改、寄信或好友功能。GET /api/games 只讀取案件選擇遊戲所需的 ID／名稱，不提供遊戲維護或商城功能。

目前 JWT 是原客服版的統一驗證。與 member/friend 的 JwtTokenProvider 並非直接相容；合併小組專案時需由共用登入將驗證身分轉為 Actor，並整合為單一 SecurityConfig，不要同時套入兩套驗證設定。

## 資料庫與帳號

沿用小組既有 SQL Server 資料庫。必要外部表為 members、password_history、member_verifications、games、admin、admin_roles；不附這些組員資料表的建庫程式。會員登入仍會更新既有 failed_login_count、locked_until。

在 SSMS 選擇正確資料庫，備份後執行 gameplatform-backend/sql/01_support.sql。它只建立缺少的客服表、客服通知表與案件流水號，補案件遊戲關聯／優先度及分類。不建立資料庫，不刪除資料，也不建立後台人員或變更人員權限。

使用小組既有帳號。此版本沒有預設 admin 帳密，也不會新增、重設帳號。後台資料中的有效角色 ADMIN 或 SUPPORT 可使用客服；REVIEWER 沒有客服權限。會員 MEMBER 與發行商 PUBLISHER 都可使用會員端客服。若小組使用 SUPER_ADMIN 等其他角色代碼，須先由負責共用登入／權限的組員確認映射，不要任意改動資料庫角色。

## 本機啟動

需求：JDK 25、Node.js 22.12 以上、SQL Server；沿用原專案 Spring Boot 4.1.1。

1. 進入 gameplatform-backend，執行 PowerShell：`powershell -ExecutionPolicy Bypass -File setup-local.ps1`。
2. 編輯 config/local.properties，填自己的 SQL Server 連線／帳密。腳本會產生本機 JWT 金鑰；此檔不應提交 GitHub。
3. 後端執行 start.cmd；或 `mvnw.cmd spring-boot:run`。
4. 進入 gameplatform-frontend，執行 `npm ci`，再執行 `npm run dev`。
5. 後台客服登入：http://localhost:5173/#/staff/login 。會員端客服登入：http://localhost:5173/#/member/login 。

如要由後端提供前端網頁，先在前端執行 `npm run build:backend`，再啟動後端並開啟 http://localhost:8080/ 。前端產物由使用者本機建置，不放入 GitHub 原始碼。

## 上傳 GitHub

解壓後，以資料夾內的 README.md、.gitignore、gameplatform-backend、gameplatform-frontend 作為專案根目錄內容。

在自己的 customer_service 工作分支內以此版本替換舊檔時，記得同步刪除舊版的 member、friend、game、shop、staff、src/cart 與舊的發行商／審核頁面；單純覆蓋新增檔案不會刪掉原有檔案。先提交或備份本機未提交的修改，再用 git status 核對刪除清單。

請勿將此客服精簡版整包覆蓋至組員分支或整合用 main。這份交付未對遠端 GitHub 執行提交或刪除。

.gitignore 已排除本機帳密、.env、node_modules、dist、target、uploads 與產生的後端 static 目錄。

## 驗證

前端正式建置與 6 項測試通過；JDK 25 後端重新編譯與 9 項測試通過。測試涵蓋 JWT、登入身分分離、重整還原、過期／登出、帳號切換隔離、客服轉案多遊戲選擇、搜尋回應順序及客服路由權限。此環境未連線至小組 SQL Server，登入、查詢、交易與通知的資料庫實際操作仍需在小組環境測試。SQL 不會由程式自動執行。
