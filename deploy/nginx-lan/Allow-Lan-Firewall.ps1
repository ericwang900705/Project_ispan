# 管理員 PowerShell 執行；僅允許同子網路連入 Nginx 的 TCP 80，不開放 SQL Server 或 Java 8080。
param([string]$NginxHome = 'C:\nginx')
$ErrorActionPreference = 'Stop'
$program = (Resolve-Path (Join-Path $NginxHome 'nginx.exe')).Path
$name = 'HERO-Nginx-LAN-80'
if (Get-NetFirewallRule -Name $name -ErrorAction SilentlyContinue) {
    throw '規則已存在；若要變更 Nginx 路徑，先執行 Remove-NetFirewallRule -Name HERO-Nginx-LAN-80。'
}
New-NetFirewallRule -Name $name -DisplayName 'HERO Nginx 同 Wi-Fi 80' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 80 -RemoteAddress LocalSubnet -Program $program -Profile Any
