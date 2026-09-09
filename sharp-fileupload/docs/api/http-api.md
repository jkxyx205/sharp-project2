# HTTP API 参考（/documents、/images）

> 概览表见 `../../API.md` 第 5 章；此处提供逐接口的请求/响应示例。

## 生效前提（三件事缺一不可）

1. **组件扫描**：业务启动类扫描 `com.rick.fileupload.client`（自动配置不注册 Controller）：
   ```java
   @SpringBootApplication(scanBasePackages = {"com.mybiz", "com.rick.fileupload.client"})
   ```
   ⚠️ 不要扫 `com.rick.fileupload` 根包（内含库自带的 `FileUploadApplication` @SpringBootApplication）。
2. **数据库**：配置 DataSource + 手动执行 `sql/sys_document-mysql.sql`（或 postgres 版）。
3. **存储配置**：`fileupload.local.*`（或自定义后端 Bean）；multipart 大小限制业务方自配。

**认证/权限**：模块内无 `@PreAuthorize`、无 Security 配置，所有接口默认无认证。生产环境必须由业务方在网关/Security 层保护（尤其 DELETE 与 download）。
**统一响应包装**：`Result<T>` → `{"success":true,"code":200,"message":"OK","data":...}`；失败时 success=false / code=500（`ResultCode.ERROR`）。Controller 抛出的异常不经 Result 包装（默认 500，或业务方全局异常处理器接管）。
**Document JSON 说明**：`id/createBy/updateBy` 序列化为字符串（雪花 ID 防前端精度丢失）；`data` 字节字段 `@JsonIgnore` 不输出；派生字段 `fullName/fullPath/url` 会输出；`createBy/createTime/updateBy/updateTime` 为只读（`JsonProperty.Access.READ_ONLY`，请求体传入被忽略）。

---

## DocumentController（`/documents`）

### 1. GET `/documents/{id}` — 附件详情

```bash
curl http://localhost:8080/documents/475029213070921728
```
```json
{
  "success": true, "code": 200, "message": "OK",
  "data": {
    "id": "475029213070921728",
    "name": "report", "extension": "pdf",
    "contentType": "application/pdf", "size": 204800,
    "groupName": "upload", "path": "475029213054144513.pdf",
    "fullName": "report.pdf", "fullPath": "upload/475029213054144513.pdf",
    "url": "http://localhost:7892/upload/475029213054144513.pdf",
    "createTime": "2026-09-01T10:00:00"
  }
}
```
错误：id 不存在 → `NoSuchElementException` → HTTP 500（模块不转 404）。

### 2. POST `/documents/upload` — multipart 批量上传（推荐）

- 文件表单字段名默认 `file`；可加请求参数 `name=otherField` 改字段名；可选参数 `groupName`（默认 `upload`）。

```bash
curl -F "file=@report.pdf" -F "file=@photo.jpg" -F "groupName=biz-order" \
     http://localhost:8080/documents/upload
```
响应：`Result<List<Document>>`，每项含 `id`（字符串雪花 ID）与 `url`。**业务方保存 `id` 到业务表**。

### 3. POST `/documents/upload2` — 显式 `file` 参数版

```bash
curl -F "file=@report.pdf" "http://localhost:8080/documents/upload2?groupName=biz-order"
```
与 2 的区别：`@RequestParam("file") List<MultipartFile>`，字段名固定为 `file`，不支持 `name` 参数改字段名。

### 4. POST `/documents/upload3` — JSON base64 直传

```bash
curl -X POST "http://localhost:8080/documents/upload3?groupName=biz-order" \
  -H "Content-Type: application/json" \
  -d '[{"fullName":"hello.txt","contentType":"text/plain","data":"aGVsbG8="}]'
```
- 请求体：`List<FileMeta>` JSON；**每项必须含 `data`（base64 字节，Jackson 自动解码）** 和扩展名信息（`extension` 或 `fullName`）。
- 场景：前端/服务间已持有 base64 内容，不走 multipart。

### 5. GET `/documents/download/{id}` — 单文件下载

```bash
curl -OJ http://localhost:8080/documents/download/475029213070921728
```
响应：文件字节流；`Content-disposition: attachment; filename=report.pdf`；Content-Type 按扩展名映射。

### 6. GET `/documents/download?id=1&id=2` — 批量 zip 下载

```bash
curl -OJ "http://localhost:8080/documents/download?id=475029213054144512&id=475029213070921728"
```
响应：zip 流，文件名 `【批量下载】{第一个文件fullName} 等{N}个文件.zip`（源码拼接无空格：`【批量下载】xxx.pdf等2个文件.zip`）。
**前提：`fileupload.tmp` 已配置且目录存在**，否则 NPE → 500。id 为空 → `RuntimeException("下载id不能为空")`。

### 7. GET `/documents/preview/{id}` — 302 重定向到存储直链

```bash
curl -i http://localhost:8080/documents/preview/475029213054144512
# HTTP/1.1 302  Location: http://localhost:7892/upload/475029213054144513.jpg
```
路径变量约束 `{id:\d+}`（纯数字）。要求浏览器能直连存储 URL（local 后端 = 静态文件服务在线）。

### 8. GET `/documents/preview2/{id}` — 服务端流式预览（inline）

```bash
# 普通预览（图片可带 ImageParam 参数）
curl -i "http://localhost:8080/documents/preview2/475029213054144512?w=800"

# Office 在线预览专用 URL（必须以 docx/xlsx/pptx 结尾）：
# https://view.officeapps.live.com/op/view.aspx?src={公网可达的}
#   http://host/documents/preview2/477896371325009920/hello.docx
```
- 两种路径：`/preview2/{id:\d+}`、`/preview2/{id:\d+}/{fileName}.{extension:(?i)docx|xlsx|pptx}`（第二种仅为满足 Office Online 对 URL 后缀的要求，fileName 任意）。
- 行为：图片（扩展名 bmp/png/jpeg/jpg/gif/ico 或 contentType 以 image 开头）→ `ImageService.write` 按 query 参数处理；其他文件 → 原始字节 inline 输出。
- 响应头：`Content-disposition: inline; filename={fullName}`。

### 9. PUT `/documents/{id}/rename?name=新名称` — 重命名

```bash
curl -X PUT "http://localhost:8080/documents/475029213070921728?name=新报表"
```
响应：`Result`（无 data）。仅改 DB `name` 列，不改存储物理文件名；影响后续下载时的 filename（fullName = 新name + 原extension）。

### 10. DELETE `/documents/{id}` — 删除

```bash
curl -X DELETE http://localhost:8080/documents/475029213070921728
```
响应：`Result`。行为：先删存储文件（失败仅打日志不中断），再**物理删除** DB 记录（`deleteByIds` → DELETE）。

---

## ImageController（`/images`）

### 1. GET `/images/{id}` — 图片实时处理预览

```bash
# 1:1 中心裁剪、旋转30°、宽500px（源自源码注释示例）
curl -o out.jpg "http://localhost:8080/images/475036437923139584?rw=1&rh=1&p=0&r=30&w=500"
# 等比缩到宽 300
curl -o thumb.jpg "http://localhost:8080/images/475036437923139584?w=300"
# 原图（仅 p=0）
curl -o raw.jpg "http://localhost:8080/images/475036437923139584?p=0"
```
query 参数 = `ImageParam`：`p w h r x y f q rw rh`（含义与合法值见 API.md §4.2）。
行为：无参数且 ≤500KB 输出原图；无参数且 >500KB 走重编码；响应 inline。
错误：id 不是图片附件 → `NotImageTypeException`（500，建议业务方异常处理器转 4xx）；id 不存在 → `NoSuchElementException`。

### 2. POST `/images/cropPic` — 手动裁剪（不落库）

```bash
curl -F "file=@photo.jpg" \
  "http://localhost:8080/images/cropPic?x=10&y=10&w=200&h=200&aspectRatioW=1&aspectRatioH=1"
```
- 参数全部**必填**（原生 int，缺失 → 400）：`x,y` 裁剪起点、`w,h` 区域、`aspectRatioW/H` 目标比例（传 0 = 不收敛）。
- 响应：`Result<FileMeta>`，`data` 字段 = 裁剪后图片 base64 字节；**无 url/path（未存储未落库）**。前端确认后携此内容走 `/documents/upload3` 或业务上传接口。

### 3. POST `/images/cropPic2` — 按比例自动中心裁剪（不落库）

```bash
curl -F "file=@photo.jpg" "http://localhost:8080/images/cropPic2?aspectRatioW=16&aspectRatioH=9"
```
响应同上。

### 4. POST `/images/create?text=张三` — 生成文字头像

```bash
curl -X POST "http://localhost:8080/images/create?text=张三"
# {"success":true,"code":200,"message":"OK","data":"http://localhost:7892/header/475036437923139585.png"}
```
- 行为：`NameImageCreator` 生成 100×100 圆角 PNG（中文取后两字/英文取首字母大写、随机背景色），存入 group `header`（文件名为雪花 ID），**只落存储不落 sys_document**，返回 URL。
- 场景：用户默认头像。

---

## 错误码汇总

| HTTP 状态 | 触发 | 来源 |
|---|---|---|
| 200 + `Result{success:true,code:200}` | 正常 | `ResultUtils.success` |
| 400 | cropPic 缺 int 参数；JSON 体解析失败 | Spring MVC |
| 413/500 | 超 `spring.servlet.multipart.*` 限制 | `MaxUploadSizeExceededException`（业务方异常处理器决定最终状态码） |
| 500 | id 不存在（NoSuchElementException）、存储 IOException、批量下载未配 tmp（NPE）、非图片走 /images（NotImageTypeException） | 未做业务错误码转换 |
| 302 | `/documents/preview/{id}` | 重定向到存储直链 |

> 模块自身不产生 `Result{success:false}` 响应（无 fail 调用点）；失败一律走异常。`ResultCode` 中定义的 403/404/422 未在模块内使用。
