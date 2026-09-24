# sharp-mail API

> 取证来源：本模块源码 + `sharp-test` 调用证据（若有）。仓库内**无外部调用方**，以下示例为基于 `MailHandler` 门面的用法演示，业务方按需适配。

## 入口：`MailHandler`

包：`com.rick.mail.core.MailHandler`。Spring Bean，由 `MailServiceAutoConfiguration` 注册。

```java
@Resource
private MailHandler mailHandler;
```

### 发邮件

#### `send(Email) → MimeMessage` / `send(MimeMessage) → MimeMessage`
发送单封邮件（不保存到已发送）。

```java
Email email = Email.builder()
        .from("10502052@qq.com", "Rick")
        .to("someone@example.com", "某人")
        .subject("验证码")
        .htmlText("<h1>你的验证码是 9527</h1>")
        .attachment("file.txt", bytes, "text/plain")
        .embeddedImage("logo.png", logoBytes, "image/png")
        .build();
MimeMessage sent = mailHandler.send(email);
```

#### `sendAndSaveToOutbox(Email) → MimeMessage` / `sendAndSaveToOutbox(MimeMessage) → MimeMessage`
发送后追加到 IMAP 的 `"已发送"` 文件夹。⚠️ 文件夹名硬编码中文，仅适用中文邮箱服务商（见陷阱 ③）。

### 收邮件（IMAP）

#### `listFolders() → Folder[]`
列出所有邮箱文件夹。注意：调用后会 `store.close()`，**返回的 `Folder` 已不可读邮件**（163 下尤其如此，源码注释明确）。

#### `listUnreadMessage(Consumer<MimeMessage>)`
遍历未读邮件（INBOX，READ_WRITE 模式），对每封未读邮件执行 `consumer`，并**自动标记为已读**。遍历到未读计数上限即停。

#### `listMessages(String folderName, Function<MimeMessage, Boolean> consumer)` / `listMessages(Folder, ...)`
倒序分页遍历指定文件夹（每页 `SIZE=50`，从最新封向前翻）。回调返回 `true` **终止遍历**。

```java
mailHandler.listMessages("INBOX", message -> {
    try {
        String text = MailUtils.contentText(message.getContent());
        if (text.contains("invoice")) {
            MailUtils.downloadAttachments(message, "/tmp/attachments");
            return true;   // 命中即停
        }
    } catch (Exception e) {
        return false;
    }
    return false;
});
```

#### `searchByMessageId(String folderName, String messageId) → Optional<MimeMessage>` / `searchByMessageId(Folder, ...)`
按 `Message-ID` 检索。⚠️ 源码含 `//TODO 163 读取有问题`——163 邮箱可能不返回结果。

## 值对象：`Email` 与内部 `EmailBuilder`

包：`com.rick.mail.core.mail.Email`。**只能用 `Email.builder()`**（返回 `Email` 内部的 `EmailBuilder`）。

| Builder 方法 | 说明 |
|---|---|
| `from(String address, String personal)` | 发件人（必填） |
| `to(String address)` / `to(String address, String personal)` | 收件人（可多次调用） |
| `cc(String address)` / `cc(String address, String personal)` | 抄送 |
| `bcc(String address)` / `bcc(String address, String personal)` | 密送 |
| `subject(String)` | 主题 |
| `plainText(String)` / `htmlText(String)` | 正文（纯文本或 HTML，二选一） |
| `attachment(String fileName, byte[], String mimeType)` | 附件（可多次） |
| `attachments(List<Object[]>)` | 批量附件，每个 `Object[]{fileName, bytes, type}` |
| `embeddedImage(String fileName, byte[], String mimeType)` | 内嵌图片（CID = fileName，INLINE） |
| `build()` | 构造 `Email` |

> ⚠️ 同名顶层类 `com.rick.mail.core.mail.EmailBuilder` 是**死代码**（`cc`/`bcc` 空实现、无 `build()`），不要使用（见 CLAUDE.md 陷阱 ①）。

## 工具类：`MailUtils`

包：`com.rick.mail.util.MailUtils`（`@UtilityClass`，静态方法）。

| 方法 | 说明 |
|---|---|
| `downloadAttachments(Message, String downloadFolder) → List<String>` | 保存附件到目录，返回文件名列表 |
| `hasAttachment(Part) → boolean` | 判断是否含附件（递归 Multipart） |
| `contentText(Object) → String` | 解析正文：`String` 直返、`MimeMultipart` 递归取 `text/plain`/`text/html`（HTML 经 `Jsoup.parse(html).text()` 转纯文本） |

## 配置属性

### `ImapMailProperties`（`spring.mail.imap`）
| 字段 | 类型 | 说明 |
|---|---|---|
| `host` | String | IMAP 主机 |
| `port` | Integer | IMAP 端口 |

### Spring 内置 `MailProperties`（`spring.mail`）
`host`/`port`/`username`/`password`/`default-encoding`/`properties`（SMTP 相关）。详见 Spring Boot 文档。

## Common Mistakes

1. **误用顶层 `EmailBuilder`** → 编译可通过（类存在）但 `build()` 方法不存在、`cc`/`bcc` 不生效。用 `Email.builder()`。
2. **`sendAndSaveToOutbox` 用于 Gmail** → `"已发送"` 文件夹不存在，抛 `MessagingException`。
3. **收件并发** → 共享 `MailHandler` 的 `cachedStore` 串扰。收件请串行或自建多实例。
4. **口令填登录密码** → SMTP/IMAP 用的是**授权码**（开启服务后获取），非登录密码。
5. **`listFolders()` 后读邮件** → 已 `store.close()`，Folder 失效；要读邮件请用 `listMessages`/`searchByMessageId`。
6. **`listMessages` consumer 返回值理解错** → `true` 是「终止」，不是「继续」。
