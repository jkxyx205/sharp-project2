# sharp-project2 总索引（面向 AI 编程助手）

> JDK 17 · Spring Boot 3.5.7 · Gradle 多模块 · Lombok · 自研注解驱动 ORM（**非 JPA、非 MyBatis**）
> 本文件只做**导航与跨模块规则**。单个模块的 API 细节一律去该模块目录下查，不要在此文件中查找 API 签名。

---

## 1. 先读哪份文档

```
默认不要扫描整个模块源码。

使用模块时：
1. 优先阅读本文件（根 CLAUDE.md），确定该去哪个模块
2. 再阅读该模块目录下的 CLAUDE.md
3. 需要 API 细节时阅读该模块的 API.md
4. 如果 API.md 已经能解决问题，不要继续阅读源码
5. 只有在文档无法解决问题、排查 Bug 或确认特殊行为时，才阅读相关源码
```

**跨模块任务的最短路径**：本文件第 3 节选模块 → 目标模块 `CLAUDE.md` → 目标模块 `API.md` → （仅必要时）`docs/`。

---

## 2. 模块清单与文档索引

| 模块 | group | 定位一句话 | 自动配置 | 文档 |
|---|---|---|---|---|
| `sharp-common` | `com.rick.common` | 统一返回 / 业务异常 / 校验注解 / JSON / ID / 工具类 / Lambda 元信息 | ❌ **无**（必须手动装配） | [CLAUDE](sharp-common/CLAUDE.md) · [API](sharp-common/API.md) · [ARCH](sharp-common/ARCHITECTURE.md) |
| `sharp-database` | `com.rick.db` | 注解驱动 ORM + 分页 Grid + 建表生成器 + 多方言 | ✅ `SharpDatabaseAutoConfiguration` | [CLAUDE](sharp-database/CLAUDE.md) · [API](sharp-database/API.md) · [ARCH](sharp-database/ARCHITECTURE.md) · [docs/](sharp-database/docs/) |
| `sharp-meta` | `com.rick.meta` | 数据字典（Dict）+ 系统参数（Property） | ✅ `MetaServiceAutoConfiguration` | [CLAUDE](sharp-meta/CLAUDE.md) · [API](sharp-meta/API.md) · [ARCH](sharp-meta/ARCHITECTURE.md) |
| `sharp-fileupload` | `com.rick.fileupload` | 文件存储抽象（local/MinIO/OSS/FastDFS）+ 附件落库 + 图片处理 | ✅ `FileUploadAutoConfig` | [CLAUDE](sharp-fileupload/CLAUDE.md) · [API](sharp-fileupload/API.md) · [ARCH](sharp-fileupload/ARCHITECTURE.md) · [docs/](sharp-fileupload/docs/) |
| `sharp-formflow` | `com.rick.formflow` | 动态表单引擎（组件 + 自研校验 + EAV/宽表双存储 + 服务端渲染页面） | ✅ `FormFlowServiceAutoConfiguration` | [CLAUDE](sharp-formflow/CLAUDE.md) · [API](sharp-formflow/API.md) · [ARCH](sharp-formflow/ARCHITECTURE.md) · [docs/](sharp-formflow/docs/) |
| `sharp-mail` | `com.rick.mail` | 邮件收发门面：SMTP 发 + IMAP 收 + 保存已发送 + 附件/正文解析。纯集成（对标 `sharp-pay`） | ✅ `MailServiceAutoConfiguration` | [CLAUDE](sharp-mail/CLAUDE.md) · [API](sharp-mail/API.md) · [ARCH](sharp-mail/ARCHITECTURE.md) |
| `sharp-pay` | `com.rick.pay` | 支付门面：微信（API v3）+ 支付宝，扫码/JSAPI/H5/App/退款/对账/回调验签。**不落库**，纯集成（对标 `sharp-mail`） | ✅ `PayServiceAutoConfiguration`（通道按需装配） | [CLAUDE](sharp-pay/CLAUDE.md) · [API](sharp-pay/API.md) · [ARCH](sharp-pay/ARCHITECTURE.md) · [docs/](sharp-pay/docs/) |
| `sharp-test` | `com.rick` | **本地试验场，不发布**（jar/publish 任务已禁用）。是 common/database/meta 唯一的真实用法证据源 | — | 无文档；作为示例蓝本被上述文档引用 |

**文档边界**：`~/.m2/repository/com/rick/` 下还有 `admin`、`data`、`dubbo`、`excel`、`mail`、`notification`、`report`、`sms`、`sse`、`wechat`、`sharp-dependencies`、`sharp-generator`、`sharp-database2` 等构件，**均不在本仓库、不在本文档范围内**。遇到它们不要套用本仓库的结论。

---

## 3. 依赖关系与选型

### 依赖图（编译期）

```
sharp-common  ← 无 Spring 运行期依赖（Spring 全部 compileOnly）
     ↑ api
sharp-database
     ↑ implementation          ↑ implementation
sharp-meta              sharp-fileupload
     ↑ implementation          ↑ Maven 坐标 3.0-SNAPSHOT（非 project 依赖，见陷阱 ④）
     └──────── sharp-formflow ─┘

sharp-pay  ← 独立，无上游 project 依赖（仅官方 SDK：wechatpay-java + alipay-sdk-java）
sharp-mail ← 独立，无上游 project 依赖（仅 spring-boot-starter-mail + jsoup）
```

`implementation` 意味着**不传递**：业务方若要直接使用上游类型（如 `TableDAO`），必须自己再声明依赖。

### 我要做什么 → 用哪个模块

| 需求 | 去这里 |
|---|---|
| 统一接口返回体 / 抛业务异常 / 全局异常处理 | `sharp-common` → `ResultUtils`、`BizException`、`ApiExceptionHandler` |
| 获取当前用户、权限、Token | ⚠️ **本仓库不提供**。`sharp-database` 的 `ExtendTableDAOImpl.getUserId()` 硬编码返回 `1L`，需业务方自行覆写并接入自己的认证体系 |
| JSON 序列化 / 反序列化 | `sharp-common` → `JsonUtils`（不要自己 `new ObjectMapper()`） |
| 生成主键 ID | `sharp-common` → `IdGenerator.getSequenceId()` |
| 参数校验（枚举值 / 手机号） | `sharp-common` → `@EnumValid`、`@PhoneValid`、`ValidatorHelper` |
| 实体建模、单表/主从表 CRUD | `sharp-database` → `@Table` 等注解 + `extends EntityDAOImpl` |
| 分页查询、动态条件报表 | `sharp-database` → `GridService` / `GridUtils` / `AbstractTableGridService` |
| 逻辑删除、审计字段自动填充 | `sharp-database` → 注册 `ExtendTableDAOImpl`（**默认没有**，见陷阱 ①） |
| 由实体生成建表 DDL | `sharp-database` → `TableGenerator` |
| 下拉选项 / 编码转中文名 / 字典合法性校验 | `sharp-meta` → `DictService`、`@DictType`、`DictConverter` |
| 运行时可变的全局键值参数 | `sharp-meta` → `PropertyService`（**注意 `@Value` 读不到**，见陷阱 ⑥） |
| 文件上传下载、附件关联业务单据 | `sharp-fileupload` → `FileStore` / `DocumentService` |
| 图片缩略、裁剪、文字头像 | `sharp-fileupload` → `ImageService` |
| 用户自定义表单（字段可配置、无需改代码） | `sharp-formflow` → `FormService` + 现成 Controller |
| 微信/支付宝支付（下单/查询/退款/回调） | `sharp-pay` → `PayService`（**不落库**，订单自行存储；回调用 `parseNotify` 不要手写验签） |
| 邮件收发（SMTP 发 / IMAP 收 / 附件下载） | `sharp-mail` → `MailHandler` + `Email.builder()` + `MailUtils` |
| 固定结构的业务表 CRUD | ❌ **不要用 formflow**，直接用 `sharp-database` |

---

## 4. 引入与发布

**发布到本地仓库**：`./mvn.sh`（内部执行 `gradlew publishToMavenLocal`，并固定 `JAVA_HOME` 为 JDK 17）。
版本统一来自 `gradle/libs.versions.toml` 的 `sharp = "0.0.1-SNAPSHOT"`。

**业务工程引入**（Gradle）：

```gradle
repositories { mavenLocal() }          // 或改为 project(":sharp-xxx") 源码依赖

dependencies {
    implementation "com.rick.db:sharp-database:0.0.1-SNAPSHOT"
    implementation "com.rick.meta:sharp-meta:0.0.1-SNAPSHOT"
    // ↓ 根 build.gradle 对所有子模块施加的是 compileOnly，不会传递给业务方，必须自备：
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-aop'   // 用到 Service 方法级校验时
    runtimeOnly  'org.postgresql:postgresql'                            // 驱动按需，模块内是 compileOnly
}
```

**必备配置**（`sharp-database` 生效前提）：

```yaml
spring:
  datasource: { url: ..., username: ..., password: ... }   # 自动配置要求容器中存在唯一 DataSource 候选
sharp:
  database:
    type: PostgreSQL                                  # 默认 MySQL5，须与实际库一致
    entity-base-package: com.acme.**.entity           # ⚠️ 未配置 → 启动即 NPE（EntityDAOSupport.init）
```

**各模块自动配置的生效前提**（均已核实）：

| 模块 | 关键条件 |
|---|---|
| `sharp-common` | **无自动配置**。需手动：继承 `SharpWebMvcConfigurer` + `@EnableResultWrapped` + 组件扫描/自写 advice（详见其 CLAUDE.md「启用方式」） |
| `sharp-database` | `@ConditionalOnSingleCandidate(DataSource.class)`；可覆盖 Bean 仅 `tableDAO`、`validatorHelper` |
| `sharp-meta` | `@ConditionalOnSingleCandidate(TableDAO.class)` + `@AutoConfigureAfter(SharpDatabaseAutoConfiguration)` |
| `sharp-fileupload` | 无条件生效（含一个全放行 `CorsFilter`）；但 **Controller/Service 需业务方组件扫描 `com.rick.fileupload.client`** |
| `sharp-formflow` | `@ConditionalOnSingleCandidate(GridService.class)` + `@AutoConfigureAfter(SharpDatabaseAutoConfiguration)`；Controller 由 `FormServiceConfiguration` 的 `@ComponentScan("com.rick.formflow.form")` 注册，**只加依赖即生效** |
| `sharp-pay` | 无条件注册 `PayServiceAutoConfiguration`；两通道分别 `@ConditionalOnProperty(sharp.pay.wechat.mch-id / sharp.pay.alipay.app-id)` 按需装配，缺配置的通道不创建、门面调用抛 `UnsupportedOperationException` |
| `sharp-mail` | `@AutoConfigureAfter(MailSenderAutoConfiguration.class)` + `@EnableConfigurationProperties(ImapMailProperties.class)`；依赖 `JavaMailSender`（由 `spring-boot-starter-mail` 提供，本模块 `api` 引入）。加依赖即生效，**无需组件扫描** |

> `sharp-fileupload` 与 `sharp-formflow` 同时存在 `META-INF/spring.factories`（Boot 2 写法）与 `META-INF/spring/...AutoConfiguration.imports`（Boot 3 写法）。**Boot 3.x 不再读取 `spring.factories` 的 `EnableAutoConfiguration` 条目**，故前者是无效残留，实际生效的是 `.imports`。

---

## 5. 跨模块陷阱（最高价值，动手前必读）

以下每条均已对照源码核实，是「读单个模块文档不容易发现、但会跨模块咬人」的问题。

### ① 逻辑删除与审计字段**不是默认能力**，且默认/覆盖两种装配行为完全相反

`SharpDatabaseAutoConfiguration` 默认注册 `TableDAOImpl`（`@Bean @ConditionalOnMissingBean(TableDAO.class)`）。只有业务方自行声明 `TableDAO` Bean 为 `ExtendTableDAOImpl`（范例：`sharp-test/config/TestConfig.java:25`）时，才有审计字段自动填充与逻辑删除：

| | 默认 `TableDAOImpl` | 覆盖为 `ExtendTableDAOImpl` |
|---|---|---|
| `deleteById` | **物理** `DELETE FROM` | **逻辑** `UPDATE ... SET is_deleted=true`（仅对已在 `tableNameDAOMap` 中的表；未注册表仍物理删除） |
| SELECT 过滤 `is_deleted=false` | ❌ 不过滤 | ✅ 但**仅单表查询**（`SqlSingleTableChecker.isSingleTableQuery`）；多表/JOIN/子查询**不过滤**，须手写 |
| `create_by/create_time/update_by/update_time` | ❌ 只写实体上的值 | ✅ 自动填充 |
| 操作人 | — | ⚠️ `getUserId()` **硬编码 `return 1L`**，必须继承覆写 |

两个附加风险：
- **启动时序 NPE**：`tableNameDAOMap` 无初始值，直到 `ApplicationReadyEvent` 才填充，而 insert/delete 路径直接解引用它 → `@PostConstruct`、`afterPropertiesSet`、`ContextRefreshedEvent`、`ApplicationRunner`/`CommandLineRunner` 中的写入都会 NPE（select 路径不受影响，故启动期只读不报错，易误判）。详见 `sharp-database/docs/troubleshooting.md` 第 27 条。
- **对下游的连带影响**：`sharp-meta` 的字典缓存加载 SQL（`DictServiceImpl.SELECT_SQL`）文本里没有 `is_deleted = false`，是否过滤完全由上表决定；`dictDAO.deleteById` 同理。已写入 `sharp-meta/API.md` 2.1 的对照表。
- `create_time/update_time/is_deleted` 在 `BaseEntityInfo` 上是 `nullable = false`：**未注册 `ExtendTableDAOImpl` 又不手动赋值时，insert 会因 NOT NULL 约束失败**。

### ② `sharp-common` 加进依赖 ≠ 生效

它没有任何 `META-INF/spring` 注册文件。Web 能力（Long→String、枚举 code 转换、日期格式、`@ParamName`）需要**继承 `SharpWebMvcConfigurer`**；返回值自动包装需要 **`@EnableResultWrapped`**；全局异常处理需要**组件扫描 `com.rick.common` 或自写 advice**。三步缺一，能力就静默不存在。

### ③ `sharp-fileupload` 引入即带来一个全放行 CORS 过滤器

`FileUploadAutoConfig#corsFilter` **无条件注册**、允许所有 origin/header/method、且**没有 `@ConditionalOnMissingBean`**（声明同类型 Bean 会启动冲突）。收紧的唯一办法是 `spring.autoconfigure.exclude` 排除整个自动配置，再自行补注册 `InputStreamStore`/`FileStore`/`ImageService`。
同模块的 14 个 HTTP 接口（`/documents/**`、`/images/**`）**无任何认证注解**。

### ④ `sharp-formflow` 编译时链接的是一份陈旧的 fileupload jar

`sharp-formflow/build.gradle` 以坐标 `com.rick.fileupload:sharp-fileupload:3.0-SNAPSHOT` 依赖，而本仓库实际版本是 `0.0.1-SNAPSHOT`；根 `build.gradle` 中 `mavenLocal()` 排在所有远程仓库之前，且 `~/.m2/.../sharp-fileupload/3.0-SNAPSHOT/` 确实存在（2024-08-11 产物）→ **必然解析到与当前源码无关的旧 jar**。
补充事实：formflow 的 Java 代码对 `com.rick.fileupload` **零 import**，附件集成纯靠前端 JS 调 `/documents/upload`；且内置模板 `tpl/form.html:135` 引用 `@{/plugins/ajaxfileupload.js}`，实际文件在 `static/ajaxfileupload.js` → **404**。

### ⑤ `sharp-fileupload` 的 jar 里带着一份开发者本机 `application.yml`

`src/main/resources/application.yml` 含**明文数据库口令**的 MySQL 数据源、`multipart` 50MB、以及作者本机绝对路径。业务工程有自己的 classpath `application.yml` 时通常遮蔽它；**若业务工程只用 `application.properties`，这份 yml 可能被加载**，带入错误的数据源与上传限制。接入后应核对生效配置来源。
同目录的 `fdfs_client.properties` 同理（业务方放同名文件到 classpath 根可遮蔽以改 tracker 地址）。

### ⑥ 系统参数不能用 `@Value` 读

`sharp-meta` 的 `KeyValueProperties` 是 `@ConfigurationProperties(prefix="props")` 的 `Map<String,String>`，**不是 `PropertySource`**，不会进 Spring `Environment`。读 `sys_property` / `props.items` 只能用 `PropertyService` / `PropertyUtils`。
另外：改 `sys_dict` 后必须手动 `DictService.rebuild()`；改 `sys_property` **没有任何刷新手段**，只能重启。

### ⑦ 同名工具类共存，import 极易写错

`com.rick.common.util` 下的 `StringUtils`、`ClassUtils`、`ObjectUtils`、`EnumUtils`、`FileUtils` 与 `org.apache.commons.lang3.*` / `org.springframework.util.*` **同名并存**，且 commons-lang3 是 `api` 依赖（会传递）。写代码时逐个核对 import。

### ⑧ 统一异常处理的状态码约定与直觉相反

`BizException` 经 `ApiExceptionHandler` 返回 **HTTP 422**（不是 200），且**非 Ajax 请求会 `forward("/error/index")`** —— 纯 JSON API 工程若直接扫描 `com.rick.common` 会得到意外的转发。校验失败为 400，`data` 是 `{field,message,rejectedValue}` 列表。
另外 `ExceptionCode.isNull(obj, msg)` 的语义是**断言 obj 为 null**（非 null 才抛），与命名直觉相反。

### ⑨ 位于 `org.springframework` 包下的自有类

`sharp-database` 在 `src/main/java/org/springframework/jdbc/core/namedparam/ParsedSqlHelper.java` 放了一个类，目的是突破 `ParsedSql.getParameterNames()` 的包私有可见性（已用 javap 对 spring-jdbc 6.2.12 实证）。**升级 spring-jdbc 必须回归动态条件查询**；业务代码不要使用该类。

### ⑩ `sharp-formflow` 当前零外部使用者

全仓库 grep 确认 `com.rick.formflow` 只出现在其自身内部，`sharp-test/module/form/` 是空目录、`sharp-test` 也不依赖它。**成熟度未经生产验证**，使用时应更保守：优先走现成 Controller 与 `FormService`，不要深入内部类。
其已核实的具体缺陷：`CpnTypeEnum` 有 22 个值但只有 21 个组件实现类（`SINGLE_CHECKBOX` 无实现，使用必 NPE）；`storageStrategy=CREATE_TABLE` 且 `repositoryName` 为空时，写入分支**只剩被注释掉的代码 → 静默不存储任何数据**；默认 `tplName` 为 `"tpl/form/form"`，而模块只有 `templates/tpl/form.html` 与 `success.html` → 默认视图名解析不到。

### ⑪ `sharp-pay` 不落库、不内置 Controller、不自带 `application.yml`

`sharp-pay` 是纯集成门面（对标 `sharp-mail`），三个易踩点：
- **不落库**：模块**不依赖 `sharp-database`**，订单与支付流水由业务方自行建表存储；`PayService` 只返回结构化结果，调用方负责落库。
- **不内置回调 Controller**：模块只暴露 `PayService.parseNotify(HttpServletRequest)`，业务方需自写 `@PostMapping` 接收回调并回写 `result.getReplyContent()`（微信 JSON / 支付宝 `"success"`）。**不要自己写签名/验签**——微信经 SDK 的 `NotificationParser`（平台证书自动下载）、支付宝经 `AlipaySignature.rsaCheckV1`，手写极易出错或被绕过。
- **通道按需装配**：未配 `sharp.pay.wechat.mch-id` 或 `sharp.pay.alipay.app-id` 的通道 Bean **不创建**，门面调用该通道抛 `UnsupportedOperationException`（设计行为，非 bug）。`PayService` 通过 `ObjectProvider` 注入两通道（可空）。
- **配置文件归属**：模块**故意**只在 `docs/config-sample.yml` 放样例，**不**在 `src/main/resources` 放 `application.yml`（避免重蹈 `sharp-fileupload` 的覆辙——其 jar 携带开发者本机含明文口令的 yml，见陷阱 ⑤）。
- **未联调**：实现基于 SDK 官方 API 与 javap 核实的类签名，编译/发布通过，但**无沙箱真实下单/回调联调**；上线前必须用沙箱走通 createOrder → 回调 → refund。详见 `sharp-pay/ARCHITECTURE.md` 已知缺陷。

### ⑫ `sharp-mail` 收件非线程安全，且有几处硬编码/死代码

`sharp-mail` 是纯集成门面（对标 `sharp-pay`），收件走 IMAP，三个易踩点：
- **顶层 `EmailBuilder` 是死代码**：`com.rick.mail.core.mail.EmailBuilder` 的 `cc`/`bcc` 空实现、无 `build()` 方法、私有构造却又有 `builder()`（自相矛盾）。真正生效的是 `Email.builder()` 返回的 `Email` **内部** `EmailBuilder`（内部类优先）。`[需要确认]`：建议删除顶层 `EmailBuilder.java`。
- **`IMAPStore` 单字段缓存非线程安全**：`MailHandlerImpl.cachedStore` 在并发收件时共享同一连接、相互 `close()`。收件请串行或自建多实例。
- **「已发送」文件夹名硬编码中文 `"已发送"`**：`saveToOutbox` 仅适用中文邮箱服务商；Gmail（`[Gmail]/Sent Mail`）、Outlook（`Sent`）会抛 `MessagingException`。
- **163 兼容性硬编码**：`getStore` 强制 `store.id(clientParams)`，clientParams 写死 `name=sharp-mail`/`vendor=Rick`/`support-email=jkxyx205@163.com`；`searchByMessageId` 内有 `//TODO 163 读取有问题`。
- **`listFolders()` 后不可读邮件**：方法内 `store.close()`，返回的 `Folder` 失效（163 下尤甚，源码注释明确）；要读邮件用 `listMessages`/`searchByMessageId`。
- **profile 参考配置含占位口令**：`application-{gmail,aliyun,qq,163}.yml` 的 `xxx` 是示例占位，仅在激活对应 profile 时加载（不像 `sharp-fileupload` 的 `application.yml` 默认加载，见陷阱 ⑤），业务方仍应用自己的授权码。
- 详见 `sharp-mail/ARCHITECTURE.md` 已知缺陷。

---

## 6. 全局禁止行为

- **不要重新实现已有能力**：JSON（`JsonUtils`）、雪花 ID（`IdGenerator`/`Sequence`）、分页 total 计算（`GridService` 自动生成 count SQL）、驼峰下划线转换（`StringUtils.camelToSnake`）、字典缓存（`DictService`）、文件流拷贝与存储路径拼接（`FileStore`）。
- **不要绕过统一异常处理**自己 try-catch 返回 `Map`，会破坏 422/400/500 的状态码约定。
- **不要直接注入/实例化实现类**：注入 `DocumentService` 而非 `DocumentServiceImpl`；注入 `TableDAO` Bean 而非 `new TableDAOImpl(...)`（会绕过 Bean 覆盖与依赖注入）；DAO 必须是 Spring Bean，否则 `@Resource` 注入与 `@Validated` 校验不生效。
- **不要用 JPA/MyBatis 心智模型解释 `sharp-database` 的注解**：注解同名但语义不同——无懒加载、无 PersistenceContext、无脏检查；`@ManyToOne/@OneToMany/@ManyToMany` 是查询后一次性批量补查；`@Select` 是逐行 N+1。
- **不要混用两套校验体系**：`sharp-common`/`sharp-meta` 用 Jakarta Bean Validation（`@EnumValid`/`@PhoneValid`/`@DictType`），`sharp-formflow` 用**自研** `Validator`/`ValidatorManager`。给表单提交值（Map）加 `@Valid` 无效。
- **不要修改任何模块的 `src/main/resources` 配置来适配自己的环境**（尤其 fileupload 的 `application.yml`、`fdfs_client.properties`）——应通过业务工程自己的配置或同名文件遮蔽。
- **不要扫描 `com.rick.fileupload` 根包**：会把库内的 `FileUploadApplication`（`@SpringBootApplication`）当嵌套配置类处理。只扫 `com.rick.fileupload.client`。
- **不要信任前端传入的排序参数**：Grid 的 `sidx`/`sord` 是字符串拼接进 `ORDER BY` 的（非绑定参数），必须通过 `GridUtils.list(..., sortableColumns...)` 或 `AbstractTableGridService` 提供列白名单。同理不要用 `${...}` 模板变量承接用户输入（`SQLParamCleaner.replaceVars` 是原样字符串替换，存在注入风险）。
- **不要调用已废弃 API**：`com.rick.db.util.OperatorUtils`（整类 `@Deprecated`）、`@Select.nullWhenParamsIsNull`、`SQLParamCleaner.formatSql` 带外置 formatMap 的两个重载、`EnumJsonDeserializer`、`ClassUtils.getFieldGenericClass(Field)` 单参重载、`sharp-meta` 的 `DictValidator`/`Dict2Validator`（未注册进 `@DictType` 的 `validatedBy`，遗留死代码）。
- **不要为了理解一个 API 扫描整个模块**。按第 1 节的顺序读文档。

### 已修复的历史缺陷（不要再用旧的规避写法）

以下 4 处曾是真实缺陷，**现已在源码中修复**。若在历史对话、旧注释或第三方文档里看到对应的规避建议，不要照做：

| 位置 | 旧行为 | 现状（已核实源码） |
|---|---|---|
| `EntityDAOImpl.insertOrUpdateTable(list, refColumnName, refValue)` | 忽略后两参，直接委托 `(list, true, null)` → 按全表范围删除 | ✅ 已透传为 `insertOrUpdateTable(entityList, refColumnName, refValue, true, null)`，范围删除正常生效 |
| `EntityCodeDAOImpl.selectIdsByCodes` | SQL 用 `:codes` 但参数 Map key 是 `"code"` → 命名参数异常 | ✅ 已改为 `Map.of("codes", codes)`，可直接使用 |
| `BaseServiceImpl.updateWithPropertyNames` | 属性名被当列名传给 `baseDAO.update(...)` | ✅ 已改为委托 `baseDAO.updateWithPropertyNames(...)`，由 `EntityDAOImpl` 内部的 `propertyNamesToColumns()` 完成属性名→列名转换 |
| `FileCheckUtils`（sharp-fileupload） | 空类，零方法 | ✅ **文件已整体删除**，源码中无残留引用 |

> ⚠️ **但文件校验能力的缺失依然存在**：`FileCheckUtils` 只是个空占位类，删除它并没有带来任何校验逻辑。`sharp-fileupload` 至今**没有类型白名单、没有大小限制、没有文件名/`groupName` 消毒**（`groupName` 直接拼接存储路径，存在 `../` 逃逸可能）。这些必须由业务方自行实现。详见 `sharp-fileupload/ARCHITECTURE.md`。

---

## 7. 文档维护约定

- 每个模块的文档三件套（`CLAUDE.md` / `API.md` / `ARCHITECTURE.md`）+ 复杂模块的 `docs/`（`api/` 参考、`examples/` 示例、`troubleshooting.md`）随模块存放，**改代码时同步改文档**。
- 所有文档内容均可由源码验证；无法确认处标注 `[需要确认]`，**不要靠推断补齐**。当前 `[需要确认]` 集中在 `sharp-formflow`（10 处，因其无外部调用方，无法从使用侧取证）。
- 示例代码的取证优先级：模块自身 `src/test` → `sharp-test` 的真实调用 → 模块内部调用链改写。仓库内无外部调用样例时须在示例顶部注明。
- 本文件只收录**跨模块**的规则与陷阱；单模块细节请写进该模块的文档，避免两处维护同一条内容。
