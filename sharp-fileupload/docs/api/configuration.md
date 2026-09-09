# 配置参考（四种后端完整 yml 样例）

> 逐 key 说明（类型/默认值/必填性/副作用）见 `../../API.md` 第 7 章；此处提供可复制样例。
> 所有样例中的公共前提：业务工程需配置 `spring.datasource`（Document 附件功能依赖 sharp-database），并按需配置 `spring.servlet.multipart.*`（Spring 默认单文件仅 1MB）。

## 0. 公共最小配置（任何后端都建议带上）

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 50MB       # 必须由业务工程配置，模块 jar 内的同名配置不保证生效
      max-request-size: 50MB

fileupload:
  tmp: /data/fileupload/tmp     # 批量下载(/documents/download?id=1&id=2)打 zip 的临时目录；不用批量下载可不配
```

## 1. 本地磁盘（默认后端，零代码）

不注册任何自定义 `InputStreamStore` Bean 时自动生效（`FileUploadAutoConfig` 的 `@ConditionalOnMissingBean`）。

```yaml
fileupload:
  tmp: /data/fileupload/tmp
  local:
    root-path: /data/fileupload           # 磁盘根目录；文件写入 {root-path}/{groupName}/{雪花ID}.{ext}
                                          # ⚠️ 不配置时会写入工作目录下的 "null/{groupName}/"
    server-url: http://file-host:7892/    # getURL 前缀，必须【以 / 结尾】
                                          # 需自行部署静态文件服务映射到 root-path
                                          # （模块源码注释示例：cd root-path && http-server -p 7892）
```

- 访问 URL 形态：`http://file-host:7892/{groupName}/{雪花ID}.{ext}`
- 无额外 Gradle 依赖。
- 适用：单机/开发环境；多实例需共享磁盘。

## 2. MinIO

需要业务方注册 Bean（见 `../examples/upload-minio.md`），并添加编译期依赖 `io.minio:minio:8.5.7`（模块内为 implementation，运行期已含）。

```yaml
fileupload:
  tmp: /data/fileupload/tmp
  oss:                                    # ⚠️ MinIO 复用 "fileupload.oss" 前缀（OSSProperties）
    endpoint: http://minio-host:9000      # ⚠️ 必须含 http(s):// 前缀，且不带 bucket
                                          #    URL 拼接 = {endpoint}/{bucketName}/{group}/{file}
    bucket-name: sharp-fileupload         # 桶必须预先创建（代码不建桶）
    access-key-id: minioadmin             # 建 MinioClient Bean 时使用
    access-key-secret: minioadmin
```

- 访问 URL 形态：`http://minio-host:9000/sharp-fileupload/{groupName}/{雪花ID}.{ext}`（匿名可访问取决于桶读策略，模块不做签名 URL）。
- 已知实现行为：所有 SDK 异常被包装为**无 message 的 IOException**，排障看 MinIO 服务端日志。

## 3. 阿里云 OSS

需要业务方注册 `OSS` 客户端 Bean + `OSSInputStreamStore` Bean，并添加依赖 `com.aliyun.oss:aliyun-sdk-oss:3.10.2`（模块内 compileOnly，**必须自补**）。

```yaml
fileupload:
  tmp: /data/fileupload/tmp
  oss:
    endpoint: oss-cn-beijing.aliyuncs.com # ⚠️ 不带 scheme（与 MinIO 相反！）
                                          #    URL 拼接 = https://{bucketName}.{endpoint}/{group}/{file}
    bucket-name: sharp-fileupload
    access-key-id: <your-ak>
    access-key-secret: <your-sk>
```

- 访问 URL 形态：`https://sharp-fileupload.oss-cn-beijing.aliyuncs.com/{groupName}/{雪花ID}.{ext}`（需桶公共读或业务方另做签名）。
- 键名宽松绑定：`accessKeyId` 驼峰写法等价于 `access-key-id`（模块自带样例 yml 用驼峰）。

## 4. FastDFS

**不走 application.yml**。需要依赖 `org.csource:fastdfs-client-java:1.29`（compileOnly，必须自补），注册 `FastDFSInputStreamStore` Bean（构造参数 = classpath 上的属性文件名）。

模块 jar 自带 `fdfs_client.properties`（classpath 根）：

```properties
fastdfs.tracker_servers=192.168.0.117:22122
fastdfs.http_tracker_http_port=8080
```

业务方修改 tracker 地址：在**自己工程的 classpath 根**放同名 `fdfs_client.properties`（应用 classes 目录先于 jar，形成遮蔽），或自备其他文件名传给构造函数。
⚠️ 键 `fastdfs.http_tracker_http_port` 疑似笔误——`getServerUrl()` 读取 `ClientGlobal.getG_tracker_http_port()`，fastdfs-client-java 的标准键为 `fastdfs.tracker_http_port`；该键是否被识别 `[需要确认]`，建议业务自备文件时使用标准键并验证。

- 访问 URL 形态：`http://{tracker主机}:{tracker_http_port}/{集群返回group}/{集群生成文件名}`
- 行为差异：无法指定存储文件名（storeName 被忽略）；`StoreResponse.groupName` 为集群返回值。

## 5. 模块自带 application.yml 的警示（重要）

`sharp-fileupload.jar` 内含有 `application.yml`（spring.datasource 指向作者本机 MySQL、multipart 50MB、fileupload.local/oss 本机样例，含明文口令）与 `fdfs_client.properties`。其存在目的是支撑模块独立启动（`FileUploadApplication`）与 `src/test` 下的 `@SpringBootTest`（模块无 test resources）。

对业务工程的影响：
1. 业务工程有自己的 classpath `application.yml` 时，按类路径顺序（应用 classes 先于依赖 jar）通常遮蔽 jar 内文件；
2. **业务工程若只使用 `application.properties`，jar 内 `application.yml` 可能被 Boot 加载**，带入他人的 datasource / multipart / fileupload 配置 → 数据源连错库、上传限制被改等；
3. 接入后建议启动时打印 `spring.config.location` 生效来源，或用 Actuator `/actuator/env`（如启用）确认 `spring.datasource.url` 等关键配置的来源不是本 jar。

## 6. 关闭/调整自动配置行为

| 需求 | 做法 |
|---|---|
| 收紧模块注册的全放行 CorsFilter | `spring.autoconfigure.exclude: com.rick.fileupload.core.config.FileUploadAutoConfig`，并自行注册 `InputStreamStore`、`FileStore`、`ImageService` 三个 Bean（corsFilter Bean 无条件注册且不可同名覆盖） |
| 切换存储后端 | 注册自己的 `InputStreamStore` Bean（local 默认自动退让），无需排除自动配置 |
| 不暴露 HTTP 接口 | 不组件扫描 `com.rick.fileupload.client` 即可（Controller 本就靠扫描注册） |
| 修改图片压缩阈值 | `Constants.COMPRESS_THRESHOLD`（public static，全局生效，不建议改） |
