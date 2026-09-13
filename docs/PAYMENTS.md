# 订单支付

消费者支付现在采用“订单—支付单—渠道异步通知”三段式流程。创建订单不会直接记为已支付；消费者使用同一个 `requestId` 创建支付单，渠道通知验签并落库后，交易服务才扣减预占库存、确认会员权益并推进订单状态。自提订单进入 `PICKUP_READY`，邮寄订单进入 `PENDING_SHIPMENT`。

## 提供方与配置

`PaymentProvider` 是渠道适配契约，`PaymentProviderRegistry` 负责按渠道代码选择适配器。当前实现 `SANDBOX`；`WECHAT`、`ALIPAY` 已保留配置和注册入口，但没有真实 SDK 或商户密钥，默认不可用。所有渠道默认关闭，开发或验收环境可显式设置：

```text
SANDBOX_PAYMENT_ENABLED=true
SANDBOX_PAYMENT_CALLBACK_SECRET=<仅保存在服务端的随机长密钥>
WECHAT_PAYMENT_ENABLED=false
ALIPAY_PAYMENT_ENABLED=false
PAYMENT_CALLBACK_TOLERANCE_SECONDS=300
```

不要把回调密钥或后续商户私钥返回给浏览器。真实渠道适配器应在服务端创建预支付、验证渠道证书/签名，将渠道通知归一化为 `PaymentProvider.Callback`；订单状态流转继续复用 `PaymentService`，不能在渠道 Controller 中直接改订单。

## 接口与安全边界

- `POST /api/consumer/v1/orders/{orderId}/payments` 创建或幂等返回支付单。租户和消费者所有权只取认证令牌，不采信请求体。
- `GET /api/consumer/v1/orders/{orderId}/payment` 查询本人、本租户的支付状态。
- `POST /api/consumer/v1/payments/{paymentId}/sandbox/complete` 仅供显式启用的本地沙箱；仍校验消费者、租户与订单所有权。
- `POST /api/payment/v1/callback/{provider}` 是渠道回调入口。沙箱要求 `X-Payment-Timestamp` 与 `X-Payment-Signature`，签名内容为 `<timestamp>.<raw body>` 的 HMAC-SHA256；超出时间窗或签名不匹配会拒绝。

支付请求、订单和渠道交易号分别有唯一约束；回调事件按 `(provider,event_id)` 去重。回调还会校验租户、金额、支付单和订单状态。超时任务会同时关闭待支付订单和待处理支付单；迟到的成功通知只记为忽略，不会复活订单。

## 验证

后端集成测试覆盖支付创建重放、回调重放、库存只扣一次、超时迟到通知、消费者所有权及跨租户隔离。消费者浏览器测试覆盖支付单创建、沙箱确认、订单回查和明确的成功结果。
