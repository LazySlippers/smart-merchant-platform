# 运维手册

## 日常检查

1. 检查 Docker：`docker compose --env-file .env -f deploy/compose/compose.yml ps`。
2. 检查 Gateway 与 8081–8085 `/actuator/health`。
3. 检查 `runtime-logs/backend` 是否有连续 5xx、数据库连接耗尽或 Outbox 重试。
4. 检查 `saas_trade.analytics_outbox`、`saas_member.analytics_outbox` 未发布数量和最老创建时间。
5. 检查 MySQL 容量、慢查询、Redis 内存和 RabbitMQ 积压。

## 启停

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start-backend.ps1 -SkipBuild
powershell -ExecutionPolicy Bypass -File scripts/stop-backend.ps1
docker compose --env-file .env -f deploy/compose/compose.yml stop
```

停止基础设施前先停止业务服务。恢复顺序为 MySQL/Redis/RabbitMQ/Nacos，确认健康后再启动业务服务和 Gateway。

## 备份与恢复

每日对六个 `saas_*` 数据库做一致性备份并加密保存；至少每季度执行一次恢复演练。恢复后依次验证 Flyway 版本、租户数量、库存余额、订单/财务流水、会员权益和分析事实数量。

## 告警建议

- 5xx 比例连续 5 分钟超过 1%。
- API P95 超过 1 秒或 P99 超过 2 秒。
- Outbox 最老未发布事件超过 2 分钟。
- 数据库连接池使用率超过 80%、磁盘超过 75%。
- 任一服务健康状态 DOWN、容器反复重启或敏感字段进入日志。

## 故障处理

- RabbitMQ：服务仍以数据库 Outbox 保持业务事务；恢复 RabbitMQ 后确认积压下降。
- Analytics：交易不回滚，Outbox 记录失败次数；恢复后确认 `published_at` 和 Inbox 唯一事件。
- MySQL：请求必须明确失败，禁止返回伪成功；恢复后等待连接池重连并检查所有健康端点。
- Redis：租户运行态校验不可用时拒绝受保护请求；恢复后重新验证登录与停用租户拦截。

演练命令：`scripts/m6-outbox-drill.ps1 -Execute` 与 `scripts/m6-database-drill.ps1 -Execute`。

### 更新代码后的本地重启

在项目根目录运行 `powershell -ExecutionPolicy Bypass -File scripts/start-backend.ps1`。默认流程会停止本项目 6 个后端进程、等待退出、构建并启动，然后检查 8080–8085 的健康状态；本地接口会短暂中断。`-SkipBuild` 仅适用于已有可用构建且代码未变化的情况，不负责加载修改。

`backend-processes.ps1` 按完整 JAR 路径识别当前项目的服务。启动脚本记录全部匹配进程，包含复用的服务；停止脚本也按实际路径发现进程，不依赖可能遗漏或过期的 `processes.json`。数据库等 Docker 基础设施由操作者单独管理。

### Windows 构建提示 Unable to rename JAR

症状：Spring Boot `repackage` 无法把 `target/*.jar` 重命名为 `.jar.original`。本次故障由正在运行的 Java 服务占用 JAR 引起。

处理：使用上述默认启动命令，它会先释放文件占用。若需要手动构建，先执行 `scripts/stop-backend.ps1`，再构建，最后用 `scripts/start-backend.ps1 -SkipBuild` 启动。构建失败时脚本停止后续启动，应排查构建日志并重新运行；不要把跳过构建当成部署新代码的方法。

### 门店账号配置错误

IAM 错误分派不再被认证规则覆盖成 401。商户组织接口将下游账号重名映射为 409 和“登录账号已被使用，请更换账号后重试”，下游参数错误映射为 400；其余下游 HTTP 错误返回 502 和账号服务暂不可用提示。检查账号重名、参数及 IAM 服务日志，不要把下游认证错误当成商户需要重新登录。

### 门店密码检视与密钥

IAM 使用 AES-GCM 保存可检视的门店密码副本，随机 nonce，并绑定租户 ID 和用户 ID；登录仍使用 BCrypt。`STORE_CREDENTIAL_KEY` 可配置独立加密密钥，本地启动脚本会读取 `.env` 中该项；未设置时从内部签名密钥按独立用途派生。部署时应使用稳定的高熵密钥，并将密钥与数据库分开保管和备份。

改动密钥会导致已有副本无法解密；当前没有自动密钥轮换迁移，变更前需另行迁移密文或安排重新设置密码。旧账号只有哈希，无法还原原密码，必须重新设置一次。IAM 的 `store_credential_audit` 记录账号编辑及成功查看的操作者，不记录密码。查看响应不缓存；页面明文在 30 秒后、窗口失焦及退出页面时清除。

## 产品演示说明

### 启动

按 [部署手册](DEPLOYMENT.md) 启动 Docker 与后端。平台端、租户端、门店端分别运行 `pnpm dev:platform`、`pnpm dev:merchant`、`pnpm dev:store`。各端仅使用账号、密码登录。

### 推荐演示链路

1. 提交品牌入驻申请，平台管理员审核并选择套餐。
2. 使用品牌老板账号登录，创建门店、员工、角色、商品、SKU 和库存。
3. 发起跨店调拨并展示双方不可变库存流水。
4. 注册会员、调整积分/储值并发布优惠券。
5. 顾客选店选品、领券、创建自提订单并模拟支付。
6. 门店核销员使用最小权限账号核销，展示库存实扣与财务流水。
7. 发起退款，展示库存、优惠券、积分和储值回退。
8. 打开经营大屏，查看订单、净收入、商品与门店排行。
9. 执行 `scripts/m6-security-e2e.ps1`，展示跨租户与伪造请求头被拒绝。

### 自动演示与验收

```powershell
powershell -ExecutionPolicy Bypass -File scripts/m4-e2e.ps1
powershell -ExecutionPolicy Bypass -File scripts/m6-security-e2e.ps1
cd frontend
pnpm test:e2e
```

自动化数据均使用时间戳生成唯一业务号，不依赖固定数据库主键。演示环境中的密码和租户号不得用于生产。

### 自助升级与门店账号演示

1. 用具有门店管理权限的租户总部管理员登录商户端。
2. 点击顶部“升级套餐”，或门店额度用完提示中的同名入口；查看当前套餐、额度和可升级套餐。
3. 选择高级版并确认。套餐和门店额度立即更新，无需联系平台管理员；当前流程不收款。没有可升级项时显示“当前已无可升级套餐”。
4. 在门店管理中点击“配置账号”，使用建议的 `store-{门店ID}-admin` 或自定义用户名，填写名称和至少 8 位密码并保存。
5. 已配置账号点击“修改账号”可更改登录名、账号名称和密码；密码留空则保留。点击密码旁的小眼睛显示，再点隐藏，30 秒后或离开窗口自动隐藏。旧账号需先设置一次密码才能查看。账号权限入口可继续配置门店功能。若用户名被占用，按提示更换，不会自动覆盖已有账号。

升级会实际修改当前演示租户套餐，门店账号保存会实际创建账号；优先使用专用演示租户。相关接口及权限要求见 [API 概览](API.md)。
