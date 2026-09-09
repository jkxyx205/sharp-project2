# 示例：本地磁盘存储最小接入

> 蓝本：`src/test/java/com/rick/fileupload/FileStoreTest.java`、`InputStreamStoreTest.java` 与模块自带 `src/main/resources/application.yml`（示例值已替换为通用路径）。

## 1. 加依赖

```gradle
implementation project(":sharp-fileupload")   // 或对齐版本的 Maven 坐标，见 CLAUDE.md 版本警示
implementation 'org.springframework.boot:spring-boot-starter-web'
```

无需注册任何 Bean：自动配置默认注册 `LocalInputStreamStore`（`@ConditionalOnMissingBean`）+ `FileStore(@Primary)` + `ImageService`。

## 2. 配置 application.yml

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB

fileupload:
  local:
    root-path: /data/fileupload          # 不存在会自动 mkdirs（到 group 一级）
    server-url: http://localhost:7892/   # 以 / 结尾；指向映射 root-path 的静态服务
```

静态文件服务（模块源码注释中的做法）：

```bash
cd /data/fileupload && npx http-server -p 7892
```

## 3. 编程式上传（注入 FileStore）

```java
@Service
@RequiredArgsConstructor
public class DemoService {

    private final FileStore fileStore;          // ⭐ 注入门面，勿注入 LocalInputStreamStore

    public String saveAvatar(MultipartFile multipartFile) throws IOException {
        // 方式一：MultipartFile 直传
        FileMeta stored = fileStore.upload(List.of(multipartFile), "avatar").get(0);
        return stored.getUrl();                 // http://localhost:7892/avatar/{雪花ID}.{ext}
    }

    public String saveLocalFile() throws IOException {
        // 方式二：磁盘 File（FileStoreTest 同款）
        FileMeta fileMeta = FileMetaUtils.parse(new File("/data/demo/1.jpg"));
        return fileStore.storeFileMeta(List.of(fileMeta), "upload").get(0).getUrl();
    }

    public byte[] readBack(String groupName, String path) throws IOException {
        // local 后端 getInputStream 直读磁盘，不走 HTTP
        return fileStore.getByteArray(groupName, path);
    }
}
```

## 4. 底层 InputStreamStore 用法（InputStreamStoreTest 同款）

```java
@Autowired
private InputStreamStore inputStreamStore;

StoreResponse r = inputStreamStore.store("group", "jpeg", new FileInputStream("/data/demo/1.jpg"));
r.getGroupName();  // "group"
r.getPath();       // "{雪花ID}.jpeg"
r.getFullPath();   // local: 磁盘绝对路径 /data/fileupload/group/{雪花ID}.jpeg
r.getUrl();        // http://localhost:7892/group/{雪花ID}.jpeg

InputStream is = inputStreamStore.getInputStream("group", r.getPath());
inputStreamStore.delete("group", r.getPath());   // 文件不存在 → FileNotFoundException
```

## 5. 注意事项

- 同名覆盖：`store(group, storeName, ext, is)` 用相同 storeName 会覆盖旧文件；默认 storeName 为雪花 ID，不会撞名。
- `server-url` 不以 `/` 结尾会拼出 `...7892group/...` 之类的坏 URL（纯字符串拼接）。
- 返回 URL 是否可公网访问取决于静态服务的暴露方式；内网地址不要下发给外网前端。
- 多实例部署时 root-path 必须是共享存储（NFS 等），否则 A 实例存 B 实例读不到。
