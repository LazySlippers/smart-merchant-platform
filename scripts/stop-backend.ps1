$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $projectRoot "runtime-logs\backend\processes.json"
. (Join-Path $PSScriptRoot "backend-processes.ps1")
Stop-ProjectBackendProcess -BackendRoot (Join-Path $projectRoot "backend")
if (Test-Path -LiteralPath $pidFile) { Remove-Item -LiteralPath $pidFile -Force }
