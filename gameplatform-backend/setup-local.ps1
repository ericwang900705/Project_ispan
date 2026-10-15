$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$localPath = Join-Path $PSScriptRoot 'config/local.properties'
if (-not (Test-Path $localPath)) { Copy-Item 'config/local.properties.example' $localPath }
Write-Host 'Edit config/local.properties for SQL Server; then run start.cmd. JWT uses the shared JwtTokenProvider.'
