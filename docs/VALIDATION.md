# 测试与验收

## 2026-09-13 支付提供方流程

- `mvn -pl trade-service -am test`：20 项通过，包含支付单幂等、回调事件去重、沙箱确认、迟到通知关单和跨租户隔离。
- `pnpm --filter @smart-merchant/consumer-web build`：类型检查与生产构建通过。
- `pnpm exec playwright test --config=playwright.consumer.config.ts tests/consumer/h5.spec.ts`：真实 Chromium 16 项全部通过，页面完成支付单创建、沙箱确认、订单回查并展示支付成功结果。
- 未接入真实商户密钥；微信、支付宝仅保留禁用的配置与适配器入口。

本文集中维护验证步骤和历史验收记录。所有命令从仓库根目录运行；涉及真实接口的验证需先启动基础设施、Gateway 与 5 个业务服务。分析模块现由 merchant-service 承载。

历史记录反映当时的版本与环境，不代表当前代码已重新通过相同测试。

## 自动化门禁

```powershell
# 后端单元、集成和安全测试
mvn -f backend/pom.xml test

# 使用真实 MySQL、Redis、RabbitMQ 容器的集成门禁
mvn -f backend/pom.xml -pl merchant-service -Dm6.testcontainers=true -Dtest=InfrastructureContainersTests test

# API 越权和跨租户安全测试
powershell -ExecutionPolicy Bypass -File scripts/m6-security-e2e.ps1

# 平台开通到经营大屏浏览器 E2E
cd frontend
pnpm install
pnpm exec playwright install chromium
pnpm test:e2e
```

安全脚本验证平台/商户身份域隔离、伪造可信请求头、内部密钥以及跨租户资源读取。浏览器失败时，Playwright 会保留 trace 和截图。

## 性能基线

推荐使用 Docker 中的固定 k6 版本执行。脚本会自动创建隔离租户、商品、库存、优惠券和最小权限核销员：

```powershell
docker run --rm --env-file .env -e VUS=2 -e DURATION=30s `
  -v E:\project\SAAS\performance\k6:/scripts:ro `
  grafana/k6:0.54.0 run /scripts/m6-smoke.js
```

默认门禁是错误率低于 1%、P95 小于 1 秒、P99 小于 2 秒。脚本覆盖登录、目录、会员注册、领券、下单、支付、最小权限核销和经营大屏；每次运行创建独立租户，不复用共享演示数据。

## 故障与补偿演练

`scripts/m6-outbox-drill.ps1` 与 `scripts/m6-database-drill.ps1` 默认只展示状态；传入 `-Execute` 才会短暂停止并自动恢复对应容器。演练期间交易事件先持久化至 Outbox。恢复后检查：

1. `analytics_outbox.published_at` 最终被填写，失败阶段 `attempts` 有记录。
2. 同一 `event_id` 重放后，`analytics_inbox` 仍只有一行。
3. 分钟/日聚合与事实表校准结果一致。
4. 数据库不可用时请求明确失败，不返回伪成功；数据库恢复后迁移与健康检查通过。

## 发布判定

单元测试、Testcontainers、API 安全门禁、Playwright 和 k6 阈值必须全部通过。故障演练必须记录开始/恢复时间、积压数量、补偿耗时和最终一致性结果；任何跨租户读取或权限提升都直接阻断发布。

实际执行结果见下方历史验收记录。

## 历史验收记录

### 2026-09-11 本地双租户公开商城验收

- 扩展 `scripts/seed-department-store.py`，保留悦享百货并新增独立租户“森野花房 · 湖滨店”；脚本连续执行两次后仍为 24+6 个 SPU，未重复创建。
- 真实 Gateway 与浏览器用例验证杭州地区同时展示两店、封面加载、百货与鲜花园艺目录互斥、统一购物车生成两张门店卡片。
- 同一统一消费者账号分别进入两个租户并创建真实自提订单；订单响应的 tenantId、storeId 和商品快照分别属于悦享百货与森野花房，未发生串店。
- Playwright 证据：`frontend/apps/consumer-web/qa/multi-tenant-region-home.png`、`multi-tenant-carts.png`。

以下内容保留原始执行日期、结果与限制；旧记录中的独立分析服务和四个前端应用属于当时架构。当前部署方式见 [部署手册](DEPLOYMENT.md)。

### M6 发布验收记录

执行时间：2026-08-27，环境：Windows、Docker Desktop 28.5.1、MySQL 8.4、Redis 7.4、RabbitMQ 4、JDK 25（`--release 21`）。

| 门禁 | 结果 | 关键证据 |
|---|---|---|
| 后端测试 | 通过 | 22 份报告、40 项测试，0 失败、0 错误；常规套件中容器门禁按设计跳过 |
| Testcontainers | 通过 | MySQL、Redis、RabbitMQ 均真实启动并可连接 |
| Playwright | 通过 | 平台汇总、新租户审核后进入经营大屏，2/2 通过 |
| API 安全 | 通过 | 身份域、伪造头、内部密钥、跨租户、敏感日志 5 项通过 |
| 前端构建 | 通过 | platform/merchant/store/dashboard 四应用构建成功 |
| k6 全链路 | 通过 | 49/49 检查，错误率 0%，P95 339.92ms，P99 小于 2s |
| RabbitMQ 演练 | 通过 | 停止期间 Gateway 保持健康，容器恢复 healthy |
| MySQL 演练 | 通过 | 数据请求明确失败；恢复后 8081–8086 全部 UP |
| Outbox 补偿 | 通过 | Analytics 停止时 attempts 从 0 增至 4+；恢复后 published，Inbox=1 |
| 重复事件 | 通过 | 同一 eventId 首次 accepted=true，重复投递 accepted=false |

已知非阻断项：前端单包超过 500kB，后续可按路由拆包；Flyway 对 MySQL 8.4 输出版本范围提示，但所有迁移与 Testcontainers 已通过。

#### 2026-08-28 全阶段复核

- M1、M2、M3、M4 真实 API 验收脚本全部通过；脚本已兼容 PowerShell 7 的字节响应体和 `HttpResponseMessage` 错误响应。
- M5 的订单、退款、会员及营销事件 Outbox、Inbox 去重、事实表、日终校准、经营指标和大屏入口均已复核。
- M6 API 安全门禁 5/5、Playwright 2/2 再次通过；后端 22 份报告共 40 项测试零失败，前端类型检查、单测和四应用构建通过。
- 已移除前端开发态硬编码测试账号与密码，并修复前端启动脚本，使其直接托管 Vite 进程、记录真实 PID，并使用明确的 IPv4 健康检查地址。
- 最终运行状态：Docker 四项基础设施健康，Gateway 与六个业务服务均为 `UP`，四个前端端口均返回 HTTP 200。

### 功能回归与登录界面验收（2026-09-07）

本次检查当前平台、租户、门店三端及在用后端模块。历史已撤销 analytics-service 的残留测试报告不计入结果。

#### 验证结果

| 范围 | 方法与结果 |
|---|---|
| 身份、权限、租户隔离、账号密码配置 | 后端测试通过；真实平台及新租户登录通过 |
| 平台入驻审核、租户开通、经营汇总 | 2 项真实浏览器测试通过 |
| 门店范围、商品发布、品牌/门店价格、销售范围 | M2 真实接口验收通过 |
| 库存幂等、并发扣减、盘点、调拨出入库及越权拒绝 | M2 真实接口及后端测试通过 |
| 租户分店分配、低库存预警、预警知晓 | 5 项浏览器测试及后端测试通过 |
| 自主调拨、直寄客户、订单商品搜索、供货出库 | 4 项浏览器测试及后端测试通过 |
| 订单、模拟支付、库存预占、门店核销、财务流水、超时关闭 | M3 真实接口验收通过 |
| 会员积分、储值、优惠券、支付确认、退款权益及库存回补 | M4 真实接口验收通过 |
| 登录界面桌面与手机、账号密码提交、空表单拦截 | 6 项浏览器测试通过 |
| 前端类型、单元测试、打包 | 三端类型检查和构建通过，1 项前端单元测试通过 |

在用模块 Maven 共 75 项测试：74 通过，1 项显式启用的 Testcontainers 基础设施测试未执行。17 项浏览器测试通过，其中登录/库存/调拨使用模拟 API，平台与新租户流程连接本地真实服务。M2、M3、M4 使用新建验收租户，并保留测试数据供核对。当前验证不是穷举所有输入或第三方生产支付验收；支付仍使用项目现有模拟支付。构建仍提示资源包较大，不影响本次构建通过。

#### 登录界面

共用 LoginScreen 组件。桌面采用品牌介绍与登录表单双栏，手机收起介绍区域。三端显示各自身份说明，门店使用绿色强调色。只输入账号与密码，支持密码显隐、浏览器自动填充、回车提交、提交加载和空值/重复提交拦截。

修正旧浏览器验收脚本的租户编号输入及已变更页面定位；默认 Playwright 配置补上门店服务，调拨测试采用明确门店地址。当前本地 Vite 开发服务刷新即可显示，生产构建位于各端 dist。

日志：runtime-logs/full-function-tests.log、full-frontend-build.log、full-live-ui-tests.log、full-m2-check.log、full-m3-check.log、full-m4-check.log。


## 2026-09-07 消费者 H5（M7）开发联调验收

- 全前端 `pnpm typecheck`、`pnpm build` 通过。消费者生产包 JS 约 96 KB，gzip 约 36 KB（构建版本会变化）；既有后台大包警告仍存在。
- `consumer-web` 的 3 项边界单测通过：64 位 ID、购物车库存/失效数据、品牌入口校验。
- `playwright.consumer.config.ts` 的 4 项夹具测试通过：手机选购/登录/试算/支付/刷新、切店隔离、无效入口、丢失下单响应后同幂等键重试并取消。
- 后端全模块测试及打包通过；后续试算修复再次执行 trade-service 及公共依赖测试和打包。新增品牌配置 2 项、消费者账户 3 项；订单生命周期累计 12 项，覆盖所有权、历史订单拒绝认领、退款审核幂等和试算不占库存。
- 更新后的 M3/M4 脚本在真实 MySQL、Redis、Gateway 和业务服务环境通过。M4 已改为消费者发起退款、商户审核；M3 增加不同消费者无法读取前一消费者订单检查。
- 实际 H5 浏览器用例通过，未拦截接口：注册→选品→试算→下单→模拟支付→所属门店核销→H5 看到完成→提交售后→商户审核→H5 看到退款。
- 首次真实 H5 试算失败：传入会员接口的关联编号为 0，违反其正数契约。改为独立正数关联编号，新增不创建订单/不预占库存回归测试，再次真实验收通过。
- 本机 localhost 存在连接超时，IPv4 回环正常。开发启动脚本及前端代理使用 127.0.0.1；数据库和 Redis 未更换。

真实浏览器测试需先运行 M3 生成验收品牌，随后在 frontend 执行：

```powershell
$env:H5_E2E_TENANT='M3 输出的 tenantId'
pnpm exec playwright test --config playwright.consumer.config.ts h5-real.spec.ts
```

该用例会修改验收品牌的展示配置、创建测试消费者与订单，并完成退款；不可指向经营中的真实品牌。未设置环境变量时跳过，不影响常规夹具测试。

结果文件：`runtime-logs/consumer-backend-tests.log`、`consumer-quote-tests.log`、`consumer-frontend-build.log`、`consumer-m3-e2e.log`、`consumer-m4-e2e.log`。手机截图位于 `frontend/apps/consumer-web/qa/`。真实支付、真机 Safari/微信兼容、生产域名与短信验证未验收，不能据此宣称生产上线完成。


## 2026-09-10：多商家 H5 与邮寄验收

- 消费者/总部/门店三个前端 TypeScript 检查及生产构建通过，日志 `runtime-logs/marketplace-build.log`。
- merchant/trade 全量回归：55 项通过，1 项 Docker Testcontainers 用例跳过；最终新增真实 Servlet 错误转发回归另 1 项通过。首次真实联调发现业务 400 被错误转发覆盖为 401，现已仅允许内部 ERROR dispatch，直接请求受保护接口和 `/error` 仍需鉴权。最终 23 项目录/订单/HTTP 状态专项回归通过并重新打包。
- 消费端 Playwright：14 项通过、6 项 opt-in 真实环境用例跳过（`runtime-logs/marketplace-browser-tests.log`）；门店发货 Playwright 1 项通过。覆盖跨租户购物车、长 ID、登录恢复、定位授权/失败、地址与邮费、支付、自提和确认收货；修复发货后刷新列表导致详情关闭的问题。
- 启动本机 Docker 基础设施及各服务后，两项 V202609090900 迁移已在真实 MySQL 执行。
- `python scripts/marketplace-e2e.py` 真实 Gateway→merchant/member/trade 联调通过（`runtime-logs/marketplace-real-e2e.log`）：两个独立租户、三家门店，距离排序、禁运地区返回 400、租户隔离、三件商品 54 元+12 元运费、模拟支付、非所属门店发货拒绝、所属门店发货、本人收货及重复请求、运费独立入账、整单退款与库存恢复、停用租户从公开目录消失。脚本最后停用自己创建的验收租户，不修改经营租户。
- 实际 H5 首页与现有悦享百货门店读取成功，24 款商品、8 个业务分类（另有“全部商品”），无脚本错误与横向溢出。截图：`frontend/apps/consumer-web/qa/marketplace-live-home.png`、`marketplace-live-store.png`。
- 本地入口 `http://localhost:5177/`。当前沿用模拟支付；正式支付、短信验证、物流轨迹、统一跨商家身份不在本次验收内。未配置坐标的门店不显示距离；商家需在消费者商城中配置坐标及邮寄运费后启用。
# H5 自动支付验证（2026-09-10）

- H5 自动化回归：14 项通过，覆盖自动支付、失败重试、响应丢失恢复、金额变化保护和服务端支付关闭。
- consumer-web 生产构建通过。
- `python scripts/marketplace-e2e.py` 真实接口验收通过：支付后、发货前后台统计 paidOrders=1、grossRevenueCents=6600（商品54元、运费12元），并完成发货、收货、退款与库存恢复。独立验收商家已停用。
- 日志：`runtime-logs/h5-payment-revenue-e2e.log`、`runtime-logs/h5-auto-payment-build.log`。

## 2026-09-10：统一消费者账号

设计与升级说明见 [统一消费者账号](UNIFIED-CONSUMER.md)。本次验证结果：后端相关模块 **44 项通过**（公共安全 3、网关 6、会员 19、交易 16），消费者领域单元测试 **4 项通过**，移动端浏览器回归 **17 项通过**，消费者类型检查和 Vite 发布构建通过。

后端命令（项目使用本地 Maven 缓存）：

```powershell
# 在 backend 目录运行
mvn -o "-Dmaven.repo.local=E:\project\SAAS\.m2" -pl member-service,saas-gateway,trade-service -am test -q
```

最后新增网关拒绝用例后单独重跑 `-pl saas-gateway -am test`，通过。日志为 `runtime-logs/unified-account-tests.log` 与 `runtime-logs/unified-gateway-tests.log`，详细结果在各模块 `target/surefire-reports`。

前端命令（在 frontend 目录运行）：

```powershell
node node_modules/vue-tsc/bin/vue-tsc.js --noEmit -p apps/consumer-web/tsconfig.json --incremental false
node node_modules/@playwright/test/cli.js test --config=playwright.consumer.config.ts unified-account.spec.ts h5.spec.ts
# 在 frontend/apps/consumer-web 目录运行
node ../../node_modules/vitest/vitest.mjs run src/domain.test.ts
node ../../node_modules/vite/bin/vite.js build
```

新增覆盖：统一令牌不能直接访问资产或管理接口；租户令牌不能换取另一租户身份；会员列表不采信请求中的账号 ID；并发首次进入只创建一个会员与一组钱包；历史会员必须验证原密码；已有线下会员拒绝冒领；重复注册冲突；租户停用、全局密码锁定；同账号不同商家钱包、优惠券和订单权限隔离。浏览器覆盖一次登录进入两家商店、旧缓存不参与订单聚合、部分商家失败重试、分页去重、关联错误密码不退出统一账号，以及退出后清空私有数据。现有购买、支付恢复、运费及收货流程已回归。

截图：`frontend/apps/consumer-web/qa/unified-account-mobile.png`、`unified-orders-mobile.png`，已检查移动端布局。浏览器测试使用接口模拟，后端数据测试使用 H2 并实际执行 Flyway 迁移；本次没有执行真实 MySQL/Redis/Gateway 业务链路或真实数据库迁移。依赖隔离租户的 profile、benefits、mall、h5-real 浏览器脚本已适配新入口，尚未重跑真实环境。原生产上线门禁保持不变。

## 2026-09-11：消费者账号中心与多账号隔离

本轮最终验证通过：后端 **49 项**（公共安全 4、网关 6、会员 23、交易 16），消费者领域 **4 项**，移动端浏览器 **23 项**，Vue/TypeScript 检查及 Vite 发布构建。命令沿用上节，后端日志：`runtime-logs/consumer-center-tests.log`；各后端模块 `target/surefire-reports` 保存测试报告。

新增覆盖：服务端闲置 30 分钟后即使伪造客户端时间戳也不能激活；退出与修改密码撤销统一/商家会话；公共 JWT 解码器拒绝无效商家会话；地址读写/删除越权与默认地址接替；足迹去重、所有者隔离、分页、时间与关键词筛选；关闭足迹记录与清空不能影响其他账号；内部会话接口拒绝错误密钥。保留并发重复绑定及订单、余额、优惠券跨租户测试。

浏览器新增覆盖：取消添加账号保留原登录，快捷切换调用目标账号会话校验，另一账号看不到原账号的购物车，过期账号要求密码；密码输入错误保留当前登录，隐私开关实际保存；结算新增通用地址后另一商家复用；足迹分页/近 7 天/关键词筛选后进入对应 SKU，并重新获取当前价格。原购买、支付失败恢复、请求幂等、运费、收货和订单聚合流程通过回归。

截图：`frontend/apps/consumer-web/qa/unified-account-mobile.png`、`account-history-mobile.png`、`unified-orders-mobile.png`。已检查账号中心手机布局。测试使用 H2（实际执行 Flyway）及接口模拟浏览器，没有对当前真实业务数据库执行新增迁移，也没有重启实际服务或进行真实 MySQL/Redis 全链路验证。部署顺序与公共安全组件更新要求见 [账号中心设计](UNIFIED-CONSUMER.md)。

## 2026-09-13：租户自主入驻与隔离

- 商户登录页新增“自主申请入驻”，公开读取可申请套餐并提交品牌、负责人、手机号和初始密码；页面明确提示门店、商品、会员和订单按租户独立隔离。
- 公开申请不接收 `tenantId`。租户编号由平台审批时生成；负责人登录后，经营接口只使用服务端校验过的令牌租户上下文。
- 服务端新增手机号格式、字段长度校验，并拒绝同一手机号重复提交待审申请或为已有租户再次申请，冲突返回 HTTP 409。
- 租户服务测试覆盖重复申请冲突，并保留现有跨租户套餐、门店及经营数据隔离回归；商户端浏览器用例校验申请负载不含 `tenantId`。

## 2026-09-13：新门店商城上线、支付与中文错误

- 创建营业门店时由业务服务显式设置 `marketplace_listed=TRUE`、`pickup_enabled=TRUE`，不再依赖数据库隐式默认值；公开门店目录已确认能读取新建门店。
- 本地标准启动默认开启模拟支付，所有现有及新建门店均可下单支付；如需专门测试关闭状态，可使用 `start-backend.ps1 -DisableSimulatedPayment`。
- IAM 与消费者账号服务统一返回含中文 `message` 的错误体；平台、租户、门店登录前端对无正文的 401/404 也固定显示“账号或密码错误”。

## 2026-09-13：支付时间同步

- 本地启动脚本为网关及全部业务服务统一设置 JVM 时区 `Asia/Shanghai`。
- tenant、IAM、merchant、member、trade 五个服务的 MySQL 连接统一使用 `connectionTimeZone=Asia/Shanghai`，并强制数据库会话采用该时区，避免支付、订单和退款时间出现 8 小时偏差。
- `trade-service` 及依赖模块测试通过；全量后端重新构建并启动，六个服务健康检查均为 UP，实际进程参数已确认 JVM 与数据库连接时区生效。
