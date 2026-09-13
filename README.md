# 商户智慧经营管理平台

面向连锁品牌、门店与消费者的多租户 SaaS 系统。项目覆盖租户管理、身份权限、门店经营、商品库存、交易履约、会员营销、经营分析和消费者商城，并提供本地开发、自动化测试及容器化基础设施配置。

> 当前项目适合本地开发、功能演示与二次开发。真实支付渠道和生产环境上线能力仍需结合实际商户资质、安全策略及基础设施进一步完善。

## 核心能力

- 多租户生命周期、套餐权益与数据隔离
- JWT 登录、RBAC、门店数据权限与审计
- 门店、员工、分类、SPU/SKU、价格和库存管理
- 下单、库存预占、支付、核销、退款与超时关闭
- 会员、积分、储值、优惠券及权益流水
- 平台端、商户总部端、门店端和消费者移动 H5
- 多商家发现、分店购物车、自提与配送履约
- 自动化测试、端到端验收和故障演练脚本

## 技术栈

| 范围 | 技术 |
| --- | --- |
| 后端 | Java 21+、Spring Boot 3.5、Spring Cloud、Spring Cloud Alibaba |
| 数据访问 | MyBatis-Plus、MySQL |
| 基础设施 | Redis、RabbitMQ、Nacos |
| 前端 | Vue 3、TypeScript、Vite、pnpm Workspace |
| 测试 | JUnit、Testcontainers、Vitest、Playwright |
| 部署 | Docker Compose、PowerShell 自动化脚本 |

## 项目结构

```text
.
├── backend/                  # Maven 多模块后端
│   ├── saas-gateway/         # API 网关
│   ├── saas-common-security/ # 公共安全组件
│   ├── tenant-service/       # 租户与套餐
│   ├── iam-service/          # 身份、账号与权限
│   ├── merchant-service/     # 门店、商品、库存与经营分析
│   ├── member-service/       # 会员与营销权益
│   └── trade-service/        # 订单、支付与履约
├── frontend/
│   ├── apps/                 # platform、merchant、store、consumer 应用
│   └── packages/             # 共享前端包
├── deploy/compose/           # 本地基础设施编排
├── scripts/                  # 启停、验收、迁移及演示数据脚本
├── performance/              # 性能测试
└── docs/                     # 设计、开发、部署与运维文档
```

## 环境要求

- JDK 21～25
- Maven 3.9+
- Node.js 22+
- pnpm 11.9+
- Docker 28+ 与 Docker Compose v2
- Windows PowerShell（使用仓库内的一键脚本时）

确认本地工具版本：

```powershell
java -version
mvn -version
node --version
pnpm --version
docker compose version
```

## 快速开始

### 1. 获取项目并准备配置

```powershell
git clone <your-repository-url>
Set-Location SAAS
Copy-Item .env.example .env
```

`.env.example` 仅提供本地开发模板。共享环境或生产环境必须替换所有 `change-me-*` 值。`.env` 已加入 `.gitignore`，请勿提交真实密钥。

### 2. 启动基础设施

确保 Docker Desktop 已运行，然后执行：

```powershell
docker compose --env-file .env -f deploy/compose/compose.yml up -d
```

该编排会启动 MySQL、Redis、RabbitMQ 和 Nacos。

### 3. 安装依赖

后端依赖由 Maven 管理：

```powershell
Set-Location backend
mvn dependency:go-offline
Set-Location ..
```

前端依赖由 pnpm 管理：

```powershell
Set-Location frontend
pnpm install
Set-Location ..
```

### 4. 启动后端

```powershell
.\scripts\start-backend.ps1
```

脚本会构建并在后台启动网关和业务服务，日志生成在 `runtime-logs/backend/`。代码未变化时可复用已有构建结果：

```powershell
.\scripts\start-backend.ps1 -SkipBuild
```

停止后端：

```powershell
.\scripts\stop-backend.ps1
```

### 5. 启动前端

```powershell
.\scripts\start-frontend.ps1
```

停止前端：

```powershell
.\scripts\stop-frontend.ps1
```

## 服务地址

| 服务 | 地址/端口 |
| --- | --- |
| API Gateway | `http://localhost:8080` |
| Tenant / IAM / Merchant / Member / Trade | `8081`～`8085` |
| 平台管理端 | `http://localhost:5173` |
| 商户总部端 | `http://localhost:5174` |
| 门店端 | `http://localhost:5176` |
| 消费者 H5 | `http://localhost:5177` |
| Nacos | `http://localhost:8848` |
| RabbitMQ 管理台 | `http://localhost:15672` |

所有后端服务均提供 `/actuator/health` 健康检查。后端默认设置 `NACOS_ENABLED=false` 以便单服务测试；完整联调时使用 `NACOS_ENABLED=true` 和 `NACOS_SERVER_ADDR=localhost:8848`。

## 开发与测试

后端测试：

```powershell
Set-Location backend
mvn test
```

前端检查、测试和构建：

```powershell
Set-Location frontend
pnpm typecheck
pnpm test
pnpm build
```

浏览器端到端测试：

```powershell
Set-Location frontend
pnpm test:e2e
```

仓库还提供 `scripts/m1-e2e.ps1` 至 `scripts/m4-e2e.ps1`，用于验证租户与权限、商品库存、交易履约及会员权益等完整业务链路。执行条件和验收范围参见[测试与验收文档](docs/VALIDATION.md)。

## 配置说明

主要环境变量记录在 `.env.example`，包括：

- MySQL、RabbitMQ 与 Nacos 凭据
- JWT 和内部调用签名密钥
- 平台管理员初始化账号
- 沙箱支付及第三方支付开关

沙箱支付仅用于本地联调。需要模拟支付时，可以这样启动后端：

```powershell
.\scripts\start-backend.ps1 -EnableSimulatedPayment
```

## 文档

- [开发文档](docs/DEVELOPMENT.md)：架构、业务流程与开发基线
- [API 概览](docs/API.md)：接口与权限要求
- [部署手册](docs/DEPLOYMENT.md)：环境配置、发布与升级
- [运维手册](docs/OPERATIONS.md)：日常检查、故障处理和账号维护
- [测试与验收](docs/VALIDATION.md)：测试命令、发布门禁与验收范围
- [消费者 H5](docs/CONSUMER-H5.md)：商城能力与开发说明
- [支付设计](docs/PAYMENTS.md)：支付边界与实现说明
- [统一消费者账号](docs/UNIFIED-CONSUMER.md)：账号关联与隔离设计

## 安全提示

- 不要提交 `.env`、访问令牌、生产密码或支付密钥。
- 首次部署必须修改模板中的默认凭据。
- 生产环境应关闭沙箱支付和管理员自动初始化。
- 对外部署前请完成 HTTPS、密钥托管、访问控制、备份及监控配置。

## 贡献

欢迎通过 Issue 报告问题，或通过 Pull Request 提交改进。提交代码前请确保后端测试、前端类型检查和相关端到端测试通过，并避免提交构建产物、依赖目录或本地配置。

## 许可证

本项目基于 Apache License 2.0 开源。你可以在遵守许可证条款的前提下使用、复制、修改和分发本项目，详细内容请参阅 [LICENSE](LICENSE)。
