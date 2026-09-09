# 示例：切换对象存储后端（MinIO / 阿里云 OSS / FastDFS）

> 通用机制：业务方注册**一个自己的 `InputStreamStore` Bean**，自动配置的默认 local 后端因 `@ConditionalOnMissingBean` 退让；`FileStore`/`ImageService`/`DocumentService` 零改动。注册多个且无 `@Primary` 会启动失败。
> 配置 key 详见 `../api/configuration.md`。

## 1. MinIO（`MinioInputStreamStore`）

**依赖**（模块内 minio 是 implementation：运行期 jar 已传递，但编译期引用 `MinioClient` 需自己加）：

```gradle
implementation 'io.minio:minio:8.5.7'
```

**yml**：

```yaml
fileupload:
  oss:                                  # MinIO 复用 OSSProperties（前缀 fileupload.oss）
    endpoint: http://minio-host:9000    # ⚠️ 必须含 scheme；URL = {endpoint}/{bucket}/{group}/{file}
    bucket-name: sharp-fileupload       # 桶需预先创建
    access-key-id: minioadmin
    access-key-secret: minioadmin
```

**Bean 注册**：

```java
@Configuration
public class StorageConfig {

    @Bean
    public MinioClient minioClient(OSSProperties props) {   // OSSProperties 已由自动配置绑定注册
        return MinioClient.builder()
                .endpoint(props.getEndpoint())
                .credentials(props.getAccessKeyId(), props.getAccessKeySecret())
                .build();
    }

    @Bean
    public InputStreamStore inputStreamStore(MinioClient minioClient, OSSProperties props) {
        return new MinioInputStreamStore(minioClient, props);
    }
}
```

**行为备注**（源码事实）：object key = `{groupName}/{storeName}.{extension}`；contentType 按扩展名映射；上传用 `stream(is, -1, 10485760)`（未知长度、10MB 分片）；SDK 异常一律包装为**无 message 的 IOException**（排障看 MinIO 服务端日志）。

## 2. 阿里云 OSS（`OSSInputStreamStore`）

**依赖**（compileOnly，必须自补，否则运行期 `NoClassDefFoundError: com/aliyun/oss/OSS`）：

```gradle
implementation 'com.aliyun.oss:aliyun-sdk-oss:3.10.2'
```

**yml**：

```yaml
fileupload:
  oss:
    endpoint: oss-cn-beijing.aliyuncs.com   # ⚠️ 不带 scheme；URL = https://{bucket}.{endpoint}/{group}/{file}
    bucket-name: sharp-fileupload
    access-key-id: <ak>
    access-key-secret: <sk>
```

**Bean 注册**：

```java
@Bean(destroyMethod = "shutdown")
public OSS ossClient(OSSProperties props) {
    return new OSSClientBuilder().build(props.getEndpoint(),
            props.getAccessKeyId(), props.getAccessKeySecret());
}

@Bean
public InputStreamStore inputStreamStore(OSS ossClient, OSSProperties props) {
    return new OSSInputStreamStore(ossClient, props);
}
```

**行为备注**：`getInputStream` 走 SDK getObject；delete 走 deleteObject；URL 固定 https 虚拟主机风格，匿名可读需桶公共读（模块不生成签名 URL）。

## 3. FastDFS（`FastDFSInputStreamStore`）

**依赖**（compileOnly，必须自补）：

```gradle
implementation 'org.csource:fastdfs-client-java:1.29'
```

**配置不走 yml**：构造参数是 classpath 上的属性文件名。模块 jar 自带 `fdfs_client.properties`（tracker `192.168.0.117:22122`，开发环境值）；业务工程在自己 classpath 根放**同名文件**遮蔽以指向自己的 tracker（应用 classes 先于 jar 加载），或自备文件名传入：

```properties
# src/main/resources/fdfs_client.properties（业务工程自己的）
fastdfs.tracker_servers=your-tracker:22122
fastdfs.tracker_http_port=8080
```

> ⚠️ 模块自带文件写的键是 `fastdfs.http_tracker_http_port`，而 `getServerUrl()` 读的是 `ClientGlobal.getG_tracker_http_port()`（标准键为 `fastdfs.tracker_http_port`）；自带键名疑似笔误 `[需要确认]`。自备文件建议用标准键并实测预览 URL。

**Bean 注册**：

```java
@Bean
public InputStreamStore inputStreamStore() throws IOException, MyException {
    return new FastDFSInputStreamStore("fdfs_client.properties");
}
```

**行为备注**（与其他后端差异，务必知晓）：
- `store` 的 `storeName` 被忽略（源码 `log.warn("FastDFS 无法指定存储文件名")`），文件名由集群生成；
- `StoreResponse.groupName/path` 取集群返回值 `uploadResults[0]/[1]`，**可能与入参 groupName 不同**；而 `FileStore.storeFileMeta` 回填 `FileMeta.groupName` 用的是入参 → 不一致时后续 `getInputStream/delete` 会用错 group。FastDFS 场景建议直接使用 `InputStreamStore.store(...)` 并以 `StoreResponse` 为准持久化 groupName+path；
- 读取走基类默认实现：OkHttp GET `http://{tracker主机}:{tracker_http_port}/{group}/{path}`（要求 storage 节点 HTTP 可达）；
- 每次操作新建 TrackerClient/StorageClient，无连接池。

## 4. 自定义新后端（S3/COS 等）

继承扩展点 `AbstractInputStreamStore`，实现 `store(4参)`、`delete`、`getServerUrl()`（以 `/` 结尾）；对象存储建议覆写 `getInputStream` 走 SDK（基类默认 OkHttp GET url，要求 url 匿名可读）。注册为唯一 `InputStreamStore` Bean 即完成切换。详见 `../../ARCHITECTURE.md` §7。
