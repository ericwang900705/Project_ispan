# HERO 同 Wi-Fi Nginx 部署

本更新以 Project_ispan(10).zip 為基礎。網站入口：http://192.168.36.122
前台：http://192.168.36.122/#/member/login
後台：http://192.168.36.122/#/staff/login

## 修改內容
- 同一個 Nginx 網站提供前後台；保留各自登入、路由與 API 權限。
- API 使用現有 /api，好友聊天室使用現有 /ws/friends；不改成跨網域 API。
- Nginx 新增 API、上傳與 WebSocket 轉發，客服 /api/events 關閉緩衝。
- 新增 application-lan.properties：LAN 網址、好友來源白名單、會員驗證連結及付款返回設定。
- HTTP 區域網路補上 UUID v4 相容方法，遊戲頁、付款分頁與時數同步都可使用；仍使用 crypto.getRandomValues，不使用 Math.random。
- SQL 02、03 的示範素材改成網站相對路徑。遊戲庫下載與商品頁預覽支援這些相對路徑；未修改畫面版型。
- HTTP 入口 Google 按鈕顯示需要 HTTPS 的說明，帳號密碼登入照常。
- 未新增資料表，不需重建資料庫；既有資料只需視情況執行 SQL/06_Hero_LAN_Asset_URLs.sql。

## 1. 覆蓋更新檔
將本包 gameplatform-frontend、gameplatform-backend、SQL、deploy 放到原專題相同層級，覆蓋同名檔案。
保留你原本的 uploads、ai-game-assets、資料庫連線與密鑰設定。這是更新包，不含整個專題或 Nginx 安裝檔。

## 2. 下載 Nginx
從官方 https://nginx.org/en/download.html 下載 Windows ZIP，解壓至 C:\nginx。
確認 C:\nginx\nginx.exe 與 C:\nginx\conf\mime.types 都存在。
可使用其他路徑，後續加 -NginxHome '你的路徑'。

## 3. 建置並部署前端
需要你原本可執行專題的 Node.js（至少 22.12），並可連線下載 npm 套件。
在專題根目錄（看得到前後端與 deploy）開 PowerShell，執行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\nginx-lan\Install-Lan.ps1
```

這會執行 npm ci 及 npm run build（合併版，非 build:member 或 build:staff），放到 C:\nginx\html\hero，檢查 Nginx 設定並啟動／重新載入。
腳本會備份原 nginx.conf，既有 html/hero 也會另存備份；若這台 Nginx 已提供其他專題，請先自行合併設定，避免替換原站點。
這個部署方式不使用 5173、5174，也不需要 npm run dev；其他電腦只開瀏覽器。

## 4. 啟動後端
先啟動 SQL Server，停止原本佔用 8080 的後端，在同一專題根目錄另一個 PowerShell 執行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\nginx-lan\Start-Backend-Lan.ps1
```

會從 gameplatform-backend 目錄執行 .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=lan。
保持視窗開啟，使用原本 Java 25 與資料庫設定，不使用 H2。
若使用 VS Code 啟動，需將 Spring profile 設為 lan；不要同時啟動兩個後端。
後端只監聽 127.0.0.1:8080，Nginx 對外監聽 80。內部 127.0.0.1 是正確設定，不需改成 Wi-Fi IP。

## 5. 允許同 Wi-Fi 連入
以系統管理員開 PowerShell，切換至專題根目錄：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\nginx-lan\Allow-Lan-Firewall.ps1
```

只允許同子網路透過指定 nginx.exe 存取 TCP 80，不開放 SQL Server 或 Java 8080。
不需要關閉整個防火牆。若教室不允許修改防火牆或有 Wi-Fi 裝置隔離，請老師協助。
移除本規則：Remove-NetFirewallRule -Name HERO-Nginx-LAN-80

## 6. 既有資料庫素材
如果已經有遊戲資料、封面與 ZIP 還指向 localhost:5173 或 127.0.0.1:5173：
備份資料庫後，只執行 SQL/06_Hero_LAN_Asset_URLs.sql。可重跑，不修改售價、帳號、訂單或退款金額。
它只移除 games.cover_url、game_media.media_url、game_builds.file_url 的本機前端網址前綴。
如果從零建庫，沿用原本 01～05 的流程，改用本包新版 02、03；06 可略過。時數表仍可由原後端自動初始化。
ai-game-assets 請保留在與 gameplatform-backend 相同層，uploads 保留於後端原位置。

## 7. 開啟測試
先在主機開 http://192.168.36.122，再用其他同 Wi-Fi 電腦開相同網址。
建議依序測試帳密登入、商店封面、遊戲庫下載與遊玩、客服雙向對話、好友聊天。
付款流程仍保留真正的後端驗證，沒有因 LAN 模式直接將訂單改成已付款。

## Google 與綠界限制
- 這是教室內 HTTP 入口，不是公開網站。
- Google 身分驗證：目前專題使用 Google Identity Services popup ID token（google-identity.js），不是自訂 OAuth callback 流程。HTTP 私人 IP 無法直接當成正常 HTTPS 網站使用，需要日後配置 HTTPS 網域並加入 Google 授權 JavaScript 來源；本包不繞過 Google 限制。
- 綠界：使用者瀏覽器可從教室返回此 IP，但綠界伺服器無法呼叫 192.168.36.122 的通知網址。LAN profile 的網址設定只確保瀏覽器返回位置正確，不能代替公網通知通道。要完整驗證付款通知，仍需公開 HTTPS 入口。

## 常見狀況
- 無法連線：確認 ipconfig 的 Wi-Fi IPv4 仍為 192.168.36.122，其他電腦連同個 Wi-Fi，防火牆規則已建立，教室網路未隔離。
- 502：後端未啟動、啟動失敗或 8080 不符；先查後端終端機。
- 顯示 Nginx 歡迎頁：沒套用本設定，或未重新載入；請確認執行的是同一個 nginx.exe。
- 80 埠被其他服務占用：先確認占用服務，不要直接終止未知服務；若改用 8081，Nginx listen、網址、後端 LanUrl 與防火牆埠需一起修改。
- IP 變了：以 -LanUrl 'http://新的IP' 啟動後端，瀏覽器使用新 IP；nginx.conf 的 server_name 也更新後 nginx -t、nginx -s reload。網址不保證永久不變，需由教室網管提供 DHCP 保留才固定。
- 改完前端：重新執行 Install-Lan.ps1，再重新整理網頁。
- 遊戲時數：不同 origin 的瀏覽器暫存不共用；localhost 的未同步批次請先在舊入口完成同步。

## 驗證範圍
前端正式建置通過。以實際 Linux Nginx 配合隔離的模擬後端測試路徑、Authorization、Host、查詢、PUT 內容、素材轉發、404、SSE 與 WebSocket upgrade。
瀏覽器測試 HTTP 非安全來源登入、Google 說明、UUID fallback（500 筆）、時數同步批次、前後台標題及無頁面執行錯誤。
測試期間僅將 Nginx 監聽埠改成 8098，並指定測試環境的執行帳號及暫存目錄；部署設定使用 Windows Nginx 的 80 埠。
未在你的 Windows、教室 Wi-Fi 或 SQL Server 實際部署，未驗證真實會員聊天與綠界付款通知。PowerShell 腳本已檢閱，但未在 Windows 執行。

官方操作參考：https://nginx.org/en/docs/windows.html
WebSocket 轉發參考：https://nginx.org/en/docs/http/websocket.html
