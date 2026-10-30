# 使用固定 ngrok 網址啟動後端；不包含 ngrok 私人 Token。
param([string]$PublicUrl = 'https://reassign-shelve-pleat.ngrok-free.dev')
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'Start-Backend-Lan.ps1') -LanUrl $PublicUrl
