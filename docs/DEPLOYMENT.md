# 部署手册

## 环境要求

- Windows 11 或 Linux，Docker 28+、Docker Compose v2
- JDK 21+、Maven 3.9+
- Node.js 22+、pnpm 11.9+
- 可用端口：8080–8085、5173、5174、5176、13306、6379、5672、8848

## 配置

从 `.env.example` 创建 `.env`，至少设置 MySQL、RabbitMQ、JWT、内部签名和平台管理员密码。生产环境不得沿用示例密钥；JWT 与内部签名密钥至少 32 字节，并通过密钥管理系统注入。

## 本地/验收环境部署

```powershell
docker compose --env-file .env -f deploy/compose/compose.yml up -d
powershell -ExecutionPolicy Bypass -File scripts/start-backend.ps1
cd frontend
pnpm install
pnpm build
```

健康检查为 Gateway `http://localhost:8080/actuator/health` 以及业务服务 8081–8085 的 `/actuator/health`。全部返回 `UP` 后才能开放流量。

启动脚本会按当前项目的完整 JAR 路径识别旧 Java 服务，停止并等待退出后执行 `mvn package -DskipTests`，再启动全部后端。构建不执行测试，发布前仍需单独完成测试门禁。代码未变化时可使用 `-SkipBuild` 复用健康服务；代码更新后应执行默认构建启动流程。Windows 下不要在服务仍运行时直接重新打包其 JAR，详见 [运维手册](OPERATIONS.md)。

## 前端发布

三个应用的产物位于各自 `dist`：platform-web、merchant-web、store-web。智慧大屏无需单独部署或启动端口。使用 Nginx 或对象存储静态托管，所有 `/api` 请求反向代理至 Gateway 8080；HTML 启用 no-cache，带内容哈希的静态资源启用长期缓存。

## 生产要求

- MySQL 使用独立实例/账号和定期备份；Redis 启用认证与持久化。
- Nacos 必须启用认证并使用专用服务账号。
- Gateway 置于 TLS 终止层后，只向外暴露 Gateway 和前端。
- 8081–8085、MySQL、Redis、RabbitMQ、Nacos 仅允许内网访问。
- 每次发布先备份数据库，再执行 Flyway；禁止手工修改已执行迁移。

## 回滚

应用回滚使用上一版本 JAR/前端产物并重启。数据库只允许向前兼容迁移；涉及破坏性结构变更时使用“新增字段→双写→切换→后续清理”，不直接回滚已执行的 Flyway 版本。



## 门店密码检视配置

本次 IAM 启动会执行 `V202609061900__store_credentials.sql`，新增密文表及审计表，不修改历史密码。可在 IAM 环境配置 `STORE_CREDENTIAL_KEY`，本地脚本也支持从 `.env` 加载。密钥需要稳定保存，备份恢复及轮换规则见 [运维手册](OPERATIONS.md)。旧门店账号第一次使用小眼睛前需重新设置密码。

## 分析服务合并升级

分析代码、接口及新建分析表均由 merchant-service（8083）承载，不再构建或启动 analytics-service（8086）。原有查询路径保持不变。

已有环境升级时，先备份 saas_analytics 和 saas_merchant，停止旧分析服务及订单、会员事件生产服务。启动新版 merchant-service 完成 Flyway 建表后，用具备两库权限的数据库管理员执行 scripts/migrate-analytics-to-merchant.sql（不要使用忽略错误模式；出错应回滚）。目标分析表必须为空。核对六张分析表的行数与营收汇总后，再启动新版网关、订单和会员服务。全新环境无需迁移。旧库暂留用于核对，不再写入；不要把旧事件重新发布到已有事实表。

## 同租户调拨升级

重新构建并重启 merchant-service 与 trade-service，Flyway 自动应用 `V202609062100` 的调拨配送信息及订单履约字段。门店端发布新版构建。门店账号需启用“调拨收发”；直寄同时需要“销售订单查看”，授权后点击“同步权限”。直寄使用现有已支付待履约订单，按整单商品与数量申请，不新增支付。

## 租户分配库存升级（2026-09-07）

重新构建并重启 merchant-service，Flyway 自动执行 `V202609062200__tenant_inventory_allocation.sql`，新增门店 SKU 预警线和幂等分配记录。既有库存数量不变，默认预警线为 5。发布 merchant-web、store-web 新版产物。

总部“商品与库存”需要库存查看权限；分配、预警线设置和盘盈确认需要库存管理权限，维护商品需要商品管理权限。两端低库存提醒在页面可见时每 15 秒刷新，业务操作后立即刷新；当前通知为页面提醒。
