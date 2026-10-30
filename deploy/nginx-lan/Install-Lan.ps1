# 建置合併版前後台，部署至指定 Nginx；保留原 conf 備份。
param([string]$NginxHome = 'C:\nginx')
$ErrorActionPreference = 'Stop'
$nginxExe = Join-Path $NginxHome 'nginx.exe'
if (!(Test-Path $nginxExe)) { throw "找不到 $nginxExe，請先下載並解壓 Nginx，或指定 -NginxHome。" }
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$frontend = Join-Path $projectRoot 'gameplatform-frontend'
if (!(Test-Path (Join-Path $frontend 'package.json'))) { throw '請將 deploy 資料夾放在與 gameplatform-frontend、gameplatform-backend 相同的一層。' }
Push-Location $frontend
try {
    & npm.cmd ci --include=dev
    if ($LASTEXITCODE -ne 0) { throw 'npm ci 失敗。' }
    # 使用合併模式，會員及後台各自的權限檢查仍保留。
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw '前端建置失敗。' }
} finally { Pop-Location }
$conf = Join-Path $NginxHome 'conf'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$oldConf = Join-Path $conf 'nginx.conf'
$backup = Join-Path $conf "nginx.conf.hero-backup-$stamp"
if (Test-Path $oldConf) { Copy-Item $oldConf $backup }
Copy-Item (Join-Path $PSScriptRoot 'nginx.conf') $oldConf -Force
Copy-Item (Join-Path $PSScriptRoot 'hero-proxy.conf') (Join-Path $conf 'hero-proxy.conf') -Force
Push-Location $NginxHome
try {
    & $nginxExe -t
    if ($LASTEXITCODE -ne 0) {
        if (Test-Path $backup) { Copy-Item $backup $oldConf -Force }
        throw 'Nginx 設定檢查失敗，已還原原本 nginx.conf；請查看上方錯誤。'
    }
    $site = Join-Path $NginxHome 'html\hero'
    if (Test-Path $site) { Move-Item $site "$site.backup-$stamp" }
    New-Item -ItemType Directory -Path $site -Force | Out-Null
    Copy-Item (Join-Path $frontend 'dist\*') $site -Recurse -Force
    # reload 失敗可能是尚未啟動；首次部署時再啟動。
    & $nginxExe -s reload
    if ($LASTEXITCODE -ne 0) {
        $process = Start-Process -FilePath $nginxExe -WorkingDirectory $NginxHome -PassThru
        Start-Sleep -Milliseconds 500
        $process.Refresh()
        if ($process.HasExited -and $process.ExitCode -ne 0) { throw 'Nginx 啟動失敗，請查看 logs/error.log。' }
    }
} finally { Pop-Location }
Write-Host '部署完成。請啟動 lan profile 後端，再開 http://192.168.36.122'
