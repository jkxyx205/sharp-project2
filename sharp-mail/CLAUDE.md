# sharp-mail 模块使用指南（面向 AI 编程助手）

## 模块定位

**解决什么问题**：为 `com.rick` 体系工程提供邮件收发门面。**发邮件**走 SMTP（基于 Spring `JavaMailSender`），**收邮件**走 IMAP（基于 Angus `IMAPStore`），并支持「发送并保存到已发送」、附件下载、正文(纯文本/HTML)解析。

**对标模块**：`sharp-pay`——纯集成门面，**不依赖 `sharp-common` / `sharp-database`**、不返回 `Result`、不内置 Controller。只暴露 `MailHandler` 门面与 `Email` 值对象。

**实现方式**：
- 发送：`spring-boot-starter-mail` 的 `JavaMailSender`（`api` 依赖，会传递）。
- 收件：`org.eclipse.angus.mail` 的 `IMAPStore`（由 `spring-boot-starter-mail` 在 Boot 3.x 间接带入）。
- HTML→纯文本：`jsoup`（`api` 依赖）。

**不适用场景**：
- 需要生产级邮件队列/重试/模板引擎——本模块只做单封同步收发，无队列、无重试、无模板渲染。
- 多账号并发收件——`IMAPStore` 以单字段缓存（`cachedStore`），非线程安全（见陷阱 ⑤）。
- 非中文邮箱的「保存到已发送」——文件夹名硬编码中文 `"已发送"`（见陷阱 ③）。

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

- 详细 API（方法签名 + 字段 + 示例）→ [API.md](./API.md)
- 架构与 Spring 集成机制、IMAP 连接管理 → [ARCHITECTURE.md](./ARCHITECTURE.md)

## 启用方式（业务工程）

1. **依赖声明**（Gradle）：
   ```gradle
   dependencies {
       implementation "com.rick.mail:sharp-mail:0.0.1-SNAPSHOT"   // 或 project(":sharp-mail")
       // sharp-mail 已 api 引入 spring-boot-starter-mail 与 jsoup，会传递
       implementation 'org.springframework.boot:spring-boot-starter-web'   // 如需在 Controller 调用
   }
   ```
2. **配置**（`application.yml`，SMTP + IMAP）：
   ```yaml
   spring:
     mail:                       # SMTP 发邮件（Spring Boot 内置 MailProperties）
       host: smtp.qq.com
       port: 465
       username: 10502052@qq.com
       password: 授权码           # 不是登录密码，是开启 SMTP 后获取的授权码
       default-encoding: UTF-8
       imap:                     # IMAP 收邮件（sharp-mail 自定义 ImapMailProperties）
         host: imap.qq.com
         port: 993
       properties:
         mail.smtp.auth: true
         mail.smtp.starttls.enable: true
   ```
   模块自带 4 份 profile 参考配置（`application-gmail/aliyun/qq/163.yml`），按 `--spring.profiles.active=qq` 等激活；含 `xxx` 占位口令，仅作示例，**不要直接用**（见陷阱 ⑥）。

3. **自动配置**：加依赖即生效。`MailServiceAutoConfiguration` 在 `MailSenderAutoConfiguration` 之后装配 `MailHandlerImpl` Bean（注入 Spring 的 `JavaMailSender` + `MailProperties` + 本模块 `ImapMailProperties`）。**无需组件扫描**。

## 使用原则

- **只注入 `MailHandler`**，不要直接 `new MailHandlerImpl(...)` 或注入实现类；
- 发邮件用 `Email.builder()`（`Email` 的**内部** `EmailBuilder`）构建值对象，再 `mailHandler.send(email)`；
- 收邮件后若需解析正文/下载附件，用 `MailUtils`（`contentText` / `downloadAttachments` / `hasAttachment`）；
- `listMessages(folder, consumer)` 的回调返回 `true` 表示**终止遍历**（倒序、每页 50 封分页从新到旧）。

## 禁止行为

1. **不要使用顶层 `com.rick.mail.core.mail.EmailBuilder`**——它是**死代码**（`cc`/`bcc` 空实现、无 `build()` 方法）。真正生效的是 `Email.builder()` 返回的 `Email` **内部** `EmailBuilder`（见陷阱 ①）。
2. **不要在并发场景下共享 `MailHandler` 收件**——`IMAPStore` 以单字段缓存，非线程安全（见陷阱 ⑤）。
3. **不要期待 `sendAndSaveToOutbox` 在非中文邮箱可用**——文件夹名硬编码 `"已发送"`（见陷阱 ③）。
4. **不要把 `application-*.yml` 里的 `xxx` 占位口令当真实配置**——它们是示例占位（见陷阱 ⑥）。

## 已知缺陷（`[需要确认]`）

- 顶层 `EmailBuilder.java` 为未完成的死代码，从未被引用（见陷阱 ①）。
- `searchByMessageId` 内含 `//TODO 163 读取有问题`——163 邮箱按 Message-ID 检索可能不返回结果。
- `saveToOutbox` 的「已发送」文件夹名硬编码中文，Gmail（`[Gmail]/Sent Mail`）等会 `MessagingException`。
- `listMessages` 在 `getMessages(start, end)` 与逐条访问间存在「start=1 终止后仍可能多取一页」的边界行为（见 ARCHITECTURE）。
