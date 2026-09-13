param(
    [switch]$Execute,
    [string]$ComposeFile = (Join-Path (Split-Path -Parent $PSScriptRoot) "deploy\compose\compose.yml")
)
$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot ".env"
if (-not $Execute) {
    Write-Output "Dry run only. Re-run with -Execute to stop and automatically recover MySQL."
    docker compose --env-file $envFile -f $ComposeFile ps mysql
    exit 0
}

$failureObserved = $false
try {
    docker compose --env-file $envFile -f $ComposeFile stop mysql
    $gateway = Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:8080/actuator/health" -TimeoutSec 10
    if ($gateway.StatusCode -ne 200) { throw "Gateway did not remain available while MySQL was stopped" }
    try {
        Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:8081/api/public/v1/plans" -TimeoutSec 10 | Out-Null
    }
    catch {
        $failureObserved = $true
    }
    if (-not $failureObserved) { throw "Database-dependent request unexpectedly reported success while MySQL was stopped" }
}
finally {
    docker compose --env-file $envFile -f $ComposeFile start mysql
}

$containerId = docker compose --env-file $envFile -f $ComposeFile ps -q mysql
$deadline = (Get-Date).AddMinutes(3)
do {
    $state = docker inspect --format '{{.State.Health.Status}}' $containerId 2>$null
    if ($state -eq "healthy") { break }
    Start-Sleep -Seconds 3
} while ((Get-Date) -lt $deadline)
if ($state -ne "healthy") { throw "MySQL did not recover before timeout" }

$serviceDeadline = (Get-Date).AddMinutes(2)
foreach ($port in 8081..8085) {
    do {
        try { $health = Invoke-RestMethod -Uri "http://localhost:$port/actuator/health" -TimeoutSec 3 } catch { $health = $null }
        if ($health.status -eq "UP") { break }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $serviceDeadline)
    if ($health.status -ne "UP") { throw "Service on port $port did not recover after MySQL restart" }
}
Write-Output "PASSED: database failure was explicit, MySQL recovered, and all database services returned to UP."
