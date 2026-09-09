# sharp-fileupload 架构说明

## 1. 在依赖链中的位置

```
sharp-common ← sharp-database ← { sharp-meta, sharp-fileupload } ← sharp-formflow
```

- **对 sharp-database 的依赖**（`implementation project(":sharp-database")`）：Document 附件体系落库所用——`DocumentDAO extends EntityDAOImpl<Document, Long>`、实体注解（`@Table/@Id/@Column/@Embedded/@Transient`）、雪花 ID 填充（`@Id` 默认 `GenerationType.SEQUENCE` → `IdGenerator.getSequenceId()`，见 sharp-common）。`implementation` 意味着这些 DB 类不传递到业务方编译类路径，但 Document 功能运行期依赖 sharp-database 自动配置生效（**业务工程必须配置 DataSource**，`SharpDatabaseAutoConfiguration` 带 `@ConditionalOnSingleCandidate(DataSource.class)`）。
- **对 sharp-common**：直接使用（`FileUtils`/`IdGenerator`/`HttpServletResponseUtils`/`ZipUtils`/`Result`）。fileupload 的 `api project(':sharp-common')` 已注释；sharp-database 虽以 `api project(':sharp-common')` 暴露 common，但 fileupload 对 database 是 `implementation` → **database 与 common 都不传递到业务方编译类路径（运行期均在）**。业务代码若直接引用 `Result`、`EntityDAO` 等 common/database 类，需自行添加对应依赖。
- **被 sharp-formflow 依赖**：formflow 的 `build.gradle` 以 Maven 坐标 `com.rick.fileupload:sharp-fileupload:3.0-SNAPSHOT`（排除 sharp-common/sharp-database 传递）引入，**但 formflow 源码中没有任何 `com.rick.fileupload` 的 import**（全仓 grep 证实）；`form/cpn/Attachment.java`、`SingleImage.java` 仅做表单值的 JSON ↔ Map 转换，不注入本模块任何 Bean。依赖意图推断为让下游应用运行期获得文件上传自动配置 [需要确认]。
  ⚠️ 版本错配：本模块实际 version = `libs.versions.sharp` = **0.0.1-SNAPSHOT**，formflow 引用的 3.0-SNAPSHOT 会解析到 mavenLocal 历史产物。

## 2. 核心抽象与策略切换机制

```
                InputStreamStore (SPI 接口)
                 ▲            ▲
   AbstractInputStreamStore   │ (默认实现，OkHttp 读取 + 雪花文件名 + URL 拼接模板)
        ▲        ▲        ▲   │
   Local     Minio     OSS  FastDFS        FileStore（装饰器，实现同接口，
  InputStreamStore ×4                     增加 MultipartFile/File/FileMeta 上传）
        └──────────┬──────────┘                 │
              容器里只选一个                 @Primary Bean，包装唯一 InputStreamStore
```

- `InputStreamStore`：6 方法 SPI（store×2 / delete / getURL / getInputStream / getByteArray）。
- `AbstractInputStreamStore`：**扩展点模板**（四个实现全部继承）。默认：`store(group, ext, is)` → 雪花 ID 作 storeName；`getURL = getServerUrl() + group + "/" + path`；`getInputStream` → OkHttp GET url；`getByteArray` → IOUtils。子类必须实现 `getServerUrl()`，通常再实现 `store(4参)`、`delete`，对象存储覆写 `getInputStream` 走 SDK。
- `FileStore`：装饰器（构造注入委托），额外提供 `upload(List<MultipartFile>, group)`、`storeFiles(List<File>, group)`、`storeFileMeta(List<FileMeta>, group)`；每个 store 后回填 `FileMeta.groupName/path/url`。**注入 `FileStore` 即同时获得装饰方法与 SPI 方法。**
- **切换机制**：唯一开关是「容器里 `InputStreamStore` 类型的 Bean 是谁」。默认 `@ConditionalOnMissingBean` → Local；业务方定义任意自己的 `InputStreamStore` Bean → local 退让。多 Bean 无 `@Primary` → 启动失败。没有 `@ConditionalOnProperty` 式的 yml 开关。

## 3. 自动配置全貌（FileUploadAutoConfig）

注册入口（双份）：
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` → Boot 3 生效；
- `META-INF/spring.factories`（`EnableAutoConfiguration=...FileUploadAutoConfig`）→ Boot 2 遗留，**Boot 3 不再读取该键，实际不生效**。

`@Configuration` + `@EnableConfigurationProperties({FileUploadProperties, LocalProperties, OSSProperties})`，**无 @ConditionalOnClass/@ConditionalOnProperty，引入依赖即全部生效**：

| Bean | 条件 | 说明 |
|---|---|---|
| `corsFilter()` | **无条件** | 全放行 CORS（`/**`，所有 origin/header/method）。副作用见 API.md |
| `inputStreamStore(LocalProperties)` | `@ConditionalOnMissingBean`（按返回类型 InputStreamStore） | 默认 `LocalInputStreamStore` |
| `fileStore(InputStreamStore)` | `@Primary`，无条件 | 装饰当前唯一 InputStreamStore |
| `imageService(InputStreamStore)` | 无条件 | 图片处理 |

**不注册**：`DocumentController`、`ImageController`、`DocumentServiceImpl(@Service)`、`DocumentDAO(@Repository)`——需业务方组件扫描 `com.rick.fileupload.client`。
**整体不生效的情况**：`spring.autoconfigure.exclude` 排除；或 Boot 2（imports 文件不被读取时靠 spring.factories，行为一致）；此外无任何条件化关闭手段。
**属性绑定始终发生**：三个 Properties 类无条件绑定（不用 OSS 也会绑 `fileupload.oss.*`，无值即 null，无害）。

## 4. 上传完整链路（以 POST /documents/upload 为例）

```
HTTP multipart 请求
 → [Spring multipart 解析，受业务方 spring.servlet.multipart.* 限制]
 → DocumentController.fileUpload(MultipartHttpServletRequest, groupName默认"upload")
 → FileMetaUtils.parse(request, 参数name或"file")     // 每个文件整体读入 byte[]（内存！）
 → DocumentService.store(List<FileMeta>, groupName)
    → FileStore.storeFileMeta                          // 逐文件
       → InputStreamStore.store(group, extension, ByteArrayInputStream(data))
          // storeName = 雪花ID；local: mkdirs + FileOutputStream 写 {root}/{group}/{id}.{ext}
          // minio/oss: putObject {group}/{id}.{ext}；fastdfs: upload_file（忽略 storeName）
       → 回填 FileMeta.groupName/path/url
    → BeanUtils.copyProperties → Document
    → documentDAO.insert(document)                     // id = IdGenerator.getSequenceId()
 → Result<List<Document>>（id 序列化为字符串；data 被 @JsonIgnore）
```

## 5. 下载/预览链路

- `GET /documents/download/{id}`：findById（回填 url）→ `HttpServletResponseUtils.getOutputStreamAsAttachment`（Content-disposition: attachment; filename=fullName，Content-Type 按扩展名映射）→ `fileStore.getInputStream(group, path)` 流拷贝（local 直读磁盘；minio/oss 走 SDK；fastdfs 走 OkHttp GET url）。
- `GET /documents/download?id=…&id=…`：`fileupload.tmp` 下建临时目录 → 全部文件落盘 → `ZipUtils.zipDirectoryToZipFile` → 输出 zip → `FileUtils.deleteQuietly` 清理。
- `GET /documents/preview/{id}`：302 → 存储直链（浏览器直连存储服务）。
- `GET /documents/preview2/{id}[/{fileName}.{docx|xlsx|pptx}]`：inline 输出；图片走 `ImageService.write`（Thumbnailator 实时处理），非图片原样输出；带扩展名的第二种 URL 专供 Office Online 预览服务。
- `GET /images/{id}?w=…`：findById → ImageService.write(ImageParam) → inline。

## 6. 模块自带资源文件的设计意图与风险

| 资源 | 意图 | 风险 |
|---|---|---|
| `FileUploadApplication`（@SpringBootApplication） | 模块可独立启动调试；`@SpringBootTest` 测试（无 test resources）借此获得配置类 | 业务方组件扫描 `com.rick.fileupload` 根包会将其当作嵌套配置（@EnableAutoConfiguration/@ComponentScan 再次生效）→ 只扫 `.client` 子包 |
| `src/main/resources/application.yml`（datasource/multipart/fileupload 样例，含开发者本机明文口令） | 支撑独立运行与 5 个测试类 | jar 内 classpath 根 yml：业务工程有自己的 `application.yml` 时通常遮蔽之；业务工程仅用 `application.properties` 时可能被加载 → 带入错误 datasource 等。**不要**把它当成"模块默认配置"——它只是作者的开发环境配置 |
| `fdfs_client.properties` | FastDFS 客户端默认 tracker | 常驻 classpath；业务方同名文件（应用 classes 先于 jar）可遮蔽；或构造 `FastDFSInputStreamStore` 时传自定义文件名 |
| `spring.factories` | Boot 2 兼容遗留 | Boot 3 下不生效（冗余无害） |

## 7. 扩展点：接入新存储后端

1. 继承 `AbstractInputStreamStore`，实现 `store(group, storeName, ext, is)`、`delete(group, path)`、`getServerUrl()`（以 `/` 结尾）；对象存储建议覆写 `getInputStream` 走 SDK（否则默认 OkHttp GET url，要求 url 匿名可读）。
2. 业务工程注册：
```java
@Bean
public InputStreamStore myStore(MyProps props) { return new MyInputStreamStore(props); }
```
默认 local 因 `@ConditionalOnMissingBean` 自动退让，`FileStore/ImageService/DocumentService` 无需改动。
3. 参照 `MinioInputStreamStore`（约 80 行）为最简样板。

## 8. 设计约束与已知限制（均源自代码事实）

1. **全内存文件处理**：`FileMetaUtils.parse` 将文件整体读入 `byte[]`；`FileMeta.data` 贯穿 Document 链路 → 大文件 OOM 风险；`sys_document.size` 列为 int（~2.1GB 上限）。
2. **无文件校验**：模块内没有任何校验代码（原占位类 `FileCheckUtils` 是空类，已被删除）；无类型白名单/大小限制/`groupName` 消毒（groupName 直接拼路径，存在 `../` 逃逸可能）。大小限制完全依赖业务方 Spring multipart 配置，类型与文件名安全校验必须由业务方自行实现。
3. **同名覆盖**：local/MinIO/OSS 以 `{storeName}.{ext}` 为键，重复 storeName 直接覆盖（默认雪花 ID 不冲突；`createImage(text, group, storeName)` 固定 storeName 即利用覆盖语义）。
4. **FastDFS 特殊性**：storeName 被忽略；`FileStore.storeFileMeta` 回填的 groupName 为入参而非集群返回值（不一致时后续读写用错 group）；每次操作新建 Tracker 连接；HTTP 读取依赖 `G_tracker_http_port`（自带 properties 的键 `fastdfs.http_tracker_http_port` 疑似笔误 [需要确认]）。
5. **MinIO 实现吞异常**：全部包装为无 message 的 `new IOException()`。
6. **删除语义**：`DocumentService.delete` 存储删除失败仅 printStackTrace（DB 记录仍被物理删除 → 可能产生孤儿文件）；DB 删除是物理 DELETE（`deleteByIds`），`is_deleted` 列存在但未见逻辑删除改写。
7. **findById 无 404 语义**：`Optional.get()` → `NoSuchElementException` → 500。
8. **审计字段填充**：`DocumentServiceImpl` 未显式设置 createBy/createTime；是否由 sharp-database 的 InsertUpdateCallback 机制自动填充 [需要确认]（建表脚本 create_time NOT NULL，`DocumentServiceTest` 可运行说明本地链路能填充或有默认值）。
9. **CORS 全放行 + 接口无认证**：见 API.md Common Mistakes #10。
10. **PDF 能力孤立**：`FileConvertUtils` 是 pdfbox/itext 的唯一消费方，未接入任何 Controller/Service 链路，需业务方自行调用并补依赖。
