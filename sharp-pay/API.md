# sharp-pay API

> 取证来源：仅本模块源码与官方 SDK（`wechatpay-java` 0.2.17、`alipay-sdk-java` 4.40.1014.ALL）。
> 仓库内**无外部调用方**，以下示例为基于 `PayService` 门面的用法演示，业务方按需适配。

## 入口：`PayService`

包：`com.rick.pay.core.PayService`。Spring Bean，由 `PayServiceAutoConfiguration` 注册（`@ConditionalOnMissingBean`）。

```java
@Resource
private PayService payService;
```

### 统一下单 `createOrder(PayRequest) → PayResponse`

```java
PayRequest request = PayRequest.builder()
        .channel(PayChannel.WECHAT)   // 或 PayChannel.ALIPAY
        .scene(PayScene.NATIVE)        // NATIVE / JSAPI / H5 / APP
        .outTradeNo("ORDER202609240001")
        .subject("会员季卡")
        .amount(100)                    // 分
        .notifyUrl("https://your.host/pay/notify")   // 可选，缺省走配置
        .openid("oUpF8...")            // JSAPI 必填（微信 openid / 支付宝 buyer_id）
        .clientIp("127.0.0.1")         // H5 必填
        .expireSeconds(1800)           // 可选
        .build();
PayResponse resp = payService.createOrder(request);
```

响应字段按场景取用：

| 场景 | 取用字段 | 说明 |
|---|---|---|
| NATIVE | `resp.getCodeUrl()` | 微信 code_url / 支付宝 qr_code，前端生成二维码 |
| JSAPI | `resp.getPayParams()` | 含 `prepayId`（微信）/`tradeNo`+`buyerId`（支付宝）；微信调起需业务方二次签名 |
| H5 | `resp.getPayUrl()` | 微信 h5_url / 支付宝支付表单 HTML（直接写入响应） |
| APP | `resp.getPayParams()` | 微信 `prepayId` / 支付宝 `orderInfo`（客户端拉起） |

### 查询订单 `queryOrder(PayChannel, String outTradeNo) → PayQueryResponse`

```java
PayQueryResponse q = payService.queryOrder(PayChannel.WECHAT, "ORDER202609240001");
// q.getTradeStatus(): 微信 SUCCESS/NOTPAY/REFUND/CLOSED...；支付宝 TRADE_SUCCESS/WAIT_BUYER_PAY...
// q.getAmount(): 实付金额（分）
```

### 退款 `refund(RefundRequest) → RefundResponse`

```java
RefundRequest refund = RefundRequest.builder()
        .channel(PayChannel.WECHAT)
        .outTradeNo("ORDER202609240001")
        .outRefundNo("REFUND202609240001")
        .totalAmount(100)
        .refundAmount(100)
        .reason("用户取消")
        .build();
RefundResponse r = payService.refund(refund);
// r.getRefundStatus(): 微信 SUCCESS/PROCESSING/ABNORMAL/CLOSED；支付宝 SUCCESS/FAIL
```

### 查询退款 `queryRefund(PayChannel, String outRefundNo) → RefundQueryResponse`

```java
RefundQueryResponse rq = payService.queryRefund(PayChannel.WECHAT, "REFUND202609240001");
```

### 对账账单下载 `downloadBill(PayChannel, String billDate, String billType) → BillDownloadResponse`

```java
BillDownloadResponse bill = payService.downloadBill(PayChannel.WECHAT, "2026-09-23", "ALL");
// 微信：bill.getBillStream() 为输入流（业务方负责关闭、解压）
// 支付宝：bill.getBillUrl() 为下载链接（业务方自行 GET 下载）
// billType：微信取 SDK 的 BillType 枚举值（ALL/SUCCESS/REFUND/...）；支付宝传 "trade" 或 "signentry"
```

### 异步回调解析 `parseNotify(HttpServletRequest) → PayNotifyResult`

```java
@PostMapping("/pay/notify")
@ResponseBody
public String notify(HttpServletRequest request) {
    PayNotifyResult result = payService.parseNotify(request);
    // result.getOutTradeNo() / getTradeNo() / getTradeStatus() / getAmount() / getPayTime() / getBuyerId()
    // 业务方：落库、发通知...
    return result.getReplyContent();   // 回写：微信 {"code":"SUCCESS","message":"成功"} / 支付宝 success
}
```

## DTO 字段速查

### `PayRequest`
| 字段 | 类型 | 说明 |
|---|---|---|
| `channel` | `PayChannel` | WECHAT / ALIPAY，必填 |
| `scene` | `PayScene` | NATIVE / JSAPI / H5 / APP，必填 |
| `outTradeNo` | String | 商户订单号，必填 |
| `subject` | String | 商品描述/标题，必填 |
| `amount` | Integer | 金额（分），必填 |
| `notifyUrl` | String | 回调地址，可选（缺省走配置） |
| `openid` | String | JSAPI 用户标识（微信 openid / 支付宝 buyer_id） |
| `clientIp` | String | 终端 IP，H5 必填 |
| `expireSeconds` | Integer | 过期秒数，可选 |
| `extras` | Map | 通道透传参数，可选 |

### `PayNotifyResult`
| 字段 | 类型 | 说明 |
|---|---|---|
| `channel` | `PayChannel` | 解析出的通道 |
| `outTradeNo` | String | 商户订单号 |
| `tradeNo` | String | 通道订单号 |
| `tradeStatus` | String | 交易状态（通道原值） |
| `amount` | Integer | 实付金额（分） |
| `payTime` | String | 支付完成时间 |
| `buyerId` | String | 买家标识 |
| `replyContent` | String | 回写通道的应答报文 |

## Common Mistakes

1. **JSAPI 漏传 `openid`** → 微信 `JsapiService.prepay` 报错；支付宝 `trade.create` 缺 `buyer_id` 同样失败。
2. **H5 漏传 `clientIp`** → 微信 H5 下单失败。
3. **金额单位混淆** → 全链路用**分**（Integer）；不要传元字符串。
4. **回调手写验签** → 必须用 `parseNotify`，否则验签极易失败或被绕过。
5. **支付宝沙箱网关用错** → 正式 `https://openapi.alipay.com/gateway.do`，沙箱 `https://openapi.alipaydev.com/gateway.do`。
6. **微信 `private-key-path` 与 `private-key` 同时留空** → 构造 `RSAAutoCertificateConfig` 时抛异常，应用启动即失败（fail-fast，符合预期）。
