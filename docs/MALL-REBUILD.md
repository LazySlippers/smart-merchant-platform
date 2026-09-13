# 本地双租户商城初始化记录

## 2026-09-11 双租户扩展

`scripts/seed-department-store.py` 现可重复执行并初始化两个可公开展示的独立租户，各自拥有一个门店：

- 悦享百货 · 中心店：杭州市拱墅区，暖色百货封面，8 类、24 个 SPU、27 个 SKU，保留原有百货商品和账号。
- 森野花房 · 湖滨店：杭州市上城区延安路 258 号，绿色花房封面，鲜花花束、绿植盆栽、花器园艺 3 类，共 6 个独立商品与库存。

初始化状态仍写入 `runtime-logs/mall-seed.json`，账号写入 `runtime-logs/mall-accounts.json`；`second` 字段保存第二租户及门店标识。脚本通过公开入驻审批和商户业务 API 创建数据，不直接写业务数据库；重复运行不会重复创建商品。

真实浏览器验收位于 `frontend/tests/consumer/mall-real.spec.ts`。新增用例验证“杭州市”地区返回两家门店且两张封面加载；进入两店后目录互斥；两份购物车按租户+门店分别保存；同一统一消费者账号换取各租户身份后，真实创建的两笔订单分别返回正确的 tenantId、storeId 和商品快照。截图为 `multi-tenant-region-home.png` 与 `multi-tenant-carts.png`。

2026-09-08，按要求备份并清除本项目本地旧品牌、账号、商品和验收数据，重新建立一个百货商场。

## 访问

- 消费者：`http://127.0.0.1:5177/`，直接进入悦享百货，不需要扫码或参数。
- 商场总部：`http://127.0.0.1:5174/`。
- 门店：`http://127.0.0.1:5176/`。
- 初始账号和随机密码：`runtime-logs/mall-accounts.json`。总部账号 `13900000001`，门店账号 `yuexiang-store`。手机号为初始化占位账号，请在正式使用前完善联系方式和密码。

商场名暂定“悦享百货”，租户 ID `1788875508788001`；门店“悦享百货 · 中心店”，ID `1788875510383001`。商场名称、客服电话、门店真实地址可在后台修改。消费者默认商场配置在 `frontend/apps/consumer-web/.env.local`，生产部署需要保留该配置后重新构建。

## 数据

食品饮料、家居日用、个护美妆、服饰配件、数码家电、运动户外、母婴玩具、文具办公共 8 类，24 件示例商品、27 个 SKU，每个 SKU 初始化库存 100。价格与规格用于初始展示，正式经营前应按实际商品核对。另已创建“新客满 99 减 10 元”优惠券。

初始化全部通过正常业务 API 完成，商品、门店库存、消费者和总部数据一致。明细见 `runtime-logs/mall-seed.json`。消费者验收会留下已退款订单，库存已返还；不代表真实交易。

## 备份和清理范围

备份目录：`runtime-logs/backups/before-mall-reset-20260908-214833/`。

- `databases.sql`：六个项目数据库完整备份，包含表结构和原数据。
- `redis.rdb`：清理前 Redis 快照。
- `manifest.json`：数据库列表、文件大小和 SHA-256。

已重建 `saas_tenant`、`saas_iam`、`saas_merchant`、`saas_member`、`saas_trade`、旧 `saas_analytics`；MySQL 系统库未改动。清理了 177 个 `saas:*` 缓存键。后端 Flyway 重新建表，平台基础管理员与权限自动初始化。

`scripts/reset-local-mall.py --apply` 是破坏性重置命令，不要作为日常启动命令运行。日常使用 `scripts/start-backend.ps1 -SkipBuild -EnableSimulatedPayment` 与 `scripts/start-frontend.ps1`。

## 界面与素材

消费者采用暖白与陶土红色的百货布局：首页搜索、八大分类、会员活动入口、双列推荐商品、门店信息。商品列表与规格来自真实后台，支持跨规格搜索。24 张商品图是原创 SVG 示意图，可在后台替换为实际商品图片。

首页海报使用内置 imagegen 生成，保存在 `frontend/apps/consumer-web/public/images/department-store-hero.png`。提示词：

> Create an original premium department store ecommerce hero campaign photograph, landscape 3:2. Warm ivory seamless studio with soft apricot morning sunlight. On the RIGHT HALF a curated still life of diverse everyday products: folded oatmeal cotton towel, a sculptural cream ceramic mug, minimal cobalt-blue over-ear headphones, a small amber skincare bottle without lettering, a canvas tote and two oranges. Products arranged on low pale peach display plinths, realistic materials and soft shadows. LEFT HALF clean ivory negative space for website headline overlay. Sophisticated contemporary lifestyle retail art direction, understated editorial photography, beautiful detail, warm inviting accessible everyday department store. NO tea cups, NO drinks with straws, no logos, no words, no lettering, no watermark. Production website asset, not a UI mockup.

## 验收

在 `frontend` 执行：

```powershell
pnpm --filter @smart-merchant/consumer-web build
$env:H5_MALL_E2E='1'
pnpm exec playwright test --config playwright.consumer.config.ts mall-real.spec.ts h5.spec.ts prototype.spec.ts --workers=1
```

新商场真实验收覆盖：无参数首页、8 类与图片加载、跨规格搜索、320/390/480 像素布局、SKU 数量下单、模拟支付、门店核销、消费者售后、总部审核退款、库存返还。

最终结果：12 / 12 浏览器测试通过，消费者生产构建通过。测试日志 `runtime-logs/mall-final-tests.log`；页面截图 `frontend/apps/consumer-web/qa/mall-home.png`、`mall-catalog.png`、`mall-product.png`、`mall-order.png`。数据库核验为一个 ACTIVE 租户、一个门店、24 个 SPU 和 27 个 SKU。

旧文档中的 `1788781303132001` 等验收租户已随本次重置移除，勿再用旧租户 ID 运行原 M3 专用真实用例。真实支付、配送、充值和礼品卡交易仍未开通。
