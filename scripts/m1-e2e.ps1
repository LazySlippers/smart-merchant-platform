param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$PlatformUsername = "platform-admin",
    [string]$PlatformPassword = "change-me-platform-admin"
)

$ErrorActionPreference = "Stop"
$envFile = Join-Path (Split-Path -Parent $PSScriptRoot) ".env"
if (Test-Path $envFile) {
    $settings = @{}
    Get-Content -LiteralPath $envFile | ForEach-Object { if ($_ -match '^([^#=]+)=(.*)$') { $settings[$matches[1]] = $matches[2] } }
    if (-not $PSBoundParameters.ContainsKey('PlatformUsername') -and $settings.BOOTSTRAP_ADMIN_USERNAME) { $PlatformUsername = $settings.BOOTSTRAP_ADMIN_USERNAME }
    if (-not $PSBoundParameters.ContainsKey('PlatformPassword') -and $settings.BOOTSTRAP_ADMIN_PASSWORD) { $PlatformPassword = $settings.BOOTSTRAP_ADMIN_PASSWORD }
}

function Invoke-SaasApi {
    param(
        [Parameter(Mandatory)] [string]$Method,
        [Parameter(Mandatory)] [string]$Path,
        [object]$Body,
        [string]$AccessToken,
        [int[]]$ExpectedStatus = @(200)
    )

    $headers = @{}
    if ($AccessToken) {
        $headers.Authorization = "Bearer $AccessToken"
    }

    $parameters = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        Headers = $headers
        SkipHttpErrorCheck = $true
    }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json"
        $parameters.Body = $Body | ConvertTo-Json -Depth 8
    }

    $response = Invoke-WebRequest @parameters
    if ($response.StatusCode -notin $ExpectedStatus) {
        throw "$Method $Path expected HTTP $($ExpectedStatus -join '/') but got $($response.StatusCode): $($response.Content)"
    }

    $content = if ($response.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($response.Content) } else { [string]$response.Content }
    if ([string]::IsNullOrWhiteSpace($content)) {
        return $null
    }
    return $content | ConvertFrom-Json
}

$health = Invoke-SaasApi -Method GET -Path "/actuator/health"
if ($health.status -ne "UP") {
    throw "Gateway is not healthy."
}

$platform = Invoke-SaasApi -Method POST -Path "/api/auth/v1/login" -Body @{
    tenantId = 0
    username = $PlatformUsername
    password = $PlatformPassword
}

$suffix = Get-Date -Format "MMddHHmmss"
$mobile = "139$($suffix.Substring($suffix.Length - 8))"
$merchantPassword = "M1-Test!2026"
$application = Invoke-SaasApi -Method POST -Path "/api/public/v1/tenant-applications" -Body @{
    merchantName = "M1验收品牌-$suffix"
    contactName = "M1验收负责人"
    contactMobile = $mobile
    planCode = "STANDARD"
    password = $merchantPassword
} -ExpectedStatus @(201)

$tenant = Invoke-SaasApi -Method POST -Path "/api/platform/v1/tenant-applications/$($application.id):approve" -AccessToken $platform.accessToken
$merchant = Invoke-SaasApi -Method POST -Path "/api/auth/v1/login" -Body @{
    tenantId = $tenant.id
    username = $mobile
    password = $merchantPassword
}

$identity = Invoke-SaasApi -Method GET -Path "/api/merchant/v1/me" -AccessToken $merchant.accessToken
if ([string]$identity.tenantId -ne [string]$tenant.id -or $identity.platform) {
    throw "Merchant identity is not isolated to the approved tenant."
}

Invoke-SaasApi -Method POST -Path "/api/platform/v1/tenants/$($tenant.id):suspend" -AccessToken $platform.accessToken | Out-Null
Invoke-SaasApi -Method GET -Path "/api/merchant/v1/me" -AccessToken $merchant.accessToken -ExpectedStatus @(403) | Out-Null
Invoke-SaasApi -Method POST -Path "/api/platform/v1/tenants/$($tenant.id):resume" -AccessToken $platform.accessToken | Out-Null

$renewedMerchant = Invoke-SaasApi -Method POST -Path "/api/auth/v1/login" -Body @{
    tenantId = $tenant.id
    username = $mobile
    password = $merchantPassword
}
Invoke-SaasApi -Method GET -Path "/api/merchant/v1/me" -AccessToken $renewedMerchant.accessToken | Out-Null

[pscustomobject]@{
    result = "PASSED"
    tenantId = [string]$tenant.id
    merchantUsername = $mobile
    checks = @(
        "public application"
        "platform approval"
        "owner provisioning"
        "merchant trusted identity"
        "suspended tenant old-token rejection"
        "resumed tenant re-login"
    )
} | ConvertTo-Json -Depth 4
