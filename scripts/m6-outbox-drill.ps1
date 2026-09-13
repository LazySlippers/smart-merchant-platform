param(
    [switch]$Execute,
    [string]$ComposeFile = (Join-Path (Split-Path -Parent $PSScriptRoot) "deploy\compose\compose.yml")
)
$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot ".env"
if (-not $Execute) {
    Write-Output "Dry run only. This drill temporarily stops RabbitMQ and prints recovery checks. Re-run with -Execute."
    docker compose --env-file $envFile -f $ComposeFile ps rabbitmq
    exit 0
}

try {
    docker compose --env-file $envFile -f $ComposeFile stop rabbitmq
    Write-Output "RabbitMQ stopped. API health and database-backed Outbox must remain available; create a transaction with scripts/m4-e2e.ps1 if needed."
    Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:8080/actuator/health" -TimeoutSec 15 | Out-Null
}
finally {
    docker compose --env-file $envFile -f $ComposeFile start rabbitmq
}

$containerId = docker compose --env-file $envFile -f $ComposeFile ps -q rabbitmq
$deadline = (Get-Date).AddMinutes(2)
do {
    $state = docker inspect --format '{{.State.Health.Status}}' $containerId 2>$null
    if ($state -eq 'healthy') { break }
    Start-Sleep -Seconds 3
} while ((Get-Date) -lt $deadline)
if ($state -ne 'healthy') { throw "RabbitMQ did not recover before timeout" }
Write-Output "PASSED: RabbitMQ recovered. Next verify analytics_outbox unpublished rows are retried and duplicate event IDs remain single-row in analytics_inbox."
