param([string]$BaseUrl="http://localhost:8080")
$ErrorActionPreference="Stop"
$root=Split-Path -Parent $PSScriptRoot;$settings=@{};Get-Content (Join-Path $root ".env")|ForEach-Object{if($_-match'^([^#=]+)=(.*)$'){$settings[$matches[1]]=$matches[2]}}
function Api([string]$Method,[string]$Path,[object]$Body=$null,[string]$Token=$null,[int[]]$Expected=@(200)){$h=@{};if(-not $Token -and $Path -like "/api/consumer/*" -and $Path -notlike "*/auth/*" -and $Path -notlike "*/catalog/*"){$Token=$script:consumerToken};if($Token){$h.Authorization="Bearer $Token"};$p=@{Method=$Method;Uri="$BaseUrl$Path";Headers=$h;UseBasicParsing=$true;TimeoutSec=30};if($null-ne$Body){$p.ContentType="application/json";$p.Body=$Body|ConvertTo-Json -Depth 12};try{$r=Invoke-WebRequest @p}catch{if(-not$_.Exception.Response){throw};$raw=$_.Exception.Response;if($raw -is [System.Net.Http.HttpResponseMessage]){$r=[pscustomobject]@{StatusCode=[int]$raw.StatusCode;Content=""}}else{$reader=[IO.StreamReader]::new($raw.GetResponseStream());$r=[pscustomobject]@{StatusCode=[int]$raw.StatusCode;Content=$reader.ReadToEnd()};$reader.Dispose()}};$content=if($r.Content-is[byte[]]){[Text.Encoding]::UTF8.GetString($r.Content)}else{[string]$r.Content};if($r.StatusCode-notin$Expected){throw "$Method $Path expected $($Expected-join'/') got $($r.StatusCode): $content"};if([string]::IsNullOrWhiteSpace($content)){return $null};return $content|ConvertFrom-Json}
function Eq($Actual,$Expected,[string]$Message){if([string]$Actual-ne[string]$Expected){throw "$Message expected=$Expected actual=$Actual"}}

Eq (Api GET "/actuator/health").status "UP" "Gateway health"
$platform=Api POST "/api/auth/v1/login" @{username=$settings.BOOTSTRAP_ADMIN_USERNAME;password=$settings.BOOTSTRAP_ADMIN_PASSWORD}
$suffix=Get-Date -Format "MMddHHmmss";$ownerMobile="139$($suffix.Substring($suffix.Length-8))";$password="M3-Test!2026"
$application=Api POST "/api/public/v1/tenant-applications" @{merchantName="M3验收品牌-$suffix";contactName="负责人";contactMobile=$ownerMobile;planCode="STANDARD";password=$password} $null @(201)
$tenant=Api POST "/api/platform/v1/tenant-applications/$($application.id):approve" $null $platform.accessToken
$owner=Api POST "/api/auth/v1/login" @{username=$ownerMobile;password=$password};$token=$owner.accessToken
$a=Api POST "/api/merchant/v1/stores" @{storeCode="A-$suffix";storeName="订单门店A";address="A路";businessHours="09:00-22:00"} $token
$b=Api POST "/api/merchant/v1/stores" @{storeCode="B-$suffix";storeName="订单门店B";address="B路";businessHours="09:00-22:00"} $token
$category=Api POST "/api/merchant/v1/categories" @{categoryCode="C-$suffix";categoryName="饮品";sortOrder=1} $token
$spu=Api POST "/api/merchant/v1/products" @{categoryId=$category.id;spuCode="P-$suffix";productName="M3-Latte";description="Order snapshot E2E"} $token
$spu=Api PUT "/api/merchant/v1/products/$($spu.id)" @{categoryId=$category.id;productName=$spu.productName;description=$spu.description;imageUrl=$null;status="ACTIVE";version=$spu.version} $token
$sku=Api POST "/api/merchant/v1/products/$($spu.id)/skus" @{skuCode="S-$suffix";skuName="中杯";specJson='{"size":"M"}';barcode="68$suffix";basePriceCents=1800;allowStorePrice=$true} $token
$sku=Api PUT "/api/merchant/v1/skus/$($sku.id)" @{skuName=$sku.skuName;specJson=$sku.specJson;barcode=$sku.barcode;basePriceCents=1800;allowStorePrice=$true;status="ACTIVE";version=$sku.version} $token
Api PUT "/api/merchant/v1/stores/$($a.id)/products/$($sku.id)" @{sellable=$true;storePriceCents=2000} $token|Out-Null
Api POST "/api/merchant/v1/inventory/$($sku.id)/adjust" @{storeId=$a.id;quantityDelta=10;idempotencyKey="M3-INIT-$suffix";reason="M3验收期初"} $token|Out-Null
$catalog=Api GET "/api/consumer/v1/catalog/tenants/$($tenant.id)/stores/$($a.id)/products";Eq $catalog.products[0].effectivePriceCents 2000 "Consumer store price"

$script:consumerToken=(Api POST "/api/consumer/v1/auth/register" @{tenantId=$tenant.id;mobile="13700000000";memberName="顾客甲";password="Consumer-Test!2026"}).accessToken
$order=Api POST "/api/consumer/v1/orders" @{tenantId=$tenant.id;storeId=$a.id;customerName="顾客甲";customerMobile="13700000000";requestId="ORDER-$suffix";items=@(@{skuId=$sku.id;quantity=2})}
Eq $order.items[0].productName "M3-Latte" "Product snapshot";Eq $order.items[0].unitPriceCents 2000 "Price snapshot";Eq $order.totalAmountCents 4000 "Order total"
$stock=Api GET "/api/merchant/v1/inventory?storeId=$($a.id)" $null $token;Eq $stock[0].availableQuantity 8 "Reserved available";Eq $stock[0].reservedQuantity 2 "Reserved quantity"
$paid=Api POST "/api/consumer/v1/orders/$($order.id)/simulate-payment" @{tenantId=$tenant.id;paymentRequestId="PAY-$suffix"}
$replay=Api POST "/api/consumer/v1/orders/$($order.id)/simulate-payment" @{tenantId=$tenant.id;paymentRequestId="PAY-$suffix"};Eq $replay.pickupCode $paid.pickupCode "Payment replay"
$stock=Api GET "/api/merchant/v1/inventory?storeId=$($a.id)" $null $token;Eq $stock[0].actualQuantity 8 "Paid actual stock";Eq $stock[0].reservedQuantity 0 "Paid reserved stock"

$permissions=Api GET "/api/merchant/v1/permissions" $null $token;$ids=@($permissions|Where-Object{$_.permissionCode-in@("merchant:store:view","merchant:order:view","merchant:order:verify")}|ForEach-Object id)
$role=Api POST "/api/merchant/v1/roles" @{code="PICKUP-$suffix";name="自提核销员"} $token;Api PUT "/api/merchant/v1/roles/$($role.id)/permissions" @{ids=$ids} $token|Out-Null
function Worker([string]$Name,$Store,[string]$No){$u=Api POST "/api/merchant/v1/users" @{username=$Name;password="Worker!2026";displayName=$Name;mobile=$null;dataScope="STORE_SELF"} $token;Api PUT "/api/merchant/v1/users/$($u.id)/roles" @{ids=@($role.id)} $token|Out-Null;Api POST "/api/merchant/v1/employees" @{userId=$u.id;employeeNo=$No;employeeName=$Name;primaryStoreId=$Store.id;jobTitle="核销员";storeIds=@($Store.id)} $token|Out-Null;return(Api POST "/api/auth/v1/login" @{username=$Name;password="Worker!2026"}).accessToken}
$workerA=Worker "m3-a-$suffix" $a "EA-$suffix";$workerB=Worker "m3-b-$suffix" $b "EB-$suffix"
Api POST "/api/merchant/v1/orders/$($order.id)/verify" @{pickupCode=$paid.pickupCode} $workerB @(403)|Out-Null
$done=Api POST "/api/merchant/v1/orders/$($order.id)/verify" @{pickupCode=$paid.pickupCode} $workerA;Eq $done.status "COMPLETED" "Pickup completion"
$performance=Api GET "/api/merchant/v1/orders/performance?storeId=$($a.id)" $null $token;Eq $performance.completedOrders 1 "Store performance orders";Eq $performance.revenueCents 4000 "Store revenue"
$finance=Api GET "/api/merchant/v1/settlements/finance-ledger?storeId=$($a.id)" $null $token;Eq $finance[0].amountCents 4000 "Finance ledger"

$script:consumerToken=(Api POST "/api/consumer/v1/auth/register" @{tenantId=$tenant.id;mobile="13600000000";memberName="顾客乙";password="Consumer-Test!2026"}).accessToken
Api GET "/api/consumer/v1/orders/$($order.id)?tenantId=$($tenant.id)&mobile=13600000000" $null $script:consumerToken @(404)|Out-Null
$expired=Api POST "/api/consumer/v1/orders" @{tenantId=$tenant.id;storeId=$a.id;customerName="顾客乙";customerMobile="13600000000";requestId="EXPIRE-$suffix";items=@(@{skuId=$sku.id;quantity=1})}
Push-Location $root;try{& docker compose --env-file .env -f deploy/compose/compose.yml exec -T -e "MYSQL_PWD=$($settings.MYSQL_ROOT_PASSWORD)" mysql mysql -uroot saas_trade -e "UPDATE orders SET expires_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE id=$($expired.id);";if($LASTEXITCODE-ne0){throw "Failed to age unpaid order"}}finally{Pop-Location}
$scan=Api POST "/api/merchant/v1/orders/expire-scan" $null $token;Eq $scan.closedOrders 1 "Expired scan"
$closed=Api GET "/api/consumer/v1/orders/$($expired.id)?tenantId=$($tenant.id)&mobile=13600000000";Eq $closed.status "CLOSED" "Expired order status"
$stock=Api GET "/api/merchant/v1/inventory?storeId=$($a.id)" $null $token;Eq $stock[0].availableQuantity 8 "Expired reservation released"

[pscustomobject]@{result="PASSED";tenantId=[string]$tenant.id;checks=@("consumer store/catalog","immutable order snapshots","synchronous reservation","idempotent simulated payment","reserved deduction","pickup code","cross-store rejection","own-store verification","store performance","finance ledger","timeout close and release")}|ConvertTo-Json -Depth 5
