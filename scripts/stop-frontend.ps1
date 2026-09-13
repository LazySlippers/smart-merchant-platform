$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $projectRoot "runtime-logs\frontend\processes.json"

if (-not (Test-Path $pidFile)) {
    Write-Host "No frontend PID file found; nothing started by start-frontend.ps1 to stop."
    exit 0
}

$items = @(Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json)
foreach ($item in $items) {
    $process = Get-Process -Id $item.Pid -ErrorAction SilentlyContinue
    if ($process) {
        & taskkill.exe /PID $item.Pid /T /F | Out-Null
        Write-Host "Stopped $($item.Name) (PID $($item.Pid))"
    }
}
Remove-Item -LiteralPath $pidFile -Force
