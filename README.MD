# sharp-project2

基于 JDK 17 · Spring Boot 3.5.7 · Gradle 多模块 构建，Lombok 辅助，核心为**自研注解驱动 ORM（非 JPA、非 MyBatis）**。

这是一套自研基础设施库，按模块独立发布到本地 Maven 仓库，业务工程按需引入。

---

## 模块清单

| 模块 | group | 定位 | 自动配置 |
|---|---|---|---|
| `sharp-common` | `com.rick.common` | 统一返回 / 业务异常 / 校验注解 / JSON / ID / 工具类 / Lambda 元信息 | ❌ 需手动装配 |
| `sharp-database` | `com.rick.db` | 注解驱动 ORM + 分页 Grid + 建表生成器 + 多方言 | ✅ |
| `sharp-meta` | `com.rick.meta` | 数据字典（Dict）+ 系统参数（Property） | ✅ |
| `sharp-fileupload` | `com.rick.fileupload` | 文件存储抽象（local/MinIO/OSS/FastDFS）+ 附件落库 + 图片处理 | ✅ |
| `sharp-formflow` | `com.rick.formflow` | 动态表单引擎（组件 + 自研校验 + EAV/宽表双存储 + 服务端渲染页面） | ✅ |
| `sharp-mail` | `com.rick.mail` | 邮件收发门面：SMTP 发 + IMAP 收 + 保存已发送 + 附件/正文解析 | ✅ |
| `sharp-pay` | `com.rick.pay` | 支付门面：微信（API v3）+ 支付宝，扫码/JSAPI/H5/App/退款/对账/回调验签 | ✅ |
| `sharp-test` | `com.rick` | 本地试验场，不发布。是 common/database/meta 的真实用法示例源 | — |

> 各模块的 API 细节、内部架构与已知缺陷，请查阅对应模块目录下的 `CLAUDE.md` / `API.md` / `ARCHITECTURE.md`，根目录 `CLAUDE.md` 提供跨模块导航与陷阱清单。

---

## 依赖关系

```
sharp-common  ← 无 Spring 运行期依赖（Spring 全部 compileOnly）
     ↑ api
sharp-database
     ↑ implementation          ↑ implementation
sharp-meta              sharp-fileupload
     ↑ implementation          ↑ Maven 坐标（非 project 依赖）
     └──────── sharp-formflow ─┘

sharp-pay   ← 独立，仅官方 SDK（wechatpay-java + alipay-sdk-java）
sharp-mail  ← 独立，仅 spring-boot-starter-mail + jsoup
```

`implementation` 表示**不传递**：业务方若要直接使用上游类型（如 `TableDAO`），需自行声明依赖。

`sharp-pay` 与 `sharp-mail` 是纯集成门面：**不落库**，订单/附件由业务方自行存储。

---

## 功能速查

| 需求 | 使用模块 |
|---|---|
| 统一返回体 / 业务异常 / 全局异常处理 | `sharp-common` |
| JSON 序列化、雪花 ID 生成、参数校验 | `sharp-common` |
| 实体建模、单表/主从表 CRUD | `sharp-database` |
| 分页查询、动态条件报表 | `sharp-database` |
| 逻辑删除、审计字段自动填充 | `sharp-database`（需注册 `ExtendTableDAOImpl`） |
| 由实体生成建表 DDL | `sharp-database` |
| 下拉选项 / 编码转中文名 / 字典校验 | `sharp-meta` |
| 运行时可变全局键值参数 | `sharp-meta` |
| 文件上传下载、附件关联业务单据 | `sharp-fileupload` |
| 图片缩略、裁剪、文字头像 | `sharp-fileupload` |
| 用户自定义表单（字段可配置） | `sharp-formflow` |
| 微信/支付宝支付（下单/查询/退款/回调） | `sharp-pay` |
| 邮件收发（SMTP 发 / IMAP 收 / 附件） | `sharp-mail` |

---

## 构建与引入

**发布到本地仓库**：`./mvn.sh`（内部执行 `gradlew publishToMavenLocal`，固定 JDK 17）。版本统一来自 `gradle/libs.versions.toml` 的 `sharp = "0.0.1-SNAPSHOT"`。

**业务工程引入**（Gradle）：

```gradle
repositories { mavenLocal() }

dependencies {
    implementation "com.rick.db:sharp-database:0.0.1-SNAPSHOT"
    implementation "com.rick.meta:sharp-meta:0.0.1-SNAPSHOT"
    // compileOnly 能力需业务方自备（不会传递）：
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    runtimeOnly  'org.postgresql:postgresql'
}
```

**`sharp-database` 生效前提**：

```yaml
spring:
  datasource: { url: ..., username: ..., password: ... }
sharp:
  database:
    type: PostgreSQL              # 默认 MySQL5，须与实际库一致
    entity-base-package: com.acme.**.entity   # 未配置 → 启动 NPE
```

---

## 技术栈

- JDK 17
- Spring Boot 3.5.7
- Gradle（Kotlin DSL + `libs.versions.toml` 版本目录）
- Lombok
- 自研注解驱动 ORM（非 JPA / 非 MyBatis）
