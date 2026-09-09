# sharp-fileupload API 参考

> 依据源码版本：`sharp-fileupload`（version `0.0.1-SNAPSHOT`，JDK 17 / Spring Boot 3.5.7）。
> 所有签名、默认值、URL、行为均已逐一核对源码；无法从源码确认处标注 `[需要确认]`。

## 推荐等级总览

| 等级 | 类 |
|---|---|
| ⭐ 推荐使用 | `FileStore`、`InputStreamStore`（接口）、`AbstractInputStreamStore`（扩展新后端时继承）、`DocumentService`（接口）、`Document`、`FileMeta`、`StoreResponse`、`FileMetaUtils`、`ImageService`、`ImageParam`、`NotImageTypeException` |
| ⚠️ 特定场景使用 | `LocalInputStreamStore`、`MinioInputStreamStore`、`OSSInputStreamStore`、`FastDFSInputStreamStore`（仅选型注册 Bean 时）、`DocumentDAO`、`DocumentController`、`ImageController`、`FileConvertUtils`（PDF→图片，需自补依赖）、`NameImageCreator`、`OSSProperties`（构造 Minio/OSS Bean 时作参数）、`Constants` |
| ❌ 不推荐使用 | `LocalProperties`/`FileUploadProperties`（仅框架内部消费，业务方通过 yml 配置即可） |
| 🗑 已删除 | `FileCheckUtils`（曾是零方法的空占位类，已从源码移除；**文件校验能力依然不存在**，见 §6.2） |
| 🚫 内部 API，禁止业务代码调用 | `FileUploadApplication`（模块独立调试入口）、`FileUploadAutoConfig`、`DocumentServiceImpl`（注入其接口 `DocumentService`） |
| 🗑 已废弃 | 无（源码中无 `@Deprecated`） |

---

## 1. 核心存储接口

### 1.1 `InputStreamStore`（接口）⭐

`com.rick.fileupload.core.InputStreamStore`。最底层存储 SPI，四种后端均实现它。**业务方通常注入 `FileStore`（它同时实现了本接口），只有在注册自定义后端或需要最原始存取语义时直接面对本接口。**

方法一览（全部在接口上声明）：

| 方法 | 说明 |
|---|---|
| `StoreResponse store(String groupName, String extension, InputStream is) throws IOException` | 存储流，文件名由框架生成（`AbstractInputStreamStore` 默认用 `IdGenerator.getSequenceId()` 雪花 ID 作为文件名） |
| `StoreResponse store(String groupName, String storeName, String extension, InputStream is) throws IOException` | 存储流并**指定磁盘文件名** `storeName`（不含扩展名）。注意：FastDFS 后端会忽略 `storeName`（见 2.4） |
| `void delete(String groupName, String path) throws IOException` | 按 `groupName` + `path`（存储文件名，含扩展名）删除 |
| `String getURL(String groupName, String path)` | 拼访问 URL，规则 `getServerUrl() + groupName + "/" + path`（无受检异常） |
| `InputStream getInputStream(String groupName, String path) throws IOException` | 读取文件流。local 后端直读磁盘；MinIO/OSS 走 SDK；FastDFS 及抽象基类默认实现走 OkHttp GET `getURL(...)` |
| `byte[] getByteArray(String groupName, String path) throws IOException` | 读为字节数组（默认实现 `IOUtils.toByteArray(getInputStream(...))`） |

- **groupName 语义**：逻辑分组，同时是存储路径的第一级目录（local：`{root-path}/{groupName}/`；MinIO/OSS：对象 key 前缀 `{groupName}/`；FastDFS：作为集群 group 名传入）。
- **path 语义**：`store` 返回的 `StoreResponse.path`，即存储文件名（`{storeName}.{extension}`；extension 为空时就是 `storeName`，见 `com.rick.common.util.FileUtils.fullName`）。
- **不应该怎么使用**：不要保存 `StoreResponse` 后自行拼 URL / 路径；不要对同一 `groupName+storeName+extension` 重复 store（local/MinIO/OSS 会**覆盖同名文件**，无告警）。
- **相关 API**：`FileStore`、`StoreResponse`、`AbstractInputStreamStore`。

### 1.2 `FileStore` ⭐（推荐的业务注入入口）

`com.rick.fileupload.core.FileStore`，实现 `InputStreamStore` 并包装一个委托实例；由 `FileUploadAutoConfig` 以 `@Bean @Primary` 注册。**除 1.1 全部方法的透传外，额外提供三类便捷上传：**

#### `List<? extends FileMeta> upload(List<MultipartFile> multipartFileList, String groupName) throws IOException`
- 用途：直接上传 HTTP multipart 文件列表。
- 参数：`multipartFileList`（必填，空/null 返回 `Collections.emptyList()`）；`groupName`（必填，存储分组）。
- 行为：对每个文件 `FileMetaUtils.parse`（**整个文件读入内存 byte[]**）→ `store(groupName, extension, stream)` → 回填 `fileMeta.groupName/path/url`。
- 返回：入参解析出的 `FileMeta` 列表（含 `url`）。原始文件名保留在 `FileMeta.name/extension`。

#### `List<? extends FileMeta> storeFiles(List<File> fileList, String groupName) throws IOException`
- 用途：上传磁盘 `java.io.File` 列表；文件名取 `file.getName()`，contentType 通过 URLConnection 猜测。

#### `List<? extends FileMeta> storeFileMeta(List<? extends FileMeta> fileMetaList, String groupName) throws IOException`
- 用途：上传已解析的 `FileMeta`（**要求 `data` 字段已填充字节内容**，javadoc 明示）。`DocumentService.store` 内部即调用它。
- 注意：该方法回填 `fileMeta.setGroupName(groupName)` 用的是**调用方传入的 groupName**，而非 `StoreResponse.groupName`。对 FastDFS（集群实际返回 group 可能不同）存在不一致隐患，见 2.4 警告。

- **调用示例**（源自 `src/test/java/com/rick/fileupload/FileStoreTest.java`）：

```java
@Autowired
private FileStore fileStore;

File file = new File("/data/demo/1.jpg");
FileMeta fileMeta = FileMetaUtils.parse(file);
FileMeta stored = fileStore.storeFileMeta(Lists.newArrayList(fileMeta), "upload").get(0);
String url = stored.getUrl();   // {server-url}upload/16xxxx.jpg
```

- **使用场景**：所有非 HTTP-Controller 的编程式上传；需要拿 `FileMeta`（原名/大小/类型）+ 存储结果的场合。
- **不应该怎么使用**：不要 `new FileStore(...)`，容器已有 `@Primary` Bean；不要注入 `FileStore` 后强转成具体实现类。
- **相关 API**：`FileMetaUtils`、`DocumentService.store`（需要落库时优先后者）。

### 1.3 `AbstractInputStreamStore` ⭐ 扩展点（非内部基类）

`com.rick.fileupload.core.AbstractInputStreamStore`。**判定为扩展点的依据**：public abstract 类、无 Spring 注解、四个官方实现全部继承它、提供了通用默认实现并留出一个 `protected abstract` 钩子。接入新存储后端（如 S3、COS）时应继承它，只需实现：

- `StoreResponse store(String groupName, String storeName, String extension, InputStream is)`（必须）
- `void delete(String groupName, String path)`（必须）
- `protected String getServerUrl()`（必须，URL 前缀，**需以 `/` 结尾**——`getURL` 直接字符串拼接）
- 可选覆写 `getInputStream`（默认走 OkHttp GET `getURL(...)`，适用于「存储可 HTTP 直读」的后端）

免费获得的默认行为：`store(group, ext, is)` 自动生成雪花 ID 文件名；`getURL = getServerUrl() + groupName + "/" + path`；`getByteArray` 基于 `getInputStream`。
辅助方法：`protected String getFullPath(String groupName, String path)` → `groupName + "/" + path`（对象存储的 object key）。

### 1.4 模型

#### `FileMeta` ⭐（`core/model/FileMeta.java`）

| 字段 | 类型 | 谁填充 | 说明 |
|---|---|---|---|
| `name` | String | `FileMetaUtils.parse` / `setFullName` | 原始文件名（不含扩展名） |
| `extension` | String | 同上 | 扩展名（不含点） |
| `contentType` | String | parse 时来自 MultipartFile / URLConnection 猜测 | MIME 类型 |
| `size` | Long | parse 填充；getter 在 size 为 null 且 data 非 null 时返回 `data.length` | 字节数 |
| `groupName` | String | `FileStore.storeXxx` 回填 | 存储分组 |
| `path` | String | `FileStore.storeXxx` 用 `StoreResponse.path` 回填 | 存储文件名（含扩展名） |
| `data` | byte[] | parse 时整体读入 | 文件内容；DB `@Transient` + `@JsonIgnore`（不会入库、不出现在 JSON 响应） |
| `url` | String | store 回填 / `DocumentService.findById` 回填 | 访问 URL；DB `@Transient` |

派生方法：`getFullName()` = `name + "." + extension`（extension 空则仅 name）；`getFullPath()` = `groupName + "/" + path`；`setFullName(fullName)` 自动拆 name/extension。
**取 URL / 大小 / 类型**：`fileMeta.getUrl()` / `getSize()` / `getContentType()`。JSON 序列化时 `fullName`、`fullPath` 会作为派生属性一并输出。

#### `StoreResponse` ⭐（`core/model/StoreResponse.java`，Lombok `@Value` 不可变）

| 字段 | 说明 |
|---|---|
| `groupName` | 实际存储分组（FastDFS 下为集群返回值，可能与入参不同） |
| `path` | 存储文件名（含扩展名） |
| `fullPath` | local：磁盘绝对路径；MinIO/OSS：object key `group/name.ext`；FastDFS：`getFullPath(groupName, path)` |
| `url` | 访问 URL |

#### `Constants` ⚠️（`core/Constants.java`）

仅一个常量：`public static double COMPRESS_THRESHOLD = 500 * 1024`（图片自动压缩阈值，实际 500KB；javadoc 写 300KB 与代码不符，以代码为准）。**public static 非 final，全局可变**，改动影响所有 `ImageService` 行为，不建议修改。

---

## 2. 存储后端实现与选型

四个实现类均**无 Spring 注解**，除 local 外都不会被自动注册；均继承 `AbstractInputStreamStore`。

### 2.1 `LocalInputStreamStore` ⚠️（默认后端）

- **启用条件**：`FileUploadAutoConfig#inputStreamStore` `@Bean @ConditionalOnMissingBean`（按 `InputStreamStore` 类型判断）→ 容器中没有其他 `InputStreamStore` Bean 时自动生效。无 `@ConditionalOnProperty/OnClass`，**不配任何属性也会注册**。
- **构造**：`new LocalInputStreamStore(LocalProperties)`。
- **配置**：`fileupload.local.root-path`（磁盘根目录，**无默认值；不配时路径拼成字符串 `"null/{group}"`，文件写到进程工作目录下的 `null/` 目录**）、`fileupload.local.server-url`（URL 前缀，业务方需自建静态文件服务映射到 root-path，源码注释示例为 `http-server -p 7892`）。
- **存储行为**：目录 `{root-path}/{groupName}/` 不存在时自动 `mkdirs()`；文件写入 `{root-path}/{groupName}/{storeName}.{extension}`；同名**直接覆盖**。
- **读取**：`getInputStream` 直读磁盘（`FileInputStream`，不走 HTTP）；`delete` 用 `FileUtils.forceDelete`，**文件不存在时抛 `FileNotFoundException`（IOException 子类）**。
- **URL 形态**：`{server-url}{groupName}/{path}`（`server-url` 需自带结尾 `/`）。
- **适用**：单机开发/测试、小型部署。多实例部署需共享存储，否则 URL/读取不一致。

### 2.2 `MinioInputStreamStore` ⚠️

- **启用条件**：不会被自动注册。业务方需自建两个 Bean：`MinioClient` 与 `InputStreamStore`（示例见 `docs/examples/upload-minio.md`）。注册后默认 local 因 `@ConditionalOnMissingBean` 退让。
- **构造**：`new MinioInputStreamStore(MinioClient, OSSProperties)`。
- **依赖**：模块 `implementation(libs.minio)`（io.minio:minio 8.5.7）→ 运行期 jar 会传递给业务方，但**编译期不可见**；业务代码写 `new MinioInputStreamStore(minioClient, ...)` 需自行添加 `io.minio:minio` 编译依赖。
- **配置**：复用 `fileupload.oss.*`（`OSSProperties`）：`endpoint`、`bucket-name`、`access-key-id`、`access-key-secret`（后两者由业务方建 `MinioClient` 时使用；`MinioInputStreamStore` 本身只用 endpoint/bucketName）。
- **存储行为**：object key = `{groupName}/{storeName}.{extension}`；contentType 按扩展名映射（`FileUtils.getContentType`）；`stream(is, -1, 10485760)`（未知长度、10MB 分片）；bucket 需**预先创建**（代码不建桶）。
- **URL 形态**：`{endpoint}/{bucketName}/{groupName}/{path}` —— **endpoint 必须含 `http(s)://` 前缀且不带 bucket**，与阿里云 OSS 的 endpoint 语义不同（模块自带 yml 中 OSS 样例 `oss-cn-beijing.aliyuncs.com` 不带 scheme，直接复用到 MinIO 会拼出非法 URL）。URL 可匿名访问的前提是 bucket 读策略公开 [需要确认：取决于 MinIO 部署侧策略，代码未处理签名]。
- **异常行为**：`store/delete/getInputStream` 捕获所有 SDK 异常后 `throw new IOException()`（**无 message、无 cause**），排障需看 MinIO 服务端日志。
- **适用**：自建对象存储、私有化部署。

### 2.3 `OSSInputStreamStore` ⚠️

- **启用条件**：不会被自动注册；业务方自建 `OSS` 客户端 Bean + `InputStreamStore` Bean。
- **构造**：`new OSSInputStreamStore(OSS ossClient, OSSProperties)`。
- **依赖**：`compileOnly(libs.aliyun.oss)`（com.aliyun.oss:aliyun-sdk-oss 3.10.2）→ **业务方必须自行添加**，否则运行时 `NoClassDefFoundError: com/aliyun/oss/OSS`。
- **配置**：`fileupload.oss.endpoint`（**不带 scheme**）、`bucket-name`、`access-key-id`、`access-key-secret`。
- **存储行为**：`ossClient.putObject(bucket, "{groupName}/{storeName}.{extension}", is)`；不设置 contentType（由 SDK 按 key 推断）[需要确认：SDK 版本行为]。
- **URL 形态**：`https://{bucketName}.{endpoint}/{groupName}/{path}`（虚拟主机风格，固定 https）。
- **读取**：`getInputStream` 走 SDK `getObject`；`delete` 走 `deleteObject`（OSS 删除不存在的 key 不报错）。
- **适用**：阿里云生产环境。

### 2.4 `FastDFSInputStreamStore` ⚠️

- **启用条件**：不会被自动注册；业务方 `new FastDFSInputStreamStore("fdfs_client.properties")` 注册为 `InputStreamStore` Bean。构造函数 `throws IOException, MyException`（org.csource）。
- **依赖**：`compileOnly(libs.fastdfs)`（org.csource:fastdfs-client-java 1.29）→ **业务方必须自行添加**。
- **配置**：不走 yml。构造时从 **classpath** 加载属性文件并 `ClientGlobal.initByProperties`。模块 jar 自带 `fdfs_client.properties`：
  ```properties
  fastdfs.tracker_servers=192.168.0.117:22122
  fastdfs.http_tracker_http_port=8080
  ```
  业务方修改 tracker 地址的方式：在自己工程 classpath 根放同名 `fdfs_client.properties`（应用 classes 目录先于 jar，可遮蔽），或自备其他文件名并传给构造函数。⚠️ key `fastdfs.http_tracker_http_port` 疑似笔误（`getServerUrl` 读取的是 `ClientGlobal.getG_tracker_http_port()`，标准 client 的配置键为 `fastdfs.tracker_http_port`）——该键是否被 ClientGlobal 识别 `[需要确认]`。
- **存储行为（与其他后端的重要差异）**：
  - `storeName` **被忽略**（源码 `log.warn("FastDFS 无法指定存储文件名：{}")`），文件名由集群生成；
  - 入参 `groupName` 作为 FastDFS group 传给 `upload_file`，返回的 `StoreResponse.groupName = uploadResults[0]`（**集群实际 group，可能与入参不同**）、`path = uploadResults[1]`；
  - ⚠️ `FileStore.storeFileMeta` 回填 `FileMeta.groupName` 用的是调用方传入值而非 `StoreResponse.groupName` → 若两者不一致，后续 `DocumentService` 的下载/删除会用错 group。**使用 FastDFS 时请传入合法且唯一的 group 名，或直接用 `InputStreamStore.store` 并以 `StoreResponse` 为准**。
  - 每次 store/delete 都新建 `TrackerClient/TrackerServer/StorageClient`（无连接池）。
- **URL 形态**：`http://{tracker主机}:{G_tracker_http_port}/{实际group}/{path}`；`getInputStream/getByteArray` 继承基类 → 通过 OkHttp GET 该 URL 下载。
- **适用**：已有 FastDFS 集群的遗留环境。新项目建议 MinIO。

### 2.5 选型速查

| 问题 | 答案 |
|---|---|
| 什么都不配，用的是什么？ | `LocalInputStreamStore`（`@ConditionalOnMissingBean` 默认） |
| 怎么切后端？ | 业务方注册一个自己的 `InputStreamStore` @Bean，local 自动退让 |
| 能同时启用两个后端吗？ | 不能（`FileStore`/`ImageService` 按单 Bean 注入；多个候选且无 `@Primary` → 启动 `NoUniqueBeanDefinitionException`） |
| 切换后 `FileStore`/`DocumentService` 要改代码吗？ | 不用，它们注入的是 `InputStreamStore` 接口 |

---

## 3. 附件文档服务（Document 体系）

「附件落库（`sys_document` 表）+ 文件落存储」完整方案。**前提**：① 业务工程配置了 DataSource（sharp-database 自动配置生效）；② 手动执行建表脚本；③ 组件扫描覆盖 `com.rick.fileupload.client`（`DocumentServiceImpl @Service`、`DocumentDAO @Repository`、两个 Controller 均靠扫描注册，自动配置不注册它们）。

### 3.1 `Document` 实体 ⭐（`client/support/Document.java`）

`@Table("sys_document")`，`extends FileMeta`（继承 name/extension/contentType/size/groupName/path/url 等），`implements BaseEntityInfoGetter`。

| 字段 | 列 | 类型 | 说明 |
|---|---|---|---|
| `id` | `id` | Long | `@Id`（默认策略 SEQUENCE → `IdGenerator.getSequenceId()` 雪花 ID，insert 时自动填充并回写实体）；JSON 序列化为**字符串**（ToStringSerializer，避免前端 JS 精度丢失） |
| `baseEntityInfo` | — | BaseEntityInfo | `@Embedded` 审计信息（createBy/createTime/updateBy/updateTime/deleted） |
| `createBy` | `create_by` | Long | 创建人，`updatable=false`，JSON 只读 |
| `createTime` | `create_time` | LocalDateTime | 创建时间，`updatable=false`，JSON 只读；建表脚本 NOT NULL（`DocumentServiceImpl` 未显式赋值，是否由 sharp-database 自动填充 `[需要确认]`；测试 `DocumentServiceTest` 可跑通说明本地环境有填充机制或依赖 DB 默认值） |
| `updateBy` / `updateTime` | `update_by` / `update_time` | Long / LocalDateTime | 更新人/时间，JSON 只读 |
| `deleted` | `is_deleted` | Boolean | 逻辑删除标记列（`@JsonIgnore`）。但 `DocumentService.delete` 实际走 `deleteByIds` → `EntityDAOImpl.delete` → **物理 DELETE**（EntityDAOImpl 中未见按 is_deleted 改写） |

建表脚本：`sharp-fileupload/sql/sys_document-mysql.sql`、`sys_document-postgres.sql`。列：`id, name(255,NOT NULL), extension(16), content_type(128), size(int), group_name(255,NOT NULL), path(255,NOT NULL), create_by, create_time(NOT NULL), update_by, update_time, is_deleted`。
⚠️ `size` 列为 **int**（上限 ~2.1GB），而 `FileMeta.size` 是 Long——超大文件入库会溢出。
⚠️ MySQL 脚本开头有 `DROP TABLE IF EXISTS sys_document`。

### 3.2 `DocumentService`（接口）⭐ / `DocumentServiceImpl` 🚫

注入方式：`@Autowired DocumentService`（`DocumentServiceImpl` 为 `@Service`，禁止直接引用实现类）。

#### `Document store(FileMeta fileMeta, String groupName) throws IOException`
- 用途：单文件「存存储 + 落库」一步完成。
- 参数：`fileMeta`——**必须已填 `data` 字节**（通常来自 `FileMetaUtils.parse`）；`groupName`——存储分组。
- 返回：`Document`，含自动生成的 `id`（雪花）、`path`、`url`。
- 内部链路：`fileStore.storeFileMeta` → `BeanUtils.copyProperties(fileMeta, document)` → `documentDAO.insert(document)`。
- 示例（源自 `DocumentServiceTest`）：
```java
FileMeta fileMeta = FileMetaUtils.parse(new File("/data/demo/1.jpg"));
Document doc = documentService.store(fileMeta, "document");
long id = doc.getId();          // 关联到业务表用这个 id
String url = doc.getUrl();
```

#### `List<Document> store(List<FileMeta> fileMetaList, String groupName) throws IOException`
批量版本；落库用 `documentDAO.insertOrUpdate(documentList)`。

#### `void download(HttpServletRequest request, HttpServletResponse response, long... ids) throws IOException`
- 单 id：`Content-disposition: attachment; filename={fullName}`，流式输出文件内容。
- 多 id：在 `{fileupload.tmp}` 下建临时目录 → 逐个下载文件 → 用 `ZipUtils` 打包 → 输出 `{【批量下载】首个文件名 等N个文件}.zip` → 清理临时目录。**`fileupload.tmp` 未配置时 `Paths.get(null)` 直接 NPE**。
- `ids` 为空：抛 `RuntimeException("下载id不能为空")`。

#### `void delete(Long... ids)`
先删存储文件（`fileStore.delete`，**IOException 被 printStackTrace 吞掉**，存储删除失败不影响后续），再 `documentDAO.deleteByIds` 物理删除记录。

#### `void rename(long id, String name)`
仅更新 DB `name` 列（`updateById("name", id, ...)`），**不改存储中的物理文件名**；此后 `getFullName()`（下载文件名）随新 name 变化。

#### `Document findById(long id)`
查库并回填 `url`（`fileStore.getURL(groupName, path)`）。**id 不存在时 `Optional.get()` 抛 `NoSuchElementException` → HTTP 500**（模块未做 404 转换）。

#### `void preview(long id, ImageParam imageParam, OutputStream os) throws IOException`
图片（`FileUtils.isImageType(extension, contentType)`：扩展名匹配 `(?i)(.*)[.]?(bmp|png|jpeg|jpg|gif|ico)` 或 contentType 以 `image` 开头）→ `imageService.write`（可带缩放参数）；其他文件 → 原始字节写入 os。

#### `String getURL(long id)`
返回 `{serverUrl前缀}{groupName}/{path}`。同样在 id 不存在时抛 `NoSuchElementException`。

- **业务方标准用法**：上传拿 `Document.id` → 存到业务表外键/JSON → 前端用 `/documents/download/{id}`、`/documents/preview/{id}` 或 `getURL` → 删除业务单据时调 `documentService.delete(id)`。
- **不应该怎么使用**：不要绕过 `DocumentService` 直接 `DocumentDAO.insert`（不会先存文件）；不要把 `Document.url` 当永久地址存业务表（server-url 变更后失效，应存 `id` 或 `groupName+path`）。

### 3.3 `DocumentDAO` ⚠️（`client/support/DocumentDAO.java`）

`@Repository`，`extends EntityDAOImpl<Document, Long>`（sharp-database 通用实体 DAO，空类体）。可用其继承的 `selectById/select/insert/insertOrUpdate/deleteByIds/updateById` 等方法做自定义查询（如按业务条件查附件），但增删改请优先走 `DocumentService` 保证「存储 + DB」一致。

---

## 4. 图片处理（plugin/image）

### 4.1 `ImageService` ⭐（自动配置注册 Bean，注入即用）

构造依赖 `InputStreamStore`。底层为 Thumbnailator（模块 `implementation` 依赖，运行期传递）。

#### `void write(FileMeta fileMeta, ImageParam imageParam, OutputStream os) throws IOException`
- 用途：把图片（可带缩放/裁剪参数）写入输出流——HTTP 预览接口的核心。
- 行为：`fileMeta.data` 为 null 时先从存储读入；`imageParam` 为 null 或全空时：size ≤ 500KB 直接输出原图字节，否则进入重编码压缩路径；`imageParam` 仅 `p=0`（`isSource()`）时输出原图字节；其余走 `cropPic(fileMeta, imageParam, os)`。`imageParam.f` 非空时会**改写 `fileMeta.extension`** 为 f。
- 异常：非图片抛 `NotImageTypeException`（RuntimeException）。

#### `FileMeta cropPic(FileMeta fileMeta, int aspectRatioW, int aspectRatioH) throws IOException`
按宽高比**从中心自动裁剪**（要求 `fileMeta.data` 已有内容）。返回同一个 `fileMeta`，其 `data` 被替换为裁剪后字节（**不落存储**，需要保存请再调 `fileStore.storeFileMeta`——见 `ImageServiceTest.testCropPic`）。

#### `FileMeta cropPic(FileMeta fileMeta, int x, int y, int w, int h, int aspectRatioW, int aspectRatioH) throws IOException`
手动指定裁剪起点 (x,y)、区域 (w,h)，再按 aspectRatioW:aspectRatioH 收敛比例（`aspectRatio*` 传 0 表示不收敛）。

#### `FileMeta cropPic(FileMeta fileMeta, Position position, int w, int h, int aspectRatioW, int aspectRatioH) throws IOException`
同上，Position 可用 Thumbnailator 的 `Positions.CENTER` 等。

#### `FileMeta cropPic(FileMeta fileMeta, ImageParam imageParam) throws IOException` / `cropPic(FileMeta, ImageParam, OutputStream)`
全参数版本；前者结果写回 `fileMeta.data`，后者写入 os 并关闭 os。

#### `String createImage(String text, String groupName) throws IOException` / `createImage(String text, String groupName, String storeName) throws IOException`
- 用途：把名字画成 **100×100 圆角 PNG 文字头像**（`NameImageCreator`：中文名取后两字、英文取首字母大写、随机背景色），存入存储并**返回访问 URL**（不落 `sys_document` 库）。
- 两参版本文件名用雪花 ID；三参版本可指定 `storeName`（如用户 id，重复调用会覆盖旧头像）。
- 示例（源自 `ImageServiceTest`）：
```java
String url = imageService.createImage("张三", "header");
String url2 = imageService.createImage("张三", "header", "Rick");
```
- **不应该怎么使用**：不要用 `createImage` 处理超长文本（画布固定 100×100，字体 30–60px）；不要期待它做水印——模块主代码无水印 API（测试类 `ImageWriter` 中的 Caption 水印只是 Thumbnailator 用法演示，非模块能力）。

### 4.2 `ImageParam` ⭐（`plugin/image/ImageParam.java`）

HTTP 场景由 Spring 按 query 参数名自动绑定（`?w=500&r=30`）。全部字段（默认均为 null，无参数即 `isEmpty()`）：

| 字段 | query 名 | 类型 | 合法值/含义 |
|---|---|---|---|
| `p` | `p` | Integer | 处理模式：`0`=仅 p 时返回原图 / 与 w,h 同用时 forceSize 拉伸；`2`=按 w/2,h/2 取源区域放大 2 倍；`3`=w,h 作为百分比取源区域放大 2 倍；其他值不触发尺寸处理 |
| `w` / `h` | `w`/`h` | Integer | 目标宽/高（像素）。只给一个时等比缩放（Thumbnailator width/height） |
| `r` | `r` | Integer | 旋转角度（Thumbnailator `rotate`，顺时针） |
| `x` / `y` | `x`/`y` | Integer | 裁剪源区域起点坐标；两者齐备时自动构造 `Coordinate` Position |
| `f` | `f` | String | 输出格式扩展名（如 `jpg`、`png`）；缺省用原图格式 |
| `q` | `q` | Integer | 输出质量，**0–100**（内部 `/100f`） |
| `rw` / `rh` | `rw`/`rh` | Integer | 目标宽高比（如 `1&1` 方形、`16&9`）；w/h 缺省时取原图宽高，0 表示不收敛 |
| `position` | —（不绑定 query） | Position | 编程式设置裁剪锚点，默认 `Positions.CENTER` |

辅助：`isEmpty()`（全空）、`isSource()`（仅 p 非空）。
自动压缩规则（`ImageService.handleImage`）：p 为 null 且原图 size > `Constants.COMPRESS_THRESHOLD`(500KB) 且 w,h 齐备时，`outputQuality(500KB/size)` 按比值压质量。

### 4.3 `NameImageCreator` ⚠️

静态工具：`byte[] generateImg(String name)`（100×100 圆角头像 PNG）、`boolean isChinese(String)`、`BufferedImage makeRoundedCorner(BufferedImage, int cornerRadius)`。一般通过 `ImageService.createImage` 间接使用；字体写死「微软雅黑」，Linux 服务器无该字体时的回退行为 `[需要确认]`。

---

## 5. HTTP 接口（Controller）

**注册条件**：两个 Controller 无任何自动配置注册，业务方必须组件扫描 `com.rick.fileupload.client`（或更精确的两个子包）才会暴露；不扫描即天然"禁用"。
**认证/权限**：模块内**没有** `@PreAuthorize`、没有 Security 配置 → 所有接口默认无认证无鉴权，业务方必须自行加防护（尤其 `DELETE /documents/{id}`、`/documents/download`）。
**统一响应包装**：`Result<T>` = `{ "success": true, "code": 200, "message": "OK", "data": ... }`（`ResultUtils.success`）。异常不经 Result 包装，直接抛出（默认 Spring 500 错误页 / 业务方全局异常处理器接管）。
**前置依赖**：DocumentService 链路要求 DataSource + `sys_document` 表 + `fileupload.*` 配置。

### 5.1 `DocumentController`（`/documents`）⚠️

| # | Method | URL | 请求 | 响应 | 语义 |
|---|---|---|---|---|---|
| 1 | GET | `/documents/{id}` | path: id（Long，雪花 ID，路径变量按 Long 解析，前端传字符串数字即可） | `Result<Document>`（data.id 为字符串；含 url/fullName/size/contentType；不含 data 字节） | 查附件元信息 |
| 2 | POST | `/documents/upload` | multipart/form-data；文件字段名默认 `file`，可用请求参数 `name` 改字段名；可选参数 `groupName`（默认 `upload`） | `Result<List<Document>>` | 通用批量上传（读 `MultipartHttpServletRequest.getFiles(字段名)`）+ 落库 |
| 3 | POST | `/documents/upload2` | multipart；`file` 字段（`@RequestParam("file") List<MultipartFile>`，字段名固定）；可选 `groupName`（默认 `upload`） | `Result<List<Document>>` | 与 2 等价的显式参数版 |
| 4 | POST | `/documents/upload3` | `@RequestBody List<FileMeta>` JSON（**每项必须含 `data`（base64 字节）**及 `extension` 或 `fullName`）；可选 query/form `groupName`（默认 `upload`） | `Result<List<Document>>` | 无 multipart 的 JSON 直传（如前端已有 base64） |
| 5 | GET | `/documents/download/{id}` | path: id | 文件流，`Content-disposition: attachment; filename={fullName}` | 单文件下载 |
| 6 | GET | `/documents/download?id=1&id=2` | query: `id`（long[]，必填，可重复） | zip 流（`【批量下载】xxx 等N个文件.zip`） | 批量打包下载；**需配置 `fileupload.tmp`** |
| 7 | GET | `/documents/preview/{id:\d+}` | path: id | **302 重定向**到 `documentService.getURL(id)`（存储直链） | 快速预览；要求存储 URL 浏览器可达（local 后端需静态文件服务在线） |
| 8 | GET | `/documents/preview2/{id:\d+}` 或 `/documents/preview2/{id:\d+}/{fileName}.{extension:(?i)docx\|xlsx\|pptx}` | path: id；query 可带 `ImageParam` 参数（w/h/q/r/p…） | 文件流，`Content-disposition: inline`；图片走 ImageService 缩放，其他原样输出 | 服务端流式预览；第二种 URL 供 Office 在线预览（view.officeapps.live.com 要求 URL 以 docx/xlsx/pptx 结尾） |
| 9 | PUT | `/documents/{id}/rename?name=新名` | path: id；param: `name`（String，必填语义——未用 @RequestParam 标注，缺省时 update name=null [需要确认：DB 列 NOT NULL，可能报错]） | `Result`（无 data） | 重命名（仅 DB） |
| 10 | DELETE | `/documents/{id}` | path: id | `Result` | 删存储文件 + 物理删记录 |

典型 curl：
```bash
# 上传（字段名 file，分组 mybiz）
curl -F "file=@/path/report.pdf" -F "groupName=mybiz" http://localhost:8080/documents/upload
# → {"success":true,"code":200,"message":"OK","data":[{"id":"475029213070921728","name":"report","extension":"pdf",...,"url":"http://.../mybiz/16xxx.pdf"}]}

curl http://localhost:8080/documents/475029213070921728        # 详情
curl -OJ http://localhost:8080/documents/download/475029213070921728  # 下载
curl -X DELETE http://localhost:8080/documents/475029213070921728     # 删除
```

### 5.2 `ImageController`（`/images`）⚠️

| # | Method | URL | 请求 | 响应 | 语义 |
|---|---|---|---|---|---|
| 1 | GET | `/images/{id}` | path: id（sys_document 主键）；query: `ImageParam` 全参数（`p w h r x y f q rw rh`） | 图片流，inline；Content-Type 按 fullName 推断 | 附件图片预览 + 实时缩放/裁剪/旋转/转格式。例：`/images/475036437923139584?rw=1&rh=1&p=0&r=30&w=500`（源码注释：按 1:1 裁剪、旋转 30°、宽 500px）。无参数时 ≤500KB 输出原图 |
| 2 | POST | `/images/cropPic` | multipart `file` + 必填 int 参数 `x,y,w,h,aspectRatioW,aspectRatioH` | `Result<FileMeta>`（data 为裁剪后图片的 **base64 字节**，url/path 为空——**不落库不落存储**） | 手动裁剪预览：前端裁完拿 base64 再走业务上传 |
| 3 | POST | `/images/cropPic2` | multipart `file` + 必填 `aspectRatioW,aspectRatioH` | 同上 | 按比例中心自动裁剪 |
| 4 | POST | `/images/create?text=张三` | param `text`（String） | `Result<String>`（data = 头像 URL） | 生成文字头像并存入 group `header`（**只落存储，不落 sys_document**） |

注意：`/images/{id}` 依赖 Document 落库（先 `documentService.findById`）；非图片附件会抛 `NotImageTypeException`；id 不存在抛 `NoSuchElementException`（500）。

---

## 6. 文件校验与元信息

### 6.1 `FileMetaUtils` ⭐（`core/support/FileMetaUtils.java`）

| 方法 | 说明 |
|---|---|
| `static List<FileMeta> parse(MultipartHttpServletRequest multipartRequest, String formFileName) throws IOException` | 从请求中按表单字段名取文件列表并解析 |
| `static List<FileMeta> parse(List<MultipartFile> fileList) throws IOException` | 空/null → `Collections.emptyList()` |
| `static FileMeta parse(MultipartFile file) throws IOException` | 填 fullName(原始名拆分)/contentType/size/**data（全量读入内存）** |
| `static FileMeta parse(File file) throws IOException` | 同上，contentType 用 `file.toURI().toURL().openConnection().getContentType()` 猜测 |

### 6.2 校验规则（重要，如实描述）

- **模块本身没有类型白名单、没有大小限制、没有文件名安全校验**：模块内不存在任何校验工具类（原有的 `FileCheckUtils` 是零方法的空占位类，**已被删除**，不要再引用）。
- 唯一的类型校验发生在图片链路：`FileUtils.isImageType(extension, contentType)`（正则 `(?i)(.*)[.]?(bmp|png|jpeg|jpg|gif|ico)` 或 contentType 前缀 `image`），不满足抛 `NotImageTypeException`。
- **大小限制来自 Spring**：`spring.servlet.multipart.max-file-size / max-request-size` 必须由业务工程自己配置（模块 jar 内 application.yml 的 50MB 只在模块独立运行/测试时生效）。超限抛 Spring 的 `MaxUploadSizeExceededException`。
- **业务方需要自行校验**：文件类型白名单、文件名（原始名仅存 DB，存储名是雪花 ID，路径穿越风险低，但 `groupName` 由调用方传入并直接拼路径——**不要把用户输入直接当 groupName**，`../` 会逃逸存储根目录）。

### 6.3 `FileConvertUtils` ⚠️（PDF → 图片）

**PDF 能力确认存在**，就在此类（这是 pdfbox/itext 两个 compileOnly 依赖的唯一消费方）：

| 方法 | 说明 |
|---|---|
| `static List<byte[]> pdf2Image(byte[] data, int dpi) throws IOException` | PDF 每页渲染为 PNG（dpi 越大越清晰越慢），返回每页字节数组。页数用 iText `PdfReader` 读取 |
| `static void pdf2Image(byte[] data, OutputStream os, int dpi)` | 所有页渲染后**纵向拼接为一张 PNG** 写入 os；内部 IOException 被 printStackTrace 吞掉 |

**使用前提**：业务方必须自行添加 `org.apache.pdfbox:pdfbox` 与 `com.lowagie:itext`，否则首次调用抛 `NoClassDefFoundError`（类加载时才触发，启动不报错）。

---

## 7. 配置项（完整清单）

三个 `@ConfigurationProperties` 类由 `FileUploadAutoConfig` 的 `@EnableConfigurationProperties` 无条件注册绑定。

| yml key（规范写法） | 绑定字段 | 类型 | 默认值 | 必填 | 用途 | 何时修改 / 副作用 |
|---|---|---|---|---|---|---|
| `fileupload.tmp` | `FileUploadProperties.tmp` | String | 无 | 用批量下载(`/documents/download?id=多值`)时**必填** | `DocumentServiceImpl.download` 打 zip 的临时根目录 | 不配 → 批量下载 NPE；目录需存在且可写 |
| `fileupload.local.server-url` | `LocalProperties.serverUrl` | String | 无 | local 后端必配 | `getURL` 前缀，拼成 `{server-url}{group}/{path}` | **需以 `/` 结尾**（拼接无分隔符处理）；指向能静态服务 root-path 的 HTTP 服务，否则返回的 URL 404 |
| `fileupload.local.root-path` | `LocalProperties.rootPath` | String | 无 | local 后端必配 | 磁盘存储根目录 | 不配 → 文件写进工作目录 `null/{group}/`；目录无写权限 → `FileNotFoundException/IOException` |
| `fileupload.oss.endpoint` | `OSSProperties.endpoint` | String | 无 | minio/oss 后端必填 | MinIO：完整地址**含 scheme**；阿里云：**不含 scheme** | 两种语义不同，配错 → URL 非法（见 2.2/2.3） |
| `fileupload.oss.access-key-id`（驼峰 `accessKeyId` 亦可，宽松绑定） | `OSSProperties.accessKeyId` | String | 无 | minio/oss 必填 | AK（业务方建客户端 Bean 时读取） | — |
| `fileupload.oss.access-key-secret` | `OSSProperties.accessKeySecret` | String | 无 | minio/oss 必填 | SK | 泄露风险，建议环境变量注入 |
| `fileupload.oss.bucket-name` | `OSSProperties.bucketName` | String | 无 | minio/oss 必填 | 桶名 | 桶必须预先存在，代码不建桶 |

FastDFS 不走 yml（见 2.4 的 properties 文件说明）。
四种后端完整 yml 样例见 `docs/api/configuration.md`。

### 模块自带资源文件的影响（必读）

| 文件 | 内容 | 意图 | 对业务工程的影响 |
|---|---|---|---|
| `src/main/resources/application.yml` | `spring.datasource`（开发者本机 MySQL，**含明文口令**）、`spring.servlet.multipart` 50MB、`fileupload.tmp/local/oss` 本机样例 | 让模块能通过 `FileUploadApplication` 独立启动、让 `@SpringBootTest` 测试无需 test resources 即可运行 | jar 内 classpath 根的 `application.yml`。业务工程有自己的 classpath `application.yml` 时按类路径顺序通常被遮蔽；**业务工程若只有 `application.properties`，jar 内 yml 可能被加载**，带入他人 datasource/multipart/fileupload 配置 → 启动失败或行为异常。接入后应打印生效配置确认 |
| `src/main/resources/fdfs_client.properties` | `fastdfs.tracker_servers=192.168.0.117:22122` 等 | FastDFS 客户端默认配置 | 始终在 classpath 上；仅当业务构造 `FastDFSInputStreamStore` 时被读取。业务工程在 classpath 根放同名文件可遮蔽修改 tracker |
| `META-INF/spring/...AutoConfiguration.imports` | 注册 `FileUploadAutoConfig` | Boot 3 自动配置入口 | 引入依赖即生效（CorsFilter + 默认 local 后端 + FileStore + ImageService） |
| `META-INF/spring.factories` | 旧式注册同一个 AutoConfig | 兼容 Boot 2.x 的遗留 | **Boot 3 忽略 spring.factories 的 EnableAutoConfiguration 键**，实际不生效，无害但冗余 |
| `com/rick/fileupload/FileUploadApplication.java` | `@SpringBootApplication` + main | 模块独立运行/调试入口 | 🚫 业务组件扫描若覆盖 `com.rick.fileupload` 根包会把它当嵌套配置类处理（其 @ComponentScan/@EnableAutoConfiguration 被再次应用），可能引发重复扫描/冲突。只扫 `com.rick.fileupload.client` 可规避 |

---

## 8. 异常

| 异常 | 类型 | 触发点 | 业务处理建议 |
|---|---|---|---|
| `IOException`（含子类） | 受检 | 所有 store/delete/getInputStream 方法签名；MinIO 后端把一切 SDK 异常包成**无 message 的 IOException**；local delete 文件缺失 → `FileNotFoundException` | catch 后转业务异常；MinIO 排障看服务端日志 |
| `NotImageTypeException` | RuntimeException（`core/exception`，固定 message "文件不是图片类型"） | `ImageService.cropPic/write` 处理非图片 | 全局异常处理器转 4xx 提示 |
| `NoSuchElementException` | RuntimeException | `DocumentService.findById/getURL` 查不到 id（`Optional.get()`） | 业务方自行捕获转 404 |
| `RuntimeException("下载id不能为空")` | RuntimeException | `download()` 传空 ids | 参数校验 |
| `NullPointerException` | — | 批量下载未配 `fileupload.tmp`；`ImageParam` 相关空指针链路 | 配置检查 |
| `MaxUploadSizeExceededException`（Spring） | — | 超过业务方配置的 multipart 上限 | 全局异常处理器转 413/提示 |
| `NoClassDefFoundError` | Error | 使用 OSS/FastDFS/PDF 能力但未补 compileOnly 依赖 | 按 CLAUDE.md 引入清单补依赖 |
| `MyException`（org.csource） | 受检 | 仅 `FastDFSInputStreamStore` **构造函数**声明；运行期方法内已转 IOException | 注册 Bean 时处理 |

---

# Common Mistakes

### 1. 以为引入依赖就有 HTTP 上传接口
```java
// ❌ 只加依赖，调用 /documents/upload → 404
// 自动配置只注册存储 Bean 和 CorsFilter，Controller 不在其中（FileUploadAutoConfig 无 @ComponentScan）
```
```java
// ✅ 业务启动类显式扫描 client 包（不要扫 com.rick.fileupload 根包，会带入 FileUploadApplication）
@SpringBootApplication(scanBasePackages = {"com.mybiz", "com.rick.fileupload.client"})
```
原因：`DocumentController/ImageController/DocumentServiceImpl(@Service)/DocumentDAO(@Repository)` 全靠组件扫描注册。

### 2. 注入具体实现类或自己 new
```java
// ❌
@Autowired LocalInputStreamStore store;              // 容器里 Bean 类型是接口，且换后端即崩
FileStore fs = new FileStore(new LocalInputStreamStore(props));
```
```java
// ✅
@Autowired FileStore fileStore;          // 或 InputStreamStore
```
原因：`FileUploadAutoConfig` 注册的是 `InputStreamStore`/`FileStore(@Primary)` 类型 Bean；面向接口才能享受后端切换。

### 3. MinIO / 阿里云 OSS 的 endpoint 混用
```yaml
# ❌ MinIO 用不带 scheme 的 endpoint → getURL 拼出 "minio-host:9000/bucket/..."（非法 URL，OkHttp 直接抛错）
fileupload.oss.endpoint: minio-host:9000
```
```yaml
# ✅ MinIO：完整地址（MinioInputStreamStore.getServerUrl = endpoint + "/" + bucket + "/"）
fileupload.oss.endpoint: http://minio-host:9000
# ✅ 阿里云：不带 scheme（OSSInputStreamStore.getServerUrl = "https://" + bucket + "." + endpoint + "/"）
fileupload.oss.endpoint: oss-cn-beijing.aliyuncs.com
```

### 4. 只存文件不落库，之后无法下载/删除
```java
// ❌ 只用 FileStore 上传，把 url 存业务表 —— server-url 一变全失效，且没有 id 可用 /documents/download
fileStore.upload(files, "biz");
```
```java
// ✅ 用 DocumentService，业务表存 document.id
Document doc = documentService.store(FileMetaUtils.parse(multipartFile), "biz");
bizEntity.setAttachmentId(doc.getId());
```

### 5. 忘记建表 / 忘记配 multipart / 忘记配 fileupload.tmp
```text
❌ 引入即上传 → INSERT INTO sys_document 报 "table doesn't exist"
❌ 上传 20MB 文件 → MaxUploadSizeExceededException（Spring 默认 1MB！模块 jar 里的 50MB 配置在业务工程不生效）
❌ /documents/download?id=1&id=2 → NPE（DocumentServiceImpl 用 Paths.get(fileUploadProperties.getTmp()) 建临时目录）
```
```yaml
# ✅ 业务工程 application.yml
spring.servlet.multipart.max-file-size: 50MB
spring.servlet.multipart.max-request-size: 50MB
fileupload.tmp: /data/fileupload/tmp
# ✅ 手动执行 sql/sys_document-mysql.sql（或 postgres 版）；注意 MySQL 脚本会 DROP TABLE
```

### 6. 用 OSS/FastDFS/PDF 能力但没补 compileOnly 依赖
```java
// ❌ 启动正常，一调用就 java.lang.NoClassDefFoundError: com/aliyun/oss/OSS（或 org/csource/... / org/apache/pdfbox/...）
```
```gradle
// ✅ 按选型补齐
implementation 'com.aliyun.oss:aliyun-sdk-oss:3.10.2'
implementation 'org.csource:fastdfs-client-java:1.29'
implementation 'org.apache.pdfbox:pdfbox:2.0.12'
implementation 'com.lowagie:itext:2.0.7'
```
原因：build.gradle 中这些是 `compileOnly`，不传递。

### 7. 把用户输入直接当 groupName
```java
// ❌ groupName 直接拼进磁盘路径/object key：input "../../etc" → 逃逸存储根目录
fileStore.upload(files, request.getParameter("group"));
```
```java
// ✅ 白名单或固定业务分组
fileStore.upload(files, "biz-order");
```
原因：`LocalInputStreamStore.getGroupNamePath = rootPath + "/" + groupName`，模块对 groupName 不做任何校验或消毒（无校验工具类）。

### 8. 大文件走 MultipartFile/FileMeta 链路
```java
// ❌ 上传 2GB 视频 → FileMetaUtils.parse 把整个文件 IOUtils.toByteArray 读进堆内存 → OOM
//    且 sys_document.size 列是 int，>2.1GB 溢出
```
```java
// ✅ 大文件绕过 FileMeta.data，直接流式：
StoreResponse r = inputStreamStore.store("video", "mp4", multipartFile.getInputStream());
// （不落 Document，或落库时自行组装不含 data 的元信息 [需要确认：DocumentService 无流式重载]）
```

### 9. FastDFS 后端期待指定文件名 / group 不一致
```java
// ❌ store(group, "myfile", "pdf", is) —— 文件名被忽略（源码 log.warn），
//    且 FileStore.storeFileMeta 回填的 groupName 是入参而非集群返回值，两者不一致时后续下载/删除用错 group
```
```java
// ✅ FastDFS 场景直接以 StoreResponse 为准记录 groupName/path
StoreResponse r = fastdfsStore.store("group1", "pdf", is);
```

### 10. 以为模块自带的安全/跨域是安全的
```text
❌ 生产环境直接暴露 —— 模块注册了全放行 CorsFilter（/** 允许所有 origin），
   且所有 Controller 无认证；DELETE /documents/{id} 任何人可删任意附件
```
```yaml
# ✅ 需要收紧 CORS 时：排除整个自动配置，再手动注册自己需要的 Bean
# （corsFilter 无 @ConditionalOnMissingBean，定义同名 Bean 会因默认禁止覆盖而启动冲突；
#   排除后 FileStore/InputStreamStore/ImageService 也不再自动注册，需自行声明）
spring.autoconfigure.exclude: com.rick.fileupload.core.config.FileUploadAutoConfig
```
并给 `/documents/**`、`/images/**` 加认证鉴权。原因：`FileUploadAutoConfig#corsFilter()` 无条件注册；Controller 无 `@PreAuthorize`。
