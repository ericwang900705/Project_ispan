$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$localPath = Join-Path $PSScriptRoot 'config/local.properties'
if (-not (Test-Path $localPath)) { Copy-Item 'config/local.properties.example' $localPath }
$content = [IO.File]::ReadAllText($localPath)
if ($content -match '(?m)^app\.jwt\.secret=\s*$') {
    $keyBytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($keyBytes)
    $rng.Dispose()
    $key = [Convert]::ToBase64String($keyBytes)
    $content = [regex]::Replace($content, '(?m)^app\.jwt\.secret=\s*$', "app.jwt.secret=$key")
    [IO.File]::WriteAllText($localPath, $content, (New-Object System.Text.UTF8Encoding($false)))
}
Write-Host 'Local config is ready. Edit config/local.properties for SQL Server; then run start.cmd.'
