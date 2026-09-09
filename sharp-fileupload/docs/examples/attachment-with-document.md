# 示例：附件管理（Document 落库 + 关联业务单据）

> 蓝本：`src/test/java/com/rick/fileupload/DocumentServiceTest.java` 与 `DocumentController/DocumentServiceImpl` 源码。

## 0. 前置条件（缺一不可）

1. 业务工程配置 `spring.datasource`（Document 体系依赖 sharp-database，其自动配置要求容器中存在唯一 DataSource 候选）；
2. **手动建表**：执行 `sharp-fileupload/sql/sys_document-mysql.sql` 或 `sys_document-postgres.sql`
   （⚠️ MySQL 脚本首行 `DROP TABLE IF EXISTS sys_document`，已有数据的库请只摘取 CREATE 语句）；
3. 组件扫描 `com.rick.fileupload.client`（注册 `DocumentServiceImpl/@Service`、`DocumentDAO/@Repository`，需要 HTTP 接口时同包的 Controller 一并生效）；
4. 存储配置就绪（见 `upload-local.md` / `upload-minio.md`）；用批量下载需配 `fileupload.tmp`。

## 1. 编程式：上传 → 拿 ID → 关联业务表

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final DocumentService documentService;   // ⭐ 注入接口，勿注入 DocumentServiceImpl

    public void createOrder(OrderForm form, List<MultipartFile> attachments) throws IOException {
        // 1) MultipartFile → FileMeta（data 会被整体读入内存）
        List<FileMeta> fileMetaList = FileMetaUtils.parse(attachments);

        // 2) 存存储 + 落 sys_document，一步完成
        List<Document> docs = documentService.store(fileMetaList, "order");

        // 3) 业务表只存 document.id（雪花 Long；接口 JSON 中为字符串）
        Order order = new Order();
        order.setAttachmentIds(docs.stream().map(Document::getId).toList());
        // orderRepository.save(order);
    }
}
```

`DocumentServiceTest` 同款单文件流程：

```java
FileMeta fileMeta = FileMetaUtils.parse(new File("/data/demo/1.jpg"));
Document doc = documentService.store(fileMeta, "document");
long id = doc.getId();                 // 已自动生成并回填
String url = doc.getUrl();             // 存储访问 URL（也可 documentService.getURL(id)）
documentService.rename(id, "Test");    // 仅改 DB name 列
documentService.delete(id);            // 删存储文件 + 物理删 DB 记录
```

## 2. HTTP 式：前端直接调现成接口

```bash
# 上传（返回 List<Document>，前端记下每个 id）
curl -F "file=@contract.pdf" -F "groupName=order" http://localhost:8080/documents/upload

# 业务表单提交时把 id 存入业务字段；之后：
curl http://localhost:8080/documents/475029213070921728          # 详情(含url)
curl -OJ http://localhost:8080/documents/download/475029213070921728   # 下载
curl -OJ "http://localhost:8080/documents/download?id=1&id=2"          # 批量zip(需fileupload.tmp)
curl -i http://localhost:8080/documents/preview/475029213054144512     # 302→存储直链
curl -i "http://localhost:8080/documents/preview2/475029213054144512?w=800"  # inline流式预览
curl -X PUT "http://localhost:8080/documents/475029213070921728?name=新合同" # 重命名
curl -X DELETE http://localhost:8080/documents/475029213070921728          # 删除
```

图片附件的实时缩放预览走 `/images/{id}?w=...`（见 `image-thumbnail.md`）。

## 3. 删除业务单据时清理附件

```java
public void deleteOrder(Order order) {
    documentService.delete(order.getAttachmentIds().toArray(new Long[0]));  // 变参 Long...
    // 再删业务记录
}
```

注意（源码行为）：存储文件删除失败仅 `printStackTrace`，DB 记录仍会被物理删除 → 可能残留孤儿文件；对账需自建。

## 4. 按业务条件查附件（DocumentDAO，⚠️ 特定场景）

```java
@Autowired
private DocumentDAO documentDAO;    // extends EntityDAOImpl<Document, Long>

Optional<Document> doc = documentDAO.selectById(id);
List<Document> list = documentDAO.select("group_name = ?", "order");
```

仅用于查询；**增删改一律走 `DocumentService`**（它保证「先存储后落库 / 先删文件后删记录」的顺序）。

## 5. 常见坑（本场景专属）

| 坑 | 后果 | 规避 |
|---|---|---|
| 只用 FileStore 不落 Document | 无 id 可用现成下载/预览/删除接口 | 附件场景一律 `DocumentService.store` |
| 业务表存 url 而不是 id | server-url/后端切换后全部失效 | 存 id，用时 `getURL(id)` |
| 未建表 | insert 报 table not exist（启动不报错） | 前置执行 sql 脚本 |
| 上传 >2.1GB 文件 | `sys_document.size` 列为 int，溢出 | 大文件走 `InputStreamStore.store` 流式，不入 Document |
| findById 不存在的 id | `NoSuchElementException` → 500 | 业务侧先校验或全局异常处理器兜底 |
