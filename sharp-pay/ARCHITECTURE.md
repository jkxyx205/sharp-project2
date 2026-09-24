# sharp-pay 架构

## 分层

```
业务层 Controller（业务方自写）
        │ 注入
        ▼
PayService（门面，DefaultPayServiceImpl）   ──── 按 PayChannel 分派
   ├── WeChatPayService（WeChatPayServiceImpl）  ── wechatpay-java SDK
   └── AliPayService（AliPayServiceImpl）        ── alipay-sdk-java SDK
        ▲
        │ 装配
PayServiceAutoConfiguration（@AutoConfiguration，通道按 @ConditionalOnProperty 按需装配）
        ▲
        │ 属性
WeChatPayProperties / AliPayProperties（@ConfigurationProperties）
```

## 与 `sharp-mail` 的类比

| 维度 | sharp-mail | sharp-pay |
|---|---|---|
| 性质 | 纯集成门面 | 纯集成门面 |
| 上游依赖 | 无 sharp-database | 无 sharp-database |
| 外部 SDK | spring-boot-starter-mail | wechatpay-java + alipay-sdk-java（`api` 传递） |
| 自动配置 | `MailServiceAutoConfiguration` | `PayServiceAutoConfiguration` |
| 配置属性 | `spring.mail.imap.*` | `sharp.pay.wechat.*` / `sharp.pay.alipay.*` |
| Controller | 无 | 无（回调由业务方自写） |

## SDK 装配时序

`WeChatPayServiceImpl` 构造时一次性构建并复用：
- `RSAAutoCertificateConfig`（自动下载并轮换微信平台证书）；
- `NativePayService` / `JsapiService` / `H5Service` / `AppService` / `RefundService` / `BillDownloadServiceExtension`；
- `AutoCertificateNotificationConfig` + `NotificationParser`（回调验签）。

`AliPayServiceImpl` 构造 `DefaultAlipayClient`（公钥模式 7 参构造：gateway/appId/privateKey/format/charset/alipayPublicKey/signType），后续各接口复用。

## 通道按需装配

`PayServiceAutoConfiguration` 对两个通道分别加 `@ConditionalOnProperty`：
- 微信：`prefix="sharp.pay.wechat", name="mch-id"`；
- 支付宝：`prefix="sharp.pay.alipay", name="app-id"`。

未配置的通道 Bean 不创建；`PayService` 通过 `ObjectProvider` 注入两通道（可空），调用缺失通道时由 `DefaultPayServiceImpl.requireWechat()/requireAlipay()` 抛 `UnsupportedOperationException`，提示配置缺失。

## 回调通道判定

`DefaultPayServiceImpl.parseNotify` 不依赖 URL，按请求特征判定通道：
- 存在 `Wechatpay-Signature` 头 → 微信；
- 存在 `notify_id` 表单参数 → 支付宝；
- 都缺 → 抛 `IllegalArgumentException`。

业务方只需把两通道回调指向**同一个** `/pay/notify` 即可。

## 金额单位约定

全链路统一用**分**（`Integer`）。微信 API v3 原生即分；支付宝侧由 `AliPayServiceImpl` 用 `BigDecimal.movePointLeft(2)` 转元字符串下发，回调解析用 `movePointRight(2)` 转回分。避免浮点误差。

## 配置属性

### `WeChatPayProperties`（`sharp.pay.wechat`）
`mch-id`、`app-id`、`mch-serial-no`、`private-key` / `private-key-path`（二选一，private-key 优先）、`api-v3-key`、`cert-path`（可选）、`notify-url`、`base-path`（可选）。

### `AliPayProperties`（`sharp.pay.alipay`）
`app-id`、`private-key`、`alipay-public-key`、`sign-type`（默认 RSA2）、`charset`（UTF-8）、`format`（json）、`gateway`、`notify-url`、`app-cert-path`/`alipay-public-cert-path`/`root-cert-path`（证书模式预留，`[需要确认]` 见下）。

## 已知缺陷 / `[需要确认]`

1. **支付宝证书模式未实现**：`AliPayServiceImpl` 仅用公钥模式 `DefaultAlipayClient`，属性中 `app-cert-path` 等证书字段已预留但未生效。需证书模式的业务方需自行扩展。`[需要确认]`：是否多数场景公钥模式已足够。
2. **微信 JSAPI/App 调起参数二次签名缺失**：`createOrder` 返回 `prepayId`，生成 `appId`/`timeStamp`/`nonceStr`/`package`/`signType`/`paySign` 的二次签名由业务方完成。`[需要确认]`：是否应在模块内提供 `WeChatPayService.invokeParams(prepayId)` 辅助方法。
3. **微信 H5 `h5_info.type` 写死 `"iOS"`**：未按业务侧区分 iOS/Android。`[需要确认]`：是否需要按 `clientIp`/UA 区分。
4. **支付宝账单仅返回 URL**：微信用 `BillDownloadServiceExtension` 返回流，支付宝仅返回下载链接，业务方需自行 GET 下载并解压。
5. **微信账单流由业务方关闭**：`BillDownloadResponse.getBillStream()` 未在模块内关闭，避免提前关闭影响业务方读取；业务方务必 try-with-resources。
6. **沙箱/真实端到端未验证**：仓库无支付沙箱凭证，所有实现基于 SDK 官方 API 与 javap 核实的类签名，编译通过、发布通过，但**未做真实下单/回调联调**。`[需要确认]`：上线前必须用沙箱走通 createOrder → 回调 → refund 全链路。
