param([int]$StartupTimeoutSeconds = 60)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = Join-Path $projectRoot "frontend"
$logRoot = Join-Path $projectRoot "runtime-logs\frontend"
$pidFile = Join-Path $logRoot "processes.json"
$viteCli = Join-Path $frontendRoot "node_modules\vite\bin\vite.js"

New-Item -ItemType Directory -Path $logRoot -Force | Out-Null

if (-not (Get-Command pnpm -ErrorAction SilentlyContinue)) {
    throw "pnpm is not available. Run 'corepack enable pnpm' first."
}

if (-not (Test-Path (Join-Path $frontendRoot "node_modules"))) {
    Write-Host "[1/3] Installing frontend dependencies..." -ForegroundColor Cyan
    Push-Location $frontendRoot
    try {
        & pnpm install
        if ($LASTEXITCODE -ne 0) { throw "Frontend dependency installation failed." }
    } finally {
        Pop-Location
    }
} else {
    Write-Host "[1/3] Frontend dependencies already installed." -ForegroundColor DarkGray
}

$apps = @(
    @{ Name="platform-web"; Port=5173 },
    @{ Name="merchant-web"; Port=5174 },
    @{ Name="store-web"; Port=5176 },
    @{ Name="consumer-web"; Port=5177 }
)

function Test-Frontend([int]$port) {
    try {
        $response = Invoke-WebRequest -Uri "http://127.0.0.1:$port" -UseBasicParsing -TimeoutSec 2
        return $response.StatusCode -ge 200 -and $response.StatusCode -lt 500
    } catch { return $false }
}

Write-Host "[2/3] Starting frontend applications..." -ForegroundColor Cyan
$started = @()
foreach ($app in $apps) {
    if (Test-Frontend $app.Port) {
        Write-Host "  $($app.Name) already running on $($app.Port)" -ForegroundColor Yellow
        continue
    }

    $appRoot = Join-Path $frontendRoot "apps\$($app.Name)"
    $process = Start-Process node.exe -ArgumentList @($viteCli, "--port", [string]$app.Port, "--host", "127.0.0.1") -WorkingDirectory $appRoot `
        -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $logRoot "$($app.Name).out.log") `
        -RedirectStandardError (Join-Path $logRoot "$($app.Name).err.log")
    $started += [pscustomobject]@{ Name=$app.Name; Port=$app.Port; Pid=$process.Id }
    Write-Host "  started $($app.Name) (PID $($process.Id))"
}

$started | ConvertTo-Json | Set-Content -LiteralPath $pidFile -Encoding UTF8

Write-Host "[3/3] Waiting for frontend applications..." -ForegroundColor Cyan
$deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
$pending = @($apps)
do {
    $pending = @($pending | Where-Object { -not (Test-Frontend $_.Port) })
    if ($pending.Count -eq 0) { break }
    Start-Sleep -Seconds 1
} while ((Get-Date) -lt $deadline)

if ($pending.Count -gt 0) {
    $names = ($pending | ForEach-Object { "$($_.Name):$($_.Port)" }) -join ", "
    throw "Frontend startup failed: $names. Check $logRoot"
}

Write-Host "All frontend applications are ready." -ForegroundColor Green
Write-Host "Platform:  http://localhost:5173"
Write-Host "Merchant:  http://localhost:5174"
Write-Host "Store:     http://localhost:5176"
Write-Host "Consumer:  http://localhost:5177/"
Write-Host "Logs:      $logRoot"
