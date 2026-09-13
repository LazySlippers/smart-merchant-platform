# 消费者 H5 后端接入与验收

后续更新：已按要求清除旧租户，建立单一悦享百货，直接打开 `/` 即可。本文的旧验收租户 ID 已失效，当前数据和测试入口以 [商场重建记录](MALL-REBUILD.md) 为准。

2026-09-08。默认 H5 使用真实业务接口；仅 `preview=1` 进入本机演示。

## 启动与入口

在 `E:\project\SAAS` 执行：

```powershell
.\scripts\start-backend.ps1 -EnableSimulatedPayment
.\scripts\start-frontend.ps1
```

访问 `http://127.0.0.1:5177/?tenantId=品牌ID`，指定门店追加 `&storeId=门店ID`。总部「消费者商城」可查看品牌 ID 和配置展示信息。缺少有效品牌参数时显示入口说明，不猜测品牌、不读取演示资产。

当前隔离验收品牌：`1788781303132001`。它是测试数据，不应作为生产品牌。
`-EnableSimulatedPayment` 仅用于本地联调；默认关闭模拟支付。支付未开放时订单可以查看和取消，不能伪造支付成功。

## 功能对应

| 功能 | 真实接入方式 |
| --- | --- |
| 品牌与门店 | 总部配置品牌名称、主题、标题、客服；读取当前品牌门店目录 |
| 分类、搜索、详情与规格 | 门店可售 SKU、配置价格、实际库存；不使用演示杯型、加料价格 |
| 购物袋、再来一单 | 按品牌与门店本地保存；结算重新校验库存价格；后端确认金额 |
| 注册登录 | 品牌消费者账户和 JWT；无法用手机号直接冒领历史会员资产 |
| 优惠券 | 后端已发布券领取、门槛、有效期、冻结、使用与退还 |
| 积分与储值 | 实时余额与流水，结算抵扣并由后端冻结、确认、释放 |
| 订单 | 服务端试算、幂等创建、模拟支付、取消、历史与分页 |
| 取货码与门店核销 | 支付后生成码，门店工作人员核销，H5 刷新真实状态 |
| 退款售后 | 已完成订单提交申请，门店审核；退款结果与权益恢复 |
| 地址 | 后端增删改，默认标记，按品牌和登录会员隔离，最多 20 个 |
| 收藏 | 后端保存 SKU；当前门店展示可查询的收藏商品，最多 200 个 |
| 昵称与消息偏好 | 后端保存；站内消息由真实订单生成，不发送短信或系统推送 |
| 帮助与反馈 | 品牌客服电话；反馈保存至后端，总部「消费者商城」可查看并标记处理 |
| 登出与异常 | 清理账户视图；401 提示重新登录；断网可重试；丢失下单响应复用请求 ID |

地址、收藏、偏好和反馈新增 `/api/consumer/v1/profile/**` 接口；消费者身份只取自 JWT。总部反馈入口 `/api/merchant/v1/consumer-feedback` 使用会员查看 / 管理权限并限定品牌。新增迁移 `V202609080900__consumer_preferences.sql`。

## 当前业务边界

按本次确认，尚无支付商户及配送配置，因此真实微信支付、在线充值、礼品卡交易和外送下单未开放。页面显示未开通，不扣款、不生成虚假成功状态。现有储值可用于真实订单抵扣。

纯演示中的自由加料、预约时间、积分换券活动和会员等级成长规则不作为真实业务承诺；正式版使用门店 SKU 与已发布优惠活动。手机号注册仍是密码账户，尚未完成短信归属验证。

## 可重复验证

在 `frontend`：

```powershell
pnpm --filter @smart-merchant/consumer-web build
pnpm --filter @smart-merchant/consumer-web test
pnpm exec playwright test --config playwright.consumer.config.ts
$env:H5_E2E_TENANT='1788781303132001'
pnpm exec playwright test --config playwright.consumer.config.ts
```

真实测试会在隔离验收品牌创建新会员、优惠券和订单。不要针对生产品牌运行。所需测试品牌和门店账号可由 `scripts/m3-e2e.ps1` 创建；`H5_E2E_SUFFIX` 可覆盖该脚本的账号后缀。

在 `backend`：

```powershell
mvn -pl member-service,trade-service,merchant-service,tenant-service -am test
```

`scripts/m4-e2e.ps1 -BaseUrl http://127.0.0.1:8080` 验证真实资产冻结、支付确认、退款返还和库存恢复。

浏览器覆盖文件：`h5.spec.ts`（核心与异常）、`h5-real.spec.ts`（门店订单闭环）、`h5-profile-real.spec.ts`（地址收藏设置反馈）、`h5-benefits-real.spec.ts`（领券、抵扣与取消）、`prototype.spec.ts`（隔离演示）。截图保存在 `frontend/apps/consumer-web/qa`，本次后端日志在 `runtime-logs/consumer-connected-backend-tests.log`。

## 本次验收结果

- 最终浏览器回归：13 / 13 通过，包含真实消费者下单到门店核销退款，以及总部页面处理反馈后 H5 显示「已处理」。日志：`runtime-logs/consumer-connected-browser-tests.log`。
- 消费者领域单元测试：3 / 3 通过，涵盖大整数 ID、库存数量校验及非法品牌入口。
- 品牌、商品、会员、交易与公共安全后端套件构建成功，零失败；一个已有可选数据库用例跳过。新增账户 / 地址 / 收藏 / 反馈测试单独最终复验 3 / 3 通过，含跨品牌、跨会员及门店越权检查。
- 消费者与总部前端生产构建通过。真实 M4 权益联调通过，覆盖冻结、支付确认、退款权益返还、库存恢复。
- 最终后端重新构建启动，六个服务健康检查均为 UP，前端 5177 和总部 5174 可访问。

本地迁移在首轮联调中将内联索引改为等价的 `CREATE INDEX`，以兼容 H2 测试库；仅对本次新迁移执行了限定版本的 Flyway checksum 校准，未修改业务数据，重启验证已通过。
