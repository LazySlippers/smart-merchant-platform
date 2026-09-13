param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AnalyticsUrl = "http://localhost:8083"
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root ".env"
$settings = @{}
Get-Content -LiteralPath $envFile | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $settings[$matches[1]] = $matches[2] }
}

function Invoke-Api([string]$Method, [string]$Url, $Body = $null, [string]$Token = "", $Headers = @{}, [int[]]$Expected = @(200)) {
    $allHeaders = @{} + $Headers
    if ($Token) { $allHeaders.Authorization = "Bearer $Token" }
    $request = @{ Method = $Method; Uri = $Url; Headers = $allHeaders; TimeoutSec = 30; UseBasicParsing = $true }
    if ($null -ne $Body) { $request.ContentType = "application/json"; $request.Body = $Body | ConvertTo-Json -Depth 10 }
    try {
        $response = Invoke-WebRequest @request
    }
    catch {
        if ($null -eq $_.Exception.Response) { throw }
        $status = [int]$_.Exception.Response.StatusCode
        if ($status -notin $Expected) { throw "$Method $Url expected $($Expected -join ',') but got $status" }
        return $null
    }
    $content = if ($response.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($response.Content) } else { [string]$response.Content }
    if ($response.StatusCode -notin $Expected) { throw "$Method $Url expected $($Expected -join ',') but got $($response.StatusCode): $content" }
    if ([string]::IsNullOrWhiteSpace($content)) { return $null }
    $parsed = $content | ConvertFrom-Json
    if ($null -ne $parsed.data) { return $parsed.data }
    return $parsed
}

function New-Tenant([string]$Suffix) {
    $mobile = "13$Suffix"
    $password = "M6-Security!2026"
    $application = Invoke-Api POST "$BaseUrl/api/public/v1/tenant-applications" @{
        merchantName = "M6安全租户-$Suffix"; contactName = "安全验收"; contactMobile = $mobile
        planCode = "STANDARD"; password = $password
    } "" @{} @(201)
    $approved = Invoke-Api POST "$BaseUrl/api/platform/v1/tenant-applications/$($application.id):approve" $null $script:adminToken
    $token = (Invoke-Api POST "$BaseUrl/api/auth/v1/login" @{ username = $mobile; password = $password }).accessToken
    return [pscustomobject]@{ Id = $approved.id; Mobile = $mobile; Token = $token }
}

$script:adminToken = (Invoke-Api POST "$BaseUrl/api/auth/v1/login" @{
    username = $settings.BOOTSTRAP_ADMIN_USERNAME; password = $settings.BOOTSTRAP_ADMIN_PASSWORD
}).accessToken

# 平台身份不能进入商户域，匿名伪造可信头也不能绕过认证。
Invoke-Api GET "$BaseUrl/api/merchant/v1/stores" $null $adminToken @{} @(403) | Out-Null
Invoke-Api GET "$BaseUrl/api/merchant/v1/stores" $null "" @{
    "X-Tenant-Id" = "1"; "X-User-Id" = "1"; "X-Internal-Caller" = "saas-gateway"; "X-Internal-Signature" = "forged"
} @(401) | Out-Null

# 内部事件入口必须拒绝错误密钥。
Invoke-Api POST "$AnalyticsUrl/internal/v1/analytics/events" @{
    eventId = "forged-$([guid]::NewGuid())"; eventType = "ORDER_PAID"; eventVersion = 1
    tenantId = 1; aggregateId = "1"; occurredAt = (Get-Date).ToUniversalTime().ToString("o"); payload = @{}
} "" @{ "X-Internal-Key" = "forged" } @(401, 403) | Out-Null

$stamp = Get-Date -Format "HHmmssffff"
$tenantA = New-Tenant $stamp.Substring(0, 10)
$tenantB = New-Tenant (([long]$stamp.Substring(0, 10) + 1).ToString().PadLeft(10, '0'))
$store = Invoke-Api POST "$BaseUrl/api/merchant/v1/stores" @{
    storeCode = "SEC-$stamp"; storeName = "租户A门店"; address = "隔离测试"; businessHours = "09:00-22:00"
} $tenantA.Token

# B 租户不能按 A 的资源 ID 读取门店；服务端应隐藏资源存在性。
Invoke-Api GET "$BaseUrl/api/merchant/v1/stores/$($store.id)" $null $tenantB.Token @{} @(403, 404) | Out-Null

# 日志中不得落盘 Bearer JWT 或明文 password JSON 字段。
$logFiles = @(Get-ChildItem -LiteralPath (Join-Path $root "runtime-logs") -Filter "*.log" -File -ErrorAction SilentlyContinue)
foreach ($logFile in $logFiles) {
    if (Select-String -LiteralPath $logFile.FullName -Pattern 'Bearer\s+eyJ[A-Za-z0-9_-]+\.' -Quiet) {
        throw "Sensitive Bearer token found in $($logFile.Name)"
    }
    if (Select-String -LiteralPath $logFile.FullName -Pattern '"password"\s*:\s*"[^"*]+' -Quiet) {
        throw "Plain-text password field found in $($logFile.Name)"
    }
}

[pscustomobject]@{
    result = "PASSED"
    checks = @("platform/merchant identity separation", "forged trusted headers", "internal key", "cross-tenant resource isolation", "sensitive log scan")
    tenantA = $tenantA.Id
    tenantB = $tenantB.Id
} | ConvertTo-Json -Depth 4
