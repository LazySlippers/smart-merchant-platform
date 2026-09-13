param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$PlatformUsername = "platform-admin",
    [string]$PlatformPassword = "change-me-platform-admin"
)
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http
$envFile=Join-Path (Split-Path -Parent $PSScriptRoot) ".env"
if(Test-Path $envFile){$local=@{};Get-Content $envFile|ForEach-Object{if($_-match'^([^#=]+)=(.*)$'){$local[$matches[1]]=$matches[2]}};if(-not$PSBoundParameters.ContainsKey('PlatformUsername')-and$local.BOOTSTRAP_ADMIN_USERNAME){$PlatformUsername=$local.BOOTSTRAP_ADMIN_USERNAME};if(-not$PSBoundParameters.ContainsKey('PlatformPassword')-and$local.BOOTSTRAP_ADMIN_PASSWORD){$PlatformPassword=$local.BOOTSTRAP_ADMIN_PASSWORD}}
function Invoke-SaasApi {
    param([string]$Method,[string]$Path,[object]$Body,[string]$AccessToken,[int[]]$ExpectedStatus=@(200))
    $headers=@{};if($AccessToken){$headers.Authorization="Bearer $AccessToken"}
    $parameters=@{Method=$Method;Uri="$BaseUrl$Path";Headers=$headers;UseBasicParsing=$true}
    if($null-ne$Body){$parameters.ContentType="application/json";$parameters.Body=$Body|ConvertTo-Json -Depth 12}
    try{$response=Invoke-WebRequest @parameters}catch{if(-not$_.Exception.Response){throw};$raw=$_.Exception.Response;if($raw-is[System.Net.Http.HttpResponseMessage]){$response=[pscustomobject]@{StatusCode=[int]$raw.StatusCode;Content=""}}else{$reader=[System.IO.StreamReader]::new($raw.GetResponseStream());$response=[pscustomobject]@{StatusCode=[int]$raw.StatusCode;Content=$reader.ReadToEnd()};$reader.Dispose()}}
    $content=if($response.Content-is[byte[]]){[System.Text.Encoding]::UTF8.GetString($response.Content)}else{[string]$response.Content}
    if($response.StatusCode-notin$ExpectedStatus){throw "$Method $Path expected $($ExpectedStatus-join'/') but got $($response.StatusCode): $content"}
    if([string]::IsNullOrWhiteSpace($content)){return $null};return $content|ConvertFrom-Json
}
function Assert-Equal([object]$Actual,[object]$Expected,[string]$Message){if([string]$Actual-ne[string]$Expected){throw "$Message; expected=$Expected actual=$Actual"}}

$health=Invoke-SaasApi GET "/actuator/health";Assert-Equal $health.status "UP" "Gateway health"
$platform=$null;foreach($candidate in @($PlatformPassword,"change-me-platform-admin")|Select-Object -Unique){try{$platform=Invoke-SaasApi POST "/api/auth/v1/login" @{username=$PlatformUsername;password=$candidate};break}catch{if($candidate-eq"change-me-platform-admin"){throw}}};if(-not$platform){throw "Platform login failed"}
$suffix=Get-Date -Format "MMddHHmmss";$mobile="138$($suffix.Substring($suffix.Length-8))";$password="M2-Test!2026"
$application=Invoke-SaasApi POST "/api/public/v1/tenant-applications" @{merchantName="M2验收品牌-$suffix";contactName="M2负责人";contactMobile=$mobile;planCode="STANDARD";password=$password} $null @(201)
$tenant=Invoke-SaasApi POST "/api/platform/v1/tenant-applications/$($application.id):approve" $null $platform.accessToken
$owner=Invoke-SaasApi POST "/api/auth/v1/login" @{username=$mobile;password=$password}
$token=$owner.accessToken

$storeA=Invoke-SaasApi POST "/api/merchant/v1/stores" @{storeCode="A-$suffix";storeName="验收门店A";address="A路";businessHours="09:00-22:00"} $token
$storeB=Invoke-SaasApi POST "/api/merchant/v1/stores" @{storeCode="B-$suffix";storeName="验收门店B";address="B路";businessHours="09:00-22:00"} $token
$category=Invoke-SaasApi POST "/api/merchant/v1/categories" @{categoryCode="CAT-$suffix";categoryName="验收分类";sortOrder=1} $token
$spu=Invoke-SaasApi POST "/api/merchant/v1/products" @{categoryId=$category.id;spuCode="SPU-$suffix";productName="验收商品";description="M2 E2E"} $token
$spu=Invoke-SaasApi PUT "/api/merchant/v1/products/$($spu.id)" @{categoryId=$category.id;productName=$spu.productName;description=$spu.description;imageUrl=$null;status="ACTIVE";version=$spu.version} $token
$sku=Invoke-SaasApi POST "/api/merchant/v1/products/$($spu.id)/skus" @{skuCode="SKU-$suffix";skuName="标准规格";specJson='{"size":"M"}';barcode="69$suffix";basePriceCents=1800;allowStorePrice=$true} $token
$sku=Invoke-SaasApi PUT "/api/merchant/v1/skus/$($sku.id)" @{skuName=$sku.skuName;specJson=$sku.specJson;barcode=$sku.barcode;basePriceCents=1800;allowStorePrice=$true;status="ACTIVE";version=$sku.version} $token
Invoke-SaasApi PUT "/api/merchant/v1/stores/$($storeA.id)/products/$($sku.id)" @{sellable=$true;storePriceCents=2000} $token|Out-Null
Invoke-SaasApi PUT "/api/merchant/v1/stores/$($storeB.id)/products/$($sku.id)" @{sellable=$true;storePriceCents=$null} $token|Out-Null
$catalog=Invoke-SaasApi GET "/api/merchant/v1/products?storeId=$($storeA.id)" $null $token
Assert-Equal $catalog[0].skus[0].effectivePriceCents 2000 "Store price override"

$initialKey="M2-INIT-$suffix";Invoke-SaasApi POST "/api/merchant/v1/inventory/$($sku.id)/adjust" @{storeId=$storeA.id;quantityDelta=20;idempotencyKey=$initialKey;reason="验收期初"} $token|Out-Null
Invoke-SaasApi POST "/api/merchant/v1/inventory/$($sku.id)/adjust" @{storeId=$storeA.id;quantityDelta=20;idempotencyKey=$initialKey;reason="验收期初"} $token|Out-Null
$ledger=Invoke-SaasApi GET "/api/merchant/v1/inventory/$($sku.id)/ledger?storeId=$($storeA.id)" $null $token;Assert-Equal @($ledger|Where-Object { $_.idempotencyKey -eq $initialKey }).Count 1 "Idempotent ledger"

$client=[System.Net.Http.HttpClient]::new();$client.DefaultRequestHeaders.Authorization=[System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer",$token)
$json1=@{storeId=$storeA.id;quantityDelta=-15;idempotencyKey="M2-CON-A-$suffix";reason="并发扣减A"}|ConvertTo-Json
$json2=@{storeId=$storeA.id;quantityDelta=-15;idempotencyKey="M2-CON-B-$suffix";reason="并发扣减B"}|ConvertTo-Json
$task1=$client.PostAsync("$BaseUrl/api/merchant/v1/inventory/$($sku.id)/adjust",[System.Net.Http.StringContent]::new($json1,[System.Text.Encoding]::UTF8,"application/json"))
$task2=$client.PostAsync("$BaseUrl/api/merchant/v1/inventory/$($sku.id)/adjust",[System.Net.Http.StringContent]::new($json2,[System.Text.Encoding]::UTF8,"application/json"))
[System.Threading.Tasks.Task]::WaitAll([System.Threading.Tasks.Task[]]@($task1,$task2));$statuses=@([int]$task1.Result.StatusCode,[int]$task2.Result.StatusCode)|Sort-Object
Assert-Equal ($statuses-join",") "200,409" "Concurrent conditional deduction"
Invoke-SaasApi POST "/api/merchant/v1/inventory/$($sku.id)/adjust" @{storeId=$storeA.id;quantityDelta=15;idempotencyKey="M2-RESTORE-$suffix";reason="恢复调拨库存"} $token|Out-Null

$count=Invoke-SaasApi POST "/api/merchant/v1/stock-counts" @{storeId=$storeA.id;countNo="COUNT-$suffix";remark="E2E盘点";items=@(@{skuId=$sku.id;countedQuantity=18})} $token
Invoke-SaasApi POST "/api/merchant/v1/stock-counts/$($count.id)/complete" $null $token|Out-Null
$transfer=Invoke-SaasApi POST "/api/merchant/v1/transfers" @{transferNo="TRANSFER-$suffix";sourceStoreId=$storeA.id;targetStoreId=$storeB.id;remark="E2E调拨";items=@(@{skuId=$sku.id;quantity=8})} $token
$transfer=Invoke-SaasApi POST "/api/merchant/v1/transfers/$($transfer.id)/approve" $null $token
$transfer=Invoke-SaasApi POST "/api/merchant/v1/transfers/$($transfer.id)/ship" $null $token
Invoke-SaasApi POST "/api/merchant/v1/transfers/$($transfer.id)/ship" $null $token @(409)|Out-Null
$transfer=Invoke-SaasApi POST "/api/merchant/v1/transfers/$($transfer.id)/receive" $null $token;Assert-Equal $transfer.status "COMPLETED" "Transfer completion"
$sourceInventory=Invoke-SaasApi GET "/api/merchant/v1/inventory?storeId=$($storeA.id)" $null $token
$targetInventory=Invoke-SaasApi GET "/api/merchant/v1/inventory?storeId=$($storeB.id)" $null $token
Assert-Equal $sourceInventory[0].actualQuantity 10 "Source stock after count and transfer";Assert-Equal $targetInventory[0].actualQuantity 8 "Target stock after transfer"

$workerName="worker-$suffix";$workerPassword="Worker!2026"
$worker=Invoke-SaasApi POST "/api/merchant/v1/users" @{username=$workerName;password=$workerPassword;displayName="A店员工";mobile=$null;dataScope="STORE_SELF"} $token
$role=Invoke-SaasApi POST "/api/merchant/v1/roles" @{code="STORE-$suffix";name="门店验收角色"} $token
$permissions=Invoke-SaasApi GET "/api/merchant/v1/permissions" $null $token
$permissionIds=@($permissions|Where-Object { $_.permissionCode -in @("merchant:store:view","merchant:product:view","merchant:inventory:view") }|ForEach-Object id)
Invoke-SaasApi PUT "/api/merchant/v1/roles/$($role.id)/permissions" @{ids=$permissionIds} $token|Out-Null
Invoke-SaasApi PUT "/api/merchant/v1/users/$($worker.id)/roles" @{ids=@($role.id)} $token|Out-Null
Invoke-SaasApi POST "/api/merchant/v1/employees" @{userId=$worker.id;employeeNo="EMP-$suffix";employeeName="A店员工";primaryStoreId=$storeA.id;jobTitle="店员";storeIds=@($storeA.id)} $token|Out-Null
$workerLogin=Invoke-SaasApi POST "/api/auth/v1/login" @{username=$workerName;password=$workerPassword}
Invoke-SaasApi GET "/api/merchant/v1/inventory?storeId=$($storeA.id)" $null $workerLogin.accessToken|Out-Null
Invoke-SaasApi GET "/api/merchant/v1/inventory?storeId=$($storeB.id)" $null $workerLogin.accessToken @(403)|Out-Null

[pscustomobject]@{result="PASSED";tenantId=[string]$tenant.id;checks=@("store A/B scope","category SPU SKU publish","brand/store price","store sellable range","idempotent inventory ledger","concurrent non-negative deduction","stock count difference","transfer approval/outbound/receive","duplicate shipment rejection","double-sided transfer ledger","STORE_SELF cross-store rejection")}|ConvertTo-Json -Depth 5
