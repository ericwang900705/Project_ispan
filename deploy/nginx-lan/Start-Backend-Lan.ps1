# 從後端目錄啟動，維持 uploads 及 ai-game-assets 的原有相對位置。
param([string]$LanUrl = 'http://192.168.36.122')
$ErrorActionPreference = 'Stop'
$uri = $null
if (![Uri]::TryCreate($LanUrl, [UriKind]::Absolute, [ref]$uri) -or $uri.Scheme -notin @('http', 'https') -or $uri.AbsolutePath -ne '/' -or $uri.Query -or $uri.Fragment -or $uri.UserInfo) { throw 'LanUrl 請填 http://IP 或 https://公開網域，不要加路徑。' }
$env:HERO_LAN_URL = $LanUrl.TrimEnd('/')
$backend = Join-Path $PSScriptRoot '..\..\gameplatform-backend'
Push-Location $backend
try {
    & .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=lan'
    if ($LASTEXITCODE -ne 0) { throw '後端啟動失敗，請查看上方錯誤。' }
} finally { Pop-Location }
