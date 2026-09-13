param(
    [switch]$SkipBuild,
    [switch]$EnableSimulatedPayment,
    [switch]$DisableSimulatedPayment,
    [int]$HealthTimeoutSeconds = 90
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$backendRoot = Join-Path $projectRoot "backend"
$logRoot = Join-Path $projectRoot "runtime-logs\backend"
$pidFile = Join-Path $logRoot "processes.json"
$envFile = Join-Path $projectRoot ".env"

New-Item -ItemType Directory -Path $logRoot -Force | Out-Null

if (-not (Test-Path $envFile)) {
    throw "Missing $envFile. Copy .env.example to .env first."
}

$settings = @{}
Get-Content -LiteralPath $envFile | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
        $settings[$matches[1].Trim()] = $matches[2].Trim()
    }
}

foreach ($required in @("MYSQL_APP_PASSWORD", "JWT_SECRET", "INTERNAL_SIGNING_KEY", "BOOTSTRAP_ADMIN_PASSWORD")) {
    if (-not $settings[$required]) { throw "Missing $required in .env" }
}

# Check dependencies before stopping healthy services or launching JVMs.
function Test-DependencyPort([string]$hostname, [int]$port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $connect = $client.ConnectAsync($hostname, $port)
        if (-not $connect.Wait(1000)) { return $false }
        return $client.Connected
    } catch { return $false }
    finally { $client.Dispose() }
}

Write-Host "Checking MySQL and Redis before backend startup..." -ForegroundColor Cyan
$dependencyDeadline = (Get-Date).AddSeconds($HealthTimeoutSeconds)
do {
    $missingDependencies = @()
    if (-not (Test-DependencyPort "127.0.0.1" 13306)) { $missingDependencies += "MySQL 127.0.0.1:13306" }
    if (-not (Test-DependencyPort "127.0.0.1" 6379)) { $missingDependencies += "Redis 127.0.0.1:6379" }
    if ($missingDependencies.Count -eq 0) { break }
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $dependencyDeadline)
if ($missingDependencies.Count -gt 0) {
    throw "Dependencies unavailable: $($missingDependencies -join ', '). Run docker compose --env-file .env -f deploy/compose/compose.yml up -d and wait for healthy containers, then retry. Backend services were not stopped."
}

. (Join-Path $PSScriptRoot "backend-processes.ps1")
if (-not $SkipBuild) {
    Write-Host "Stopping this project's services before rebuilding..." -ForegroundColor Cyan
    Stop-ProjectBackendProcess -BackendRoot $backendRoot
    Write-Host "[1/3] Building backend JARs..." -ForegroundColor Cyan
    Push-Location $backendRoot
    try {
        & mvn package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw "Backend build failed." }
    } finally {
        Pop-Location
    }
} else {
    Write-Host "[1/3] Backend build skipped." -ForegroundColor DarkGray
}

$env:DB_PASSWORD = $settings.MYSQL_APP_PASSWORD
$env:JWT_SECRET = $settings.JWT_SECRET
$env:INTERNAL_SIGNING_KEY = $settings.INTERNAL_SIGNING_KEY
if ($settings.STORE_CREDENTIAL_KEY) { $env:STORE_CREDENTIAL_KEY = $settings.STORE_CREDENTIAL_KEY }
$env:BOOTSTRAP_ADMIN_ENABLED = "true"
$env:BOOTSTRAP_ADMIN_USERNAME = $settings.BOOTSTRAP_ADMIN_USERNAME
$env:BOOTSTRAP_ADMIN_PASSWORD = $settings.BOOTSTRAP_ADMIN_PASSWORD
$env:NACOS_ENABLED = "false"
$env:REDIS_HOST = "127.0.0.1"
$env:REDIS_PORT = "6379"
$env:TENANT_SERVICE_URL = "http://127.0.0.1:8081"
$env:IAM_SERVICE_URL = "http://127.0.0.1:8082"
$env:MERCHANT_SERVICE_URL = "http://127.0.0.1:8083"
$env:MEMBER_SERVICE_URL = "http://127.0.0.1:8084"
$env:TRADE_SERVICE_URL = "http://127.0.0.1:8085"
$env:SIMULATED_PAYMENT_ENABLED = if ($DisableSimulatedPayment) { "false" } else { "true" }

$services = @(
    @{ Name="tenant-service";   Port=8081; Jar="tenant-service\target\tenant-service-0.1.0-SNAPSHOT.jar"; Profile="compose"; DbUser="saas_tenant" },
    @{ Name="iam-service";      Port=8082; Jar="iam-service\target\iam-service-0.1.0-SNAPSHOT.jar"; Profile="compose"; DbUser="saas_iam" },
    @{ Name="merchant-service"; Port=8083; Jar="merchant-service\target\merchant-service-0.1.0-SNAPSHOT.jar"; Profile="compose"; DbUser="saas_merchant" },
    @{ Name="member-service";   Port=8084; Jar="member-service\target\member-service-0.1.0-SNAPSHOT.jar"; Profile="compose"; DbUser="saas_member" },
    @{ Name="trade-service";    Port=8085; Jar="trade-service\target\trade-service-0.1.0-SNAPSHOT.jar"; Profile="compose"; DbUser="saas_trade" },
    @{ Name="saas-gateway";     Port=8080; Jar="saas-gateway\target\saas-gateway-0.1.0-SNAPSHOT.jar"; Profile=""; DbUser="" }
)

function Test-Health([int]$port) {
    try {
        $result = Invoke-RestMethod -Uri "http://127.0.0.1:$port/actuator/health" -TimeoutSec 2
        return $result.status -eq "UP"
    } catch { return $false }
}

Write-Host "[2/3] Starting backend services..." -ForegroundColor Cyan
$started = @()
foreach ($service in $services) {
    if (Test-Health $service.Port) {
        Write-Host "  $($service.Name) already running on $($service.Port)" -ForegroundColor Yellow
        continue
    }

    if ($service.DbUser) { $env:DB_USERNAME = $service.DbUser }
    else { Remove-Item Env:DB_USERNAME -ErrorAction SilentlyContinue }

    $jarPath = Join-Path $backendRoot $service.Jar
    if (-not (Test-Path $jarPath)) { throw "Missing JAR: $jarPath" }
    $arguments = @("-Duser.timezone=Asia/Shanghai", "-jar", $jarPath)
    if ($service.Profile) { $arguments += "--spring.profiles.active=$($service.Profile)" }
    if ($service.DbUser) { $arguments += "--spring.datasource.url=jdbc:mysql://127.0.0.1:13306/$($service.DbUser)?useUnicode=true&characterEncoding=utf8&connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true" }

    $process = Start-Process java -ArgumentList $arguments -WorkingDirectory $backendRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $logRoot "$($service.Name).out.log") `
        -RedirectStandardError (Join-Path $logRoot "$($service.Name).err.log")
    $started += [pscustomobject]@{ Name=$service.Name; Port=$service.Port; Pid=$process.Id }
    Write-Host "  started $($service.Name) (PID $($process.Id))"
}

$running = @(Get-ProjectBackendProcess -BackendRoot $backendRoot | ForEach-Object {
    $item = $_
    $service = $services | Where-Object { $_.Name -eq $item.Name } | Select-Object -First 1
    [pscustomobject]@{ Name=$item.Name; Port=$service.Port; Pid=$item.Pid }
})
ConvertTo-Json -InputObject $running | Set-Content -LiteralPath $pidFile -Encoding UTF8

Write-Host "[3/3] Waiting for health checks..." -ForegroundColor Cyan
$deadline = (Get-Date).AddSeconds($HealthTimeoutSeconds)
$pending = @($services)
do {
    $pending = @($pending | Where-Object { -not (Test-Health $_.Port) })
    if ($pending.Count -eq 0) { break }
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)

if ($pending.Count -gt 0) {
    $names = ($pending | ForEach-Object { "$($_.Name):$($_.Port)" }) -join ", "
    throw "Services failed health checks: $names. Check $logRoot"
}

Write-Host "All backend services are UP." -ForegroundColor Green
Write-Host "Gateway: http://127.0.0.1:8080"
Write-Host "Logs:   $logRoot"


