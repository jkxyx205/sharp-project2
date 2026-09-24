# sharp-pay 模块使用指南（面向 AI 编程助手）

## 模块定位

**解决什么问题**：为 `com.rick` 体系工程提供统一的支付能力门面，集成**微信支付（API v3）**与**支付宝**（V2 通用版）。覆盖扫码支付（Native/当面付）、JSAPI（公众号/小程序）、H5、App 拉起、退款/退款查询、对账账单下载、异步回调验签。

**对标模块**：`sharp-mail`——纯集成模块，**不依赖 `sharp-database`、不落库**。订单与支付流水由业务方自行存储，本模块只做支付能力门面与回调解析。

**实现方式**：官方 SDK。微信用 `wechatpay-java`（签名、平台证书自动下载、回调验签由 SDK 处理），支付宝用 `alipay-sdk-java`（公钥模式，回调验签 `AlipaySignature.rsaCheckV1`）。两个 SDK 以 `api` 依赖传递，业务方可见。

**不适用场景**：
- 需要订单/交易流水自动落库——本模块**不落库**，需业务方自行建表存储；如需落库请在业务层基于 `sharp-database` 实现。
- 证书模式（支付宝 `app-cert-path`）——当前实现仅走**公钥模式**；证书模式需扩展 `AliPayServiceImpl` 的 `DefaultAlipayClient` 构造方式。
- 退款到特定账户、分账、红包等高级能力——首期未覆盖。

## 源码阅读规则

```
默认不要扫描整个模块源码。
使用模块时：
1. 优先阅读本文件（CLAUDE.md）
2. 再阅读 API.md
3. 如果 API.md 已经解决问题，不要继续阅读源码
4. 只有在文档无法解决问题、排查 Bug 或确认特殊行为时，才阅读相关源码
```

## API 文档索引

- 详细 API（方法签名 + 请求/响应字段 + 示例）→ [API.md](./API.md)
- 架构与 Spring 集成机制、SDK 装配时序 → [ARCHITECTURE.md](./ARCHITECTURE.md)

## 启用方式（业务工程必做）

1. **依赖声明**（Gradle）：
   ```gradle
   dependencies {
       implementation "com.rick.pay:sharp-pay:0.0.1-SNAPSHOT"   // 或 project(":sharp-pay")
       // SDK 是 api 依赖，会传递；业务方自带 Spring Web：
       implementation 'org.springframework.boot:spring-boot-starter-web'
   }
   ```
2. **配置**（`application.yml`，按需配一个或两个通道；未配关键属性的通道**不会被装配**）：
   ```yaml
   sharp:
     pay:
       wechat:
         mch-id: 16xxxx
         app-id: wxXXXXXX
         mch-serial-no: 证书序列号
         private-key-path: /path/to/apiclient_key.pem   # 或 private-key: PEM 内容
         api-v3-key: 32位V3密钥
         notify-url: https://your.host/pay/notify
       alipay:
         app-id: 2021xxxx
         private-key: PKCS8 私钥（不含 BEGIN/END）
         alipay-public-key: 支付宝公钥
         gateway: https://openapi.alipay.com/gateway.do   # 沙箱用 ...alipaydev.com...
         sign-type: RSA2
         charset: UTF-8
         format: json
         notify-url: https://your.host/pay/notify
   ```
   完整样例见 `docs/config-sample.yml`。

3. **回调 Controller**（业务方自写，模块不内置）：
   ```java
   @PostMapping("/pay/notify")
   @ResponseBody
   public String notify(HttpServletRequest request) {
       PayNotifyResult result = payService.parseNotify(request);
       // 业务方按 result.getOutTradeNo() 找到订单、更新状态、落库
       return result.getReplyContent();   // 回写通道（微信 JSON / 支付宝 "success"）
   }
   ```

## 使用原则

- **只注入 `PayService` 门面**，不要直接注入 `WeChatPayService` / `AliPayServiceImpl`；
- 金额统一用**分**（`Integer`），微信原生就是分，支付宝侧由模块换算为元字符串；
- 回调必须走 `payService.parseNotify(request)`——**不要自己写签名/验签**，微信侧 SDK 用平台证书验签、支付宝侧用 `rsaCheckV1`，手写极易出错；
- 回调通道由 `DefaultPayServiceImpl` 按请求特征判定（微信 `Wechatpay-Signature` 头 / 支付宝 `notify_id` 表单参数），无需业务方区分。

## 禁止行为

1. **不要在 `src/main/resources` 放 `application.yml`**——本模块故意**只**在 `docs/config-sample.yml` 提供样例，避免重蹈 `sharp-fileupload` 的覆辙（其 jar 携带开发者本机 `application.yml`，含明文口令，可能被业务工程加载）。
2. **不要手写支付签名或回调验签**——必须经 SDK / `parseNotify`。
3. **不要假定两个通道一定存在**——未配置前缀（`sharp.pay.wechat.mch-id` / `sharp.pay.alipay.app-id`）的通道 Bean 不创建，门面调用对应通道会抛 `UnsupportedOperationException`，这是设计行为，不是 bug。
4. **不要把微信 `app-id` 写进 `PayRequest`**——`app-id` 来自配置（一个商户通常对应固定 appId），`PayRequest` 只带业务字段。
5. **支付宝 JSAPI 的 `buyer_id` 走 `PayRequest.openid` 字段**（统一入口，文档同名字段复用）；微信 JSAPI 同样用 `openid`。

## 已知限制（`[需要确认]` / 待完善）

- 支付宝仅公钥模式；证书模式（`app-cert-path`/`alipay-public-cert-path`/`root-cert-path`）属性已预留但 `AliPayServiceImpl` 尚未实现证书版 `DefaultAlipayClient`。
- 微信账单下载返回 `InputStream`（`BillDownloadServiceExtension`），支付宝账单仅返回下载 URL（业务方自行 GET 下载）。
- 微信 JSAPI/App 返回 `prepayId`，**调起参数的二次签名由业务方完成**（SDK 的 prepay 响应不含 `paySign`）。
- H5 场景微信 `sceneInfo.h5_info.type` 写死 `"iOS"`，未按业务侧区分。
