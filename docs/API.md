# API 概览

所有外部请求通过 Gateway `http://localhost:8080`。请求/响应使用 JSON；受保护接口使用 `Authorization: Bearer <accessToken>`。业务 ID 是 64 位整数，JavaScript 客户端应按字符串保存，避免精度丢失。

## 认证与平台

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/v1/login` | 仅提交 username、password，自动识别账号唯一有效租户归属 |
| POST | `/api/auth/v1/refresh` | 刷新令牌 |
| POST | `/api/public/v1/tenant-applications` | 提交入驻申请 |
| GET | `/api/public/v1/plans` | 查询可申请套餐 |
| GET/POST | `/api/platform/v1/tenant-applications/**` | 平台通过或拒绝入驻申请 |
| GET/POST/PUT | `/api/platform/v1/tenants/**` | 租户状态与套餐管理 |
| GET | `/api/platform/v1/dashboard/summary` | 平台经营汇总 |

### 租户自主入驻

`POST /api/public/v1/tenant-applications` 无需登录，接收 `merchantName`、`contactName`、`contactMobile`、`planCode`、`password`。手机号必须为有效的 11 位中国大陆手机号，密码长度为 8–64；请求体不接收 `tenantId`，避免申请人指定或冒用其他租户身份。成功后返回 `PENDING` 申请及申请编号，平台审核通过时才生成独立租户、负责人账号和套餐权益。

同一手机号已有任何租户端登录账号，或已有 `PENDING`/`APPROVED` 申请时返回 `409 Conflict`；消费端账号属于独立身份域，不影响租户申请。平台可调用 `POST /api/platform/v1/tenant-applications/{id}:approve` 通过，或调用 `POST /api/platform/v1/tenant-applications/{id}:reject` 拒绝。审核通过后，负责人使用申请手机号和密码登录；服务端从登录令牌解析租户编号，门店、商品、库存、会员、购物车和订单接口均按该编号隔离，客户端不得自行传入租户编号切换归属。

## 商户经营

| 资源 | 路径前缀 | 主要操作 |
|---|---|---|
| 用户、角色、权限 | `/api/merchant/v1/users`、`roles`、`permissions` | 用户与 RBAC 管理 |
| 门店、员工 | `/api/merchant/v1/stores`、`employees` | 组织与任职范围 |
| 商品 | `/api/merchant/v1/categories`、`products`、`skus` | 分类、SPU、SKU、上下架 |
| 库存 | `/api/merchant/v1/inventory`、`stock-counts`、`transfers` | 调整、盘点、调拨 |
| 会员权益 | `/api/merchant/v1/members`、`coupon-templates`、`benefit-ledgers` | 会员、积分、储值、券 |
| 订单 | `/api/merchant/v1/orders`、`settlements` | 查询、核销、退款、日结 |
| 分析 | `/api/merchant/v1/analytics/dashboard` | 后台经营总览，支持 `from`、`to` 和可选 `storeId` |

### 会员及优惠券生命周期

消费者注册改为 `POST /api/consumer/v1/auth/register`，原 `POST /api/consumer/v1/members` 仅供已认证本人查询兼容使用；商户侧 `POST /api/merchant/v1/members` 已移除。商户保留查询及积分、储值调整权限。

- `POST /api/merchant/v1/coupon-templates` 保存 `DRAFT` 草稿。
- `PUT /api/merchant/v1/coupon-templates/{id}` 仅编辑草稿，包含名称、分单位金额、发行数量、有效期和 `version`。
- `PUT /api/merchant/v1/coupon-templates/{id}/status` 接收 `{ "status": "ACTIVE|PAUSED", "version": 0 }`，用于发布、暂停及恢复领取。
- 发布后规则不可修改；暂停仅阻止新领取，已领券仍在原有效期内可用。过期、领完的券不能恢复，每位会员限领一张。
- 列表增加 `validFrom`、`validTo`、`version`、`redeemedQuantity`。有效期沿用服务端本地日期时间约定，前端不再转换为 UTC 后截断。

经营大屏位于租户后台经营总览，旧大屏入口重定向到后台。分析按订单支付日期统计，退款扣减不超过现金实收；财务流水仍沿用原有核销商品原价口径，前端明确标注，避免与现金实收混淆。

## 消费者

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/consumer/v1/catalog/tenants/{tenantId}/stores` | 门店目录 |
| GET | `/api/consumer/v1/catalog/tenants/{tenantId}/stores/{storeId}/products` | 门店商品 |
| POST/GET | `/api/consumer/v1/members` | 注册/查询会员 |
| POST | `/api/consumer/v1/coupons/{templateId}/claim` | 领券 |
| POST | `/api/consumer/v1/orders` | 创建自提订单 |
| POST | `/api/consumer/v1/orders/{id}/simulate-payment` | 模拟支付 |

## 内部接口

`/internal/v1/**` 仅允许服务间调用，使用 `X-Internal-Key`，不得从公网暴露。事件入口 `/internal/v1/analytics/events` 以 `eventId` 幂等；调用方必须保存 Outbox 后异步投递。

常见状态码：400 参数错误、401 未认证、403 越权/租户状态拒绝、404 资源不属于当前租户、409 状态冲突或幂等冲突、500/503 基础设施异常。

## 租户自助升级套餐

以下接口由 Gateway 转发至 tenant-service。调用者必须为非平台身份、数据范围为 `TENANT_ALL`，并拥有 `merchant:store:manage`；租户 ID 取自可信登录上下文，不接受客户端指定其他租户。

| 方法 | 路径 | 成功响应 |
|---|---|---|
| GET | `/api/merchant/v1/subscription` | 当前套餐：`id`、`planCode`、`planName`、`status`、`storeQuota` |
| GET | `/api/merchant/v1/subscription/upgrades` | 可升级套餐数组，字段同上；无可用项返回空数组 |
| POST | `/api/merchant/v1/subscription/upgrade` | 更新后的租户订阅 |

升级请求示例：`{ "planCode": "ADVANCED" }`。仅允许启用且不削减现有已启用功能和额度、至少有一项提升的套餐；不限额度不能变为有限额度。提交时重新检查可升级性，不可用、重复升级或降级返回 409。成功后发布租户运行态权益并写入 `TENANT_PLAN_CHANGED` 审计。当前流程不含收费、支付或续费结算。

## 门店账号配置与重置

接口要求 `iam:user:manage` 和 `TENANT_ALL`，且门店必须属于当前租户。

| 方法 | 路径 | 请求/说明 |
|---|---|---|
| GET | `/api/merchant/v1/stores/accounts` | 本租户已绑定门店账号列表 |
| POST | `/api/merchant/v1/stores/{id}/account` | `username`、`password`、`displayName`，可选 `permissions` |
| PUT | `/api/merchant/v1/stores/{id}/account/password` | `{ "password": "新密码至少8位" }` |

创建账号时用户名、显示名称必填，密码至少 8 位；省略权限使用默认门店权限。前端建议用户名为 `store-{门店ID}-admin`，可自行修改，已绑定账号不自动改名。密码默认不随列表返回，可通过下述受保护接口按需查看。创建/重置成功返回账号视图，包含 `storeId`、`storeCode`、`storeName`、`userId`、`username`、`displayName`、`createdAt`、`updatedAt`。

IAM 下游账号冲突返回到商户端为 409，参数错误为 400，其他下游 HTTP 异常为 502，响应包含可展示的 `message`。IAM 的错误分派保留原始错误状态，不再覆盖为 401；内部账号接口仍要求正确的 `X-Internal-Key`。

### 门店账号再次修改与密码检视（2026-09-06）

- `PUT /api/merchant/v1/stores/{id}/account`：请求包含必填 `username`、`displayName`（最长 128 字符），可选 `password`（8–64 字符）。不传密码保留原密码。更新既有 userId 的账号资料并同步门店账号和员工名称，不改变门店绑定或功能权限；登录令牌版本递增。
- `POST /api/merchant/v1/stores/{id}/account/reveal`：无请求体，要求 `iam:user:manage`、`TENANT_ALL` 和本租户门店归属。成功返回 `{ "available": true, "password": "..." }`；历史账号没有可还原副本时返回 `{ "available": false, "message": "旧密码无法还原，请先重新设置一次密码" }`。响应设置 `Cache-Control: no-store`。
- 内部对应接口为 `PUT /internal/v1/store-accounts/{userId}` 和 `POST /internal/v1/store-accounts/{userId}/reveal`，只接受正确内部密钥；租户、操作人和 userId 由商户服务从可信上下文和门店绑定生成。
- 新创建、重设或修改的门店密码在 IAM 中保留 BCrypt 登录校验值和 AES-GCM 加密副本。查看时校验副本与当前密码一致，并写入不含密码的查看审计。普通账号列表、编辑响应不包含密码。

## 门店共享库存与调拨

- `GET /api/merchant/v1/transfers/shared-stock?storeId=<申请店>&query=<商品/SKU>`：查看同租户其它营业门店的可用库存；不开放其它门店的库存修改权限。
- `POST /api/merchant/v1/transfers/requests`：提交 `transferNo`、`sourceStoreId`（供货店）、`targetStoreId`（本店）、`items[{skuId,quantity}]`、`deliveryMode`、收件资料及备注。
- `deliveryMode=STORE` 使用申请店地址，收货后入申请店库存；`OTHER` 必须填写 `recipientName`、`recipientPhone`、`deliveryAddress`，收货仍入申请店库存；`CUSTOMER` 必须关联 `orderId`，使用本店已支付待履约订单的完整商品与数量。
- 新申请状态为 `REQUESTED`。供货店使用 `/transfers/{id}/ship` 确认并扣减可用及实际库存；申请店使用 `/receive` 入库；出库前参与门店可 `/cancel` 取消或拒绝申请。
- 直寄客户复用原订单收款，供货店确认后出库即完成，无调拨入库。系统撤回原订单在申请店的库存扣减、完成原订单，并将财务记在原销售店。退款库存回补实际供货店。库存与订单同步失败会自动重试，重复处理不重复扣库或入账。
- 申请不预占库存，出库时再次校验，库存不足则整个出库事务失败。门店需要 `merchant:transfer:manage`；直寄还需 `merchant:order:view`。

## 租户商品、分配入库与低库存预警

以下路径均以 `/api/merchant/v1` 为前缀，租户和门店范围从登录身份取得。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/products/with-sku` | 原子创建并启用商品及首个 SKU；要求租户总部和商品管理权限 |
| GET | `/inventory/tenant-stock?storeId=` | 总部查看本租户各店库存，可按门店筛选；要求库存查看权限 |
| GET | `/inventory/allocations` | 总部分配入库记录；要求库存查看权限 |
| POST | `/inventory/allocations` | 总部分配数量；要求库存管理权限 |
| PUT | `/inventory/threshold` | 总部设置门店 SKU 预警线；要求库存管理权限 |
| GET | `/inventory/low-stock-alerts` | 总部返回旗下门店预警，门店仅返回授权范围内预警；要求库存查看权限 |

分配请求为 `{ "requestId": "唯一请求号", "reason": "补货", "items": [{ "storeId": 1, "skuId": 2, "quantity": 10 }] }`。数量必须为正，同一批次不得重复门店/SKU。整批事务提交；同一请求号和内容重试不会重复入库，内容不同返回 409。首次分配同时配置门店商品，不覆盖已有售价及销售设置。

预警线请求为 `{ "storeId": 1, "skuId": 2, "threshold": 5 }`。按可用库存小于等于预警线提醒，默认 5，设为 0 时仅提醒缺货。已配置但未入库商品按零库存计算，停用商品和门店不提醒。

门店不能自行正数调整库存，也不能确认含盘盈的盘点单；含盘盈盘点由总部确认。正常调拨收货及退款回补仍按原业务执行。


## M7 消费者 H5 接口变更

目录和认证接口公开，其余消费者接口必须携带独立消费者 JWT；后台员工令牌不允许使用。JWT 的品牌、会员身份、手机号与订单持久化所属会员共同决定访问权限，不能通过请求参数切换身份。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/consumer/v1/catalog/tenants/{tenantId}/brand` | 启用品牌的公开展示信息 |
| GET/PUT | `/api/merchant/v1/consumer-settings` | 总部 `merchant:store:manage` 配置品牌；版本冲突返回 409 |
| POST | `/api/consumer/v1/auth/register` | tenantId、mobile、memberName、password；创建新账号及会员，不自动认领历史会员 |
| POST | `/api/consumer/v1/auth/login` | tenantId、mobile、password；返回 accessToken、expiresIn（7200 秒） |
| GET | `/api/consumer/v1/wallet` | 本人会员和资产 |
| GET | `/api/consumer/v1/wallet/coupons`、`offers`、`ledgers` | 我的券、可领券、权益流水；流水 page 从 0 开始，每页 20 条 |
| POST | `/api/consumer/v1/orders/quote` | 使用创建订单相同结构，服务端校验库存和试算权益，不冻结 |
| GET | `/api/consumer/v1/orders?page=0` | 本人订单，每页 20 条 |
| GET | `/api/consumer/v1/orders/capabilities` | 模拟支付是否启用 |
| POST | `/api/consumer/v1/orders/{id}/cancel` | 本人未支付订单取消，释放库存与权益，重复取消幂等 |
| GET/POST | `/api/consumer/v1/orders/{id}/refund-request` | 查询/创建整单售后；POST reason 最多 500 字，当前只允许 COMPLETED |
| GET/POST | `/api/merchant/v1/orders/{id}/refund-request` | 所属门店查看/处理；POST approve、reply，要求退款权限 |

模拟支付默认关闭，仅在开发联调显式设置 `SIMULATED_PAYMENT_ENABLED=true`。现有订单 GET、支付、领券和会员接口同样要求消费者身份；GET 订单还校验持久化所属会员。手机号密码是独立账号方案，手机号未经过短信验证，历史会员绑定和密码找回尚待验证渠道接入。


## 2026-09-09：多商家目录与邮寄扩展

- `GET /api/consumer/v1/catalog/stores?latitude=&longitude=&area=&search=&page=0`：匿名门店目录，每页 20 条。经纬度须同时提供且为 WGS84；area 匹配城市、区县、门店地址，search 匹配店名。仅返回公开门店资料和直线 distanceKm（未知为 null），不返回经营数据。
- `GET /api/consumer/v1/catalog/tenants/{tenantId}/stores/{storeId}/fulfillment`：公开配送配置，校验有效租户与上架门店。
- `GET/PUT /api/merchant/v1/stores/{id}/storefront`：查询需 merchant:store:view，修改需 merchant:store:manage，均限制可信门店范围。修改完整提交 Settings 及 version；冲突返回 409。
- `POST /internal/v1/trade/shipping/quote`：内部密钥保护，提交 tenantId、storeId、method(PICKUP/SHIPPING)、province、items，返回 shippingFeeCents。该路由不通过公网网关转发。
- 消费者订单创建/试算新增可选 delivery：`{method:"SHIPPING",name,phone,province,address}`；address 为包含所选省份的完整地址。省份必须使用后台提供的标准省级名称。缺省为 PICKUP，原客户端兼容。服务端运费计入 payableAmountCents；试算新增 shippingFeeCents。
- 订单返回新增 delivery：method、shippingFeeCents、name、phone、province、address、carrier、trackingNo、shippedAt。收件信息属于订单私有数据，商家/消费者接口沿用原有鉴权。
- `POST /api/merchant/v1/orders/{id}/ship`：`{carrier,trackingNo}`，需 merchant:order:verify 且为订单所属门店账号，待发货→待收货。同一单号重放幂等，改变单号的重放返回 409。
- `POST /api/consumer/v1/orders/{id}/receive`：当前消费者确认自己的已发货订单，完成后重复请求幂等。
- 新状态 PENDING_SHIPMENT（待发货）、SHIPPED（待收货）。邮寄支付不生成自提码。旧自提、超时释放及整单售后接口继续使用；整单退款包含原邮费，运费使用独立财务业务类型。

## 统一消费者账号（2026-09-10）

2026-09-11 补充账号中心：`auth/account` 下新增 `activate`、`logout`、`me`、`password`、`addresses`、`addresses/{id}/delete`、`history`、`history/clear`、`privacy`。地址与足迹所有者取验证后的统一身份；各商家资产接口仍要求租户会员身份。连续 30 分钟未使用的会话需要重新验证，修改密码撤销新会话机制下该账号的全部登录。请求字段、方法、迁移与旧令牌兼容边界见 [账号中心设计](UNIFIED-CONSUMER.md)。

新增 `/api/consumer/v1/auth/account/register`、`login`、`memberships`、`enter`。注册与登录不再要求租户参数；会员关联与进店需要独立的全局消费者令牌。全局令牌不能调用商家资产或管理接口，进店返回原有租户范围消费者令牌。请求、响应及历史账号兼容方式见 [统一消费者账号](UNIFIED-CONSUMER.md)。
