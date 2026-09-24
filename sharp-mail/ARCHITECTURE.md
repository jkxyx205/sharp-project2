# sharp-mail 架构

## 分层

```
业务层（Controller/Service，业务方自写）
        │ 注入
        ▼
MailHandler（门面，MailHandlerImpl）   ── 发：JavaMailSender(SMTP)  收：IMAPStore(IMAP)
        ▲
        │ 装配
MailServiceAutoConfiguration（@Configuration，@AutoConfigureAfter(MailSenderAutoConfiguration)）
        ▲
        │ 属性 + 依赖
ImapMailProperties（spring.mail.imap） + MailProperties（spring.mail，Spring 内置） + spring-boot-starter-mail
```

## 与 `sharp-pay` 的类比

| 维度 | sharp-pay | sharp-mail |
|---|---|---|
| 性质 | 纯集成门面 | 纯集成门面 |
| 上游依赖 | 无 sharp-database | 无 sharp-common/sharp-database |
| 外部能力 | wechatpay-java + alipay-sdk-java | spring-boot-starter-mail + jsoup |
| 自动配置 | `PayServiceAutoConfiguration` | `MailServiceAutoConfiguration` |
| 配置属性 | `sharp.pay.*` | `spring.mail.*` + `spring.mail.imap.*` |
| Controller | 无 | 无 |

## 装配时序

`MailServiceAutoConfiguration` 标注 `@AutoConfigureAfter(MailSenderAutoConfiguration.class)`：等 Spring Boot 内置的邮件自动配置先创建 `JavaMailSender` Bean，再注入 `MailHandlerImpl`。构造时**不**建立 IMAP 连接——`IMAPStore` 在首次收件时（`getStore()`）懒连接并缓存到 `cachedStore` 字段。

## IMAP 连接管理

`getStore()`：
1. 若 `cachedStore` 已连接则复用（**单例缓存，非线程安全**，见陷阱 ⑤）；
2. 否则从 `JavaMailSenderImpl.getSession()` 取 `IMAPStore`，`connect(host, port, username, password)`；
3. 对 163 邮箱特殊处理：`store.id(clientParams)` 上报 IMAP ID（`name=sharp-mail` 等），否则 163 报 "Unsafe Login"。

连接关闭策略分散在各方法：
- `listFolders` / `listUnreadMessage` / `listMessages(String,...)`：方法内 `store.close()`；
- `searchByMessageId`：`folder.close()` + `closeStore()`；
- `saveToOutbox`：`outBox.close(true)` + `store.close()`。

> 因此 `listFolders()` 返回的 `Folder` 在 `store.close()` 后**不可再读邮件**（源码注释：163 下尤其如此）。

## 发送与正文组装

`convertEmailToMimeMessage(Email)`：
- `setFrom` + TO/CC/BCC 收件人（`InternetAddress(addr, personal)`）；
- 正文：`plainText` 走 `text/plain`，否则 `htmlText` 走 `text/html`（**二选一**，plain 优先）；
- 附件：`MimeBodyPart` + `ByteArrayDataSource`，文件名 `MimeUtility.encodeText`；
- 内嵌图片：`INLINE` + `ContentID = fileName`；
- **仅当** multipart 多于 1 部分时才 `message.setContent(multipart)`，否则用 `message.setText(...)`（纯文本/HTML 无附件路径）。

## 配置文件归属

模块自带 4 份 profile 参考配置（`src/main/resources/application-{gmail,aliyun,qq,163}.yml`），含 `xxx` 占位口令，**仅示例**。这些是 Spring profile 文件，**只在激活对应 profile 时加载**，不会像 `sharp-fileupload` 的 `application.yml` 那样被默认加载（见根 CLAUDE.md 陷阱 ⑤ 的对比）。业务工程应使用自己的 `application.yml` 配置真实授权码，或以同名 profile 文件遮蔽。

## 已知缺陷 / `[需要确认]`

1. **顶层 `EmailBuilder.java` 死代码**：`cc`/`bcc` 空实现、无 `build()`、私有构造却又有 `builder()` 静态方法（自相矛盾）。`Email.builder()` 解析到 `Email` 内部的 `EmailBuilder`（内部类优先），顶层类零引用。`[需要确认]`：建议直接删除顶层 `EmailBuilder.java`。
2. **`saveToOutbox` 文件夹名硬编码中文 `"已发送"`**：Gmail（`[Gmail]/Sent Mail`）、Outlook（`Sent`）等会抛 `MessagingException`。`[需要确认]`：是否应改为可配置 `sharp.mail.outbox-folder` 或按服务商枚举。
3. **`IMAPStore` 单字段缓存非线程安全**：并发收件会共享同一连接、相互 `close()`。`[需要确认]`：是否改为每次收件新建 Store 或加锁。
4. **163 兼容性硬编码**：`getStore` 强制 `store.id(clientParams)` 且 clientParams 写死 `name=sharp-mail`/`vendor=Rick`/`support-email=jkxyx205@163.com`；`searchByMessageId` 有 `//TODO 163 读取有问题`。
5. **`listMessages` 分页边界**：`consumer` 返回 `true` 后置 `start=1` 并 `break`，但外层 `while` 以 `end = start - 1`（=0）退出，逻辑可终止；边界无功能性 bug，仅可读性差。
6. **`listUnreadMessage` 异常吞栈**：`MessagingException` 走 `e.printStackTrace()` 后返回 `false`（继续遍历），未抛出。
7. **`contentText` HTML 解析**：`text/html` 分支用 `Jsoup.parse(html).text()` 抽纯文本，会丢失结构；如需保留 HTML 原样，调用方应自行取 `text/html` part。
