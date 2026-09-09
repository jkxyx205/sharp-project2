# sharp-fileupload 模块使用指南（AI 助手入口文档）

## 模块定位

**解决什么**：为 Spring Boot 3 应用提供统一的文件存储抽象（本地磁盘 / MinIO / 阿里云 OSS / FastDFS 四种后端）、一套「附件落库 + 文件落存储」的 Document 附件管理方案（含现成 HTTP 上传/下载/预览接口）、以及基于 Thumbnailator 的图片裁剪/缩放/文字头像生成能力。

**适用**：需要文件上传下载、附件关联业务单据、图片缩略/裁剪的业务工程。

**不适用**：大文件分片/断点续传（模块将文件整体读入内存 `byte[]`，见 `FileMetaUtils.parse`）；需要细粒度存储策略（生命周期、CDN 签名 URL）的场景；PDF 以外的文档转换。

## 源码阅读规则

```
默认不要扫描整个模块源码。
使用模块时：
1. 优先阅读 CLAUDE.md
2. 再阅读 API.md
3. 如果 API.md 已经可以解决问题，不要继续阅读源码
4. 只有在文档无法解决问题、排查 Bug 或确认特殊行为时，才阅读相关源码
```

## 存储后端选型表

| 后端 | 实现类 | 启用方式 | 业务方需补依赖 | 关键配置 |
|---|---|---|---|---|
| **本地磁盘**（默认） | `LocalInputStreamStore` | 自动配置默认注册（`@ConditionalOnMissingBean(InputStreamStore)`），无需任何代码 | 无 | `fileupload.local.root-path`、`fileupload.local.server-url` |
| **MinIO** | `MinioInputStreamStore` | 业务方自行注册 `InputStreamStore` @Bean（默认 local 自动退让） | 编译期需自行加 `io.minio:minio`（模块内是 `implementation`，不传递到业务方编译类路径；运行期 jar 已在） | `fileupload.oss.endpoint`（**必须含 http(s):// 前缀**）、`bucket-name`、`access-key-id`、`access-key-secret` |
| **阿里云 OSS** | `OSSInputStreamStore` | 业务方自行注册 `InputStreamStore` @Bean + 自建 `OSS` 客户端 Bean | `com.aliyun.oss:aliyun-sdk-oss`（模块内 `compileOnly`，**必须自补，否则 NoClassDefFoundError**） | `fileupload.oss.endpoint`（**不带 scheme**，URL 拼成 `https://{bucket}.{endpoint}/`）、`bucket-name`、AK/SK |
| **FastDFS** | `FastDFSInputStreamStore` | 业务方自行注册 `InputStreamStore` @Bean（构造参数为 classpath 上的属性文件名） | `org.csource:fastdfs-client-java`（`compileOnly`，**必须自补**） | classpath 上的 `fdfs_client.properties`（模块自带一份，业务工程放同名文件到 classpath 根可遮蔽以修改 tracker 地址） |

**切换机制（一句话）**：`FileUploadAutoConfig` 中默认后端是 `@Bean @ConditionalOnMissingBean public InputStreamStore inputStreamStore(...)` → `LocalInputStreamStore`；业务方只要注册**任意一个自己的 `InputStreamStore` Bean**，local 默认即退让；`FileStore`（`@Primary`）与 `ImageService` 自动包装该唯一 Bean。注册多个 `InputStreamStore` 且无 `@Primary` 会导致启动失败。依据：`core/config/FileUploadAutoConfig.java`。

## 使用原则

1. **注入接口/门面，不注入实现**：业务代码注入 `FileStore`（推荐，支持 MultipartFile/File/FileMeta 上传）或 `InputStreamStore`；禁止注入 `LocalInputStreamStore` 等具体实现。
2. **禁止自己写文件流拷贝 / 自己拼存储路径**：路径规则统一为 `{groupName}/{storeName}.{extension}`（`AbstractInputStreamStore.getFullPath`），本地磁盘绝对路径为 `{root-path}/{groupName}/{文件名}`（`LocalInputStreamStore`）。自拼路径会与 `getURL`/`delete`/`download` 不一致。
3. **附件要落库就用 `DocumentService`**，不要只用 `FileStore` 存完就丢——`sys_document` 表记录 id↔文件 的映射，下载/预览/删除/重命名接口全部基于该表。
4. **HTTP 接口不是自动注册的**：自动配置只注册存储 Bean 与 `CorsFilter`；`DocumentController`/`ImageController`/`DocumentServiceImpl`/`DocumentDAO` 需要业务方组件扫描 `com.rick.fileupload.client` 包（**不要扫 `com.rick.fileupload` 根包**，会把库里的 `FileUploadApplication`（@SpringBootApplication）当配置类扫进来）。
5. **不要修改 `Constants.COMPRESS_THRESHOLD`**（public static 可变全局量，影响所有图片自动压缩行为）。
6. 图片以外的文件不要调用 `ImageService`/`/images/**`，会抛 `NotImageTypeException`。

## 引入清单

**Gradle 坐标**（多模块工程内推荐 project 依赖）：

```gradle
implementation project(":sharp-fileupload")
// 或 Maven 坐标：com.rick.fileupload:sharp-fileupload:<version>
```

⚠️ **版本不一致警示**：本模块 `build.gradle` 中 `version = libs.versions.sharp.get()` → 实际发布版本为 **`0.0.1-SNAPSHOT`**（`gradle/libs.versions.toml`）；而 `sharp-formflow/build.gradle` 引用的是 `com.rick.fileupload:sharp-fileupload:3.0-SNAPSHOT`。坐标依赖 `3.0-SNAPSHOT` 会解析到 mavenLocal 中的历史旧产物，与当前源码不一致。新接入方务必用 `project(":sharp-fileupload")` 或对齐版本号。

**必须自行补齐的 compileOnly 依赖**（按需）：

| 依赖 | 坐标（libs.versions.toml） | 何时需要 |
|---|---|---|
| 阿里云 OSS SDK | `com.aliyun.oss:aliyun-sdk-oss:3.10.2` | 用 `OSSInputStreamStore` |
| FastDFS 客户端 | `org.csource:fastdfs-client-java:1.29` | 用 `FastDFSInputStreamStore` |
| PDFBox | `org.apache.pdfbox:pdfbox:2.0.12` | 用 `FileConvertUtils.pdf2Image` |
| iText | `com.lowagie:itext:2.0.7` | 同上（`PdfReader` 取页数） |
| MySQL 驱动 | `mysql:mysql-connector-java:5.1.47` | Document 落库用 MySQL 时（建议改用新版驱动坐标） |
| MinIO SDK | `io.minio:minio:8.5.7` | 编译期引用 `MinioClient` 时需显式加（模块内为 implementation，运行期已有） |

**最小 application.yml（本地存储 + 附件落库）**：

```yaml
spring:
  datasource:            # DocumentService 依赖 sharp-database，需要数据源
    url: jdbc:mysql://localhost:3306/yourdb?...
    username: xxx
    password: xxx
  servlet:
    multipart:
      max-file-size: 50MB      # Spring 上传大小限制必须由业务工程自己配
      max-request-size: 50MB
fileupload:
  tmp: /data/fileupload/tmp    # 批量下载(zip)临时目录，用批量下载必填
  local:
    root-path: /data/fileupload          # 磁盘存储根目录
    server-url: http://your-host:7892/   # 静态文件服务地址(映射到 root-path)，注意结尾 /
```

**是否需要建表**：**需要，手动执行**。`sql/sys_document-mysql.sql` 或 `sql/sys_document-postgres.sql`（位于模块根目录 `sql/`，不在 classpath，不会自动执行；MySQL 脚本含 `DROP TABLE IF EXISTS`，慎在已有库直接跑）。仅使用 `FileStore`/`InputStreamStore`/`ImageService`（不经 DocumentService）时可不建表。

## ⚠️ 引入即生效的副作用（务必知晓）

1. **全放行 CORS 过滤器**：自动配置无条件注册 `CorsFilter`（`/**`，允许所有 origin/header/method），且该 Bean 无 `@ConditionalOnMissingBean`（定义同名 Bean 会启动冲突）。需要收紧时只能 `spring.autoconfigure.exclude: com.rick.fileupload.core.config.FileUploadAutoConfig` 排除整个自动配置，并自行补注册 `InputStreamStore`/`FileStore`/`ImageService` Bean（详见 API.md Common Mistakes #10）。
2. **模块 jar 内自带 `application.yml`**（含开发者本机 datasource、multipart 50MB、fileupload 样例配置）与 `fdfs_client.properties`。业务工程有自己的 classpath `application.yml` 时通常会遮蔽 jar 内文件；但若业务工程只用 `application.properties`，jar 内 `application.yml` 可能被加载，带入错误的 datasource/multipart/fileupload 配置。建议排查启动生效配置来源。
3. `com.rick.fileupload.FileUploadApplication` 是模块独立运行/测试用的 `@SpringBootApplication`，业务工程严禁组件扫描到它。

## API 文档索引

| 文档 | 内容 |
|---|---|
| `API.md` | **首选**。全部公开 API：存储接口、后端实现、Document 附件服务、图片处理、HTTP 接口、模型、配置、异常、Common Mistakes |
| `ARCHITECTURE.md` | 依赖链、自动配置全貌、上传/下载链路、扩展新后端、设计约束 |
| `docs/api/configuration.md` | 四种后端完整可复制 yml 样例 + 逐 key 说明 |
| `docs/api/http-api.md` | `/documents/**`、`/images/**` HTTP 接口参考（含 curl） |
| `docs/examples/upload-local.md` | 本地存储最小接入示例 |
| `docs/examples/upload-minio.md` | MinIO / 阿里云 OSS / FastDFS 自定义后端接入示例 |
| `docs/examples/attachment-with-document.md` | 附件落库 + 业务表关联 + 下载/删除完整示例 |
| `docs/examples/image-thumbnail.md` | 缩略/裁剪/文字头像示例 |
| `docs/troubleshooting.md` | 异常 → 触发条件 → 处理方式；启动/上传失败排查 |

## 禁止行为

- 🚫 直接实例化/注入 `DocumentServiceImpl`（注入 `DocumentService` 接口）。
- 🚫 扫描或引用 `FileUploadApplication`、`FileUploadAutoConfig`。
- 🚫 引用 `FileCheckUtils`（曾是零方法的空占位类，**已从源码删除**）。
- ⚠️ 但请注意：**模块至今没有任何文件类型/大小/文件名安全校验**，这些必须由业务方自行实现（详见 API.md §6.2）。不要把"类被删了"误读为"已有校验"。
- 🚫 绕过 `DocumentService` 直接操作 `sys_document` 表。
- 🚫 修改 `src/main/resources/application.yml`、`fdfs_client.properties` 来适配业务环境（应通过业务工程自己的配置/同名文件遮蔽）。
- 🚫 假设 HTTP 接口有认证：模块内无任何 `@PreAuthorize`/Security 配置，所有接口默认裸奔，业务方必须自行加安全控制。
