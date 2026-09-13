# 消费者 H5 界面体验版

2026-09-08：本文记录纯界面演示版。默认入口现已接入后端，真实业务说明见 [接入与验收](CONSUMER-H5-INTEGRATION.md)。

## 启动

在项目根目录执行：

```powershell
cd E:\project\SAAS\frontend
pnpm dev:consumer --host 127.0.0.1
```

演示版访问 http://127.0.0.1:5177/?preview=1 ，仅需前端。
真实接入版访问 `/?tenantId=品牌ID&storeId=门店ID`，需要后端，旧 `live=1` 链接仍兼容。

## 页面与交互

- 首页：原创叶集品牌、新品海报、自取 / 外送、会员权益、推荐饮品、送礼入口。
- 点单：分类与搜索、规格弹层、杯型 / 温度 / 甜度 / 加料、数量调整、收藏、购物袋。
- 门店：名称地址搜索、营业状态、门店位置示意、独立保存各店购物袋。
- 结算：自取 / 外送切换、地址管理、取餐时间、备注、优惠券抵扣、配送费、体验支付。
- 订单：取餐码、确认取餐 / 收货、订单详情、历史订单、再来一单、售后申请界面。
- 我的：会员体验、积分兑换、优惠券领取、钱包充值体验、收藏、地址增删改、送礼、消息、帮助、设置。

所有下单、充值、积分、送礼和售后均为本机演示，不扣款、不发送消息、不通知门店。地图是位置示意，不提供真实定位导航。正式账户、支付、交易流水和客服服务尚未接入。购物袋、订单、权益和地址使用浏览器 localStorage；清除网站数据可重置体验。

## 验证

`pnpm --filter @smart-merchant/consumer-web build` 检查类型和生产构建。
`pnpm exec playwright test --config playwright.consumer.config.ts` 验证独立界面和原接口模式。真实后端用例需显式配置环境变量才运行。
移动端检查覆盖 320px / 390px 宽度。截图位于 `frontend/apps/consumer-web/qa/prototype-*.png`。

## 原创海报

使用内置 imagegen 工具生成。素材：`frontend/apps/consumer-web/public/images/matcha-campaign.png`。
提示词：

> Create a premium original tea brand campaign photograph for a mobile ordering app, landscape 3:2 composition. Pale pistachio green seamless studio backdrop and table. On the RIGHT two elegant clear takeaway cups of iced matcha cloud latte, layered rich green matcha at bottom and white creamy milk foam at top, realistic condensation, one cup slightly taller behind the other. Small fresh green tea leaves and a sliced pear beside cups, soft morning sunlight, editorial food photography, refined clean Chinese modern tea boutique aesthetic, high-end commercial detail. LEFT THIRD is clean negative space pale green for HTML headline overlay. Cups have a tiny minimalist dark-green abstract leaf emblem, no words, no lettering, no watermark, no existing brand logos. This is a production website hero photograph, not a UI mockup.
