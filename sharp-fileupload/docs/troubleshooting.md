# 故障排查（异常 → 触发条件 → 业务含义 → 处理）

> 全部条目均有源码依据；标注文件见各条「依据」。

## A. 启动失败

| 症状 | 触发条件 | 依据 | 处理 |
|---|---|---|---|
| `NoUniqueBeanDefinitionException: InputStreamStore` | 业务方注册了多个 `InputStreamStore` Bean 且无 `@Primary` | `FileUploadAutoConfig#fileStore(InputStreamStore)` 单 Bean 注入 | 只保留一个，或给目标 Bean 加 `@Primary` |
| Bean 定义同名 `corsFilter` 冲突（`BeanDefinitionOverrideException`） | 业务方定义了名为 `corsFilter` 的 Bean | 自动配置的 `corsFilter()` 无 `@ConditionalOnMissingBean`，Boot 默认禁止覆盖 | 换 Bean 名，或排除整个自动配置并自行补注册存储 Bean（configuration.md §6） |
| `found multiple @SpringBootConfiguration` / 组件扫描异常 | 业务组件扫描覆盖了 `com.rick.fileupload` 根包 | 根包内有库自带的 `FileUploadApplication`（@SpringBootApplication） | 只扫 `com.rick.fileupload.client`，或扫描时 exclude `FileUploadApplication` |
| DataSource 相关启动失败、且配置看起来"不是自己的" | 业务工程只有 `application.properties`，jar 内 `application.yml` 被加载 | 模块 jar 自带 `application.yml`（含作者本机 MySQL datasource） | 业务工程提供自己的 classpath `application.yml` 遮蔽；启动时核对生效配置来源 |
| Document 相关 Bean（DocumentService/Controller）找不到 | 未组件扫描 `com.rick.fileupload.client` | 这些类靠 `@Service/@Repository/@RestController` 扫描注册，自动配置不注册 | 加 scanBasePackages |

## B. 上传失败

| 症状/异常 | 触发条件 | 依据 | 处理 |
|---|---|---|---|
| `MaxUploadSizeExceededException`（前端见 500/413） | 文件超 `spring.servlet.multipart.*`（Spring 默认 1MB）；**模块 jar 里的 50MB 配置对业务工程不保证生效** | application.yml 位于 jar 内 | 业务工程自配 multipart 上限 |
| `FileNotFoundException` / IOException，文件出现在工作目录 `null/{group}/` | local 后端未配 `fileupload.local.root-path` | `LocalInputStreamStore#getGroupNamePath`：`null + "/" + group` 字符串拼接 | 配置 root-path 且保证目录可写 |
| **无 message 的 `IOException`**（MinIO） | MinIO SDK 任何异常（连不上、桶不存在、凭证错） | `MinioInputStreamStore` 各方法 `catch(Exception) → throw new IOException()` | 看 MinIO 服务端/网络日志；先验证桶存在与凭证 |
| `NoSuchBucket` 等 OSS 异常直接抛出 | 阿里云 OSS 桶不存在/权限不足 | `OSSInputStreamStore` 未包装异常 | 建桶、检查 AK 权限 |
| `NoClassDefFoundError`（com/aliyun/oss、org/csource、org/apache/pdfbox、com/lowagie） | 使用 OSS/FastDFS/PDF 能力但未补 compileOnly 依赖；**启动正常，首次调用才炸**（类懒加载） | build.gradle `compileOnly` | 按 CLAUDE.md 引入清单补 `implementation` 依赖 |
| `OutOfMemoryError` / 上传接口卡顿 | 大文件走 `FileMetaUtils.parse`（整体读入 byte[]），或并发大文件 | `FileMetaUtils.parse` `IOUtils.toByteArray` | 大文件改用 `inputStreamStore.store(group, ext, multipartFile.getInputStream())` 流式直存 |
| FastDFS：文件名不是指定的 storeName；下载时 group 不对 | FastDFS 集群生成文件名；`FileStore.storeFileMeta` 回填入参 groupName 而非集群返回值 | `FastDFSInputStreamStore#store`（log.warn + uploadResults）、`FileStore#storeFileMeta` | FastDFS 场景直接用 `InputStreamStore.store` 并以 `StoreResponse` 的 groupName/path 持久化 |
| FastDFS 构造 Bean 时 `IOException/MyException` | `fdfs_client.properties` 不在 classpath / tracker 配置不可达 | 构造函数 `ClassPathResource(propertyFilePath)` + `ClientGlobal.initByProperties` | 检查文件名与 tracker 地址；业务自备同名文件遮蔽 jar 内默认 |

## C. 下载 / 预览失败

| 症状 | 触发条件 | 依据 | 处理 |
|---|---|---|---|
| HTTP 500，栈顶 `NoSuchElementException` | `/documents/{id}`、download、preview、`/images/{id}` 的 id 不存在 | `DocumentServiceImpl#findById/getURL` `Optional.get()` | 前端校验 id；全局异常处理器把 NoSuchElementException 转 404 |
| 批量下载 NPE | 未配 `fileupload.tmp` | `download()`：`Files.createTempDirectory(Paths.get(fileUploadProperties.getTmp()), null)` | 配置 tmp 且目录存在可写 |
| `RuntimeException("下载id不能为空")` | `/documents/download` 未带 id 参数 | `download()` 入口校验 | 传至少一个 id |
| preview(302) 后 404 | 存储 URL 浏览器不可达：local 静态服务未启动 / server-url 配错 / MinIO endpoint 缺 scheme / OSS 桶非公共读 | `getURL` 纯字符串拼接（各实现 getServerUrl） | curl 直接请求返回的 url 定位；核对 endpoint 格式（MinIO 带 scheme、OSS 不带） |
| `/images/{id}` 500，message "文件不是图片类型" | 对非图片附件调用图片接口 | `ImageService#cropPic` → `NotImageTypeException`；判定 = 扩展名 `(?i)(.*)[.]?(bmp|png|jpeg|jpg|gif|ico)` 或 contentType 前缀 image | 非图片走 `/documents/preview2/{id}`；注意 webp/svg 不在正则内会被判非图片 |
| Office 在线预览失败 | preview2 第一种 URL 不以 docx/xlsx/pptx 结尾 | Controller 第二路径 `/preview2/{id}/{fileName}.{extension:(?i)docx|xlsx|pptx}` 专为此设计 | 用第二种 URL 且服务需公网可达 |
| local 后端 `delete` 抛 `FileNotFoundException` | 删除已不存在的物理文件 | `LocalInputStreamStore#delete` → `FileUtils.forceDelete` | 业务侧容忍或先判存在 |

## D. 数据 / 一致性问题

| 症状 | 原因 | 依据 | 处理 |
|---|---|---|---|
| insert 报 `sys_document` 不存在 | 未执行建表脚本（脚本在模块 `sql/` 目录，不在 classpath，不会自动执行） | sql/sys_document-{mysql,postgres}.sql | 手动执行；MySQL 脚本含 DROP TABLE，存量库只取 CREATE 段 |
| insert 报 create_time NOT NULL 违反 | `DocumentServiceImpl` 不显式填审计字段；填充依赖 sharp-database 机制 `[需要确认：具体填充条件]` | DocumentServiceImpl#store；建表脚本 create_time NOT NULL | 确认 sharp-database 审计填充配置；必要时业务侧 store 前自行 set |
| >2GB 文件 size 变负数/截断 | `sys_document.size` 列为 int，`FileMeta.size` 为 Long | sql 脚本列定义 | 超大文件不入 Document 体系 |
| 删了 DB 记录但存储里还有文件 | `delete()` 中存储删除 IOException 被 `printStackTrace` 吞掉后仍删 DB | DocumentServiceImpl#delete | 定期比对存储与 DB 清理孤儿文件 |
| 同名文件被覆盖 | local/MinIO/OSS 以 `{storeName}.{ext}` 为键，无存在性检查 | 各 store 实现 | 需要保留历史时自定义 storeName（雪花默认不冲突）；`createImage(text, group, storeName)` 固定名即利用覆盖语义 |
| formflow 工程行为与本文档不符 | formflow 以 `3.0-SNAPSHOT` 坐标依赖，本模块实际版本 `0.0.1-SNAPSHOT` → 解析到 mavenLocal 旧产物 | formflow/build.gradle vs libs.versions.toml | 统一版本或改 project 依赖 |

## E. 排查顺序建议（上传接口 500 时）

1. 看响应/日志异常类型：NoSuchElementException（id 问题）→ C 表；无 message IOException → MinIO 后端 → B 表；
2. 确认后端选型：容器里 `InputStreamStore` 实际是谁（日志/actuator beans）；
3. local：核对 `root-path` 存在可写、`server-url` 以 `/` 结尾；
4. 对象存储：核对 endpoint 格式（MinIO 带 scheme / OSS 不带）、bucket 已建、AK/SK 有效；
5. Document 链路：`sys_document` 表存在、DataSource 指向正确库；
6. 大文件/并发问题：确认是否 OOM（parse 全量读内存），改流式 store。
