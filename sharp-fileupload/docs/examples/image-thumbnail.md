# 示例：图片缩略 / 裁剪 / 文字头像

> 蓝本：`src/test/java/com/rick/fileupload/ImageServiceTest.java`、`ImageService/ImageParam` 源码。底层为 Thumbnailator（模块 implementation 依赖，运行期自动带上）。

## 1. 注入

```java
@Autowired
private ImageService imageService;   // 自动配置注册（FileUploadAutoConfig#imageService）
```

## 2. HTTP 缩略图（最常用：附件图片实时处理，零代码）

前提：图片已通过 Document 体系落库（有 id）。

```html
<!-- 原图 -->
<img src="/documents/preview2/475036437923139584"/>
<!-- 宽 300 等比缩放 -->
<img src="/images/475036437923139584?w=300"/>
<!-- 200x200 方形缩略（1:1 中心裁剪） -->
<img src="/images/475036437923139584?rw=1&rh=1&w=200&h=200"/>
<!-- 源码注释示例：1:1 裁剪 + 旋转30° + 宽500 -->
<img src="/images/475036437923139584?rw=1&rh=1&p=0&r=30&w=500"/>
<!-- 转 jpg、质量 80 -->
<img src="/images/475036437923139584?f=jpg&q=80"/>
```

参数全集（`ImageParam`，均可选，不传即原样输出逻辑）：

| 参数 | 含义 |
|---|---|
| `w` / `h` | 目标宽/高（px）；只传一个 → 等比缩放；两个都传 → 配合 p/rw/rh 裁剪或拉伸 |
| `rw` / `rh` | 目标宽高比（1&1 方形、16&9 宽屏…），中心裁剪收敛 |
| `x` / `y` | 裁剪源区域起点（缺省中心） |
| `p` | 0=原图(仅p时)/拉伸(w,h齐备时 forceSize)；2=源区域取 w/2,h/2 放大2倍；3=w,h 按百分比取源区域放大2倍 |
| `r` | 旋转角度 |
| `f` | 输出格式（jpg/png…），缺省原格式 |
| `q` | 输出质量 0–100 |

无参数行为：≤500KB（`Constants.COMPRESS_THRESHOLD`）输出原图字节；>500KB 走重编码路径。
非图片附件 → `NotImageTypeException`（RuntimeException，"文件不是图片类型"）。

## 3. 编程式裁剪（ImageServiceTest 同款：裁剪后自行存储）

```java
File file = new File("/data/demo/1.jpg");
FileMeta fileMeta = FileMetaUtils.parse(file);        // data 读入内存

// 裁剪 9:5（中心裁剪；要求 fileMeta.data 非空）
ImageParam imageParam = new ImageParam();
imageParam.setRw(9);
imageParam.setRh(5);

FileMeta cropped = imageService.cropPic(fileMeta, imageParam);
// ⚠️ cropPic 只改内存中的 data，不落存储！需要保存再调：
String url = fileStore.storeFileMeta(Arrays.asList(cropped), "crop").get(0).getUrl();
```

手动指定区域：

```java
FileMeta m = imageService.cropPic(fileMeta, /*x*/10, /*y*/10, /*w*/200, /*h*/200,
                                  /*aspectRatioW*/1, /*aspectRatioH*/1);  // 比例传0=不收敛
```

HTTP 版（不落库，返回 base64 供前端确认后再上传）：
`POST /images/cropPic`（x,y,w,h,aspectRatioW,aspectRatioH 全必填）、`POST /images/cropPic2`（仅 aspectRatioW/H，中心裁剪）。

## 4. 文字头像（NameImageCreator 能力，经 ImageService 暴露）

```java
// 文件名=雪花ID；存 group "header"；只落存储不落 sys_document；返回 URL
String url = imageService.createImage("张三", "header");

// 指定 storeName（如用户id）→ 同 storeName 重复调用覆盖旧头像
String url2 = imageService.createImage("张三", "header", String.valueOf(userId));
```

HTTP 版：`POST /images/create?text=张三` → `Result<String>`（data=URL）。
生成规则（NameImageCreator）：100×100 圆角 PNG、随机背景色；中文取后两字（30px），单中文 50px，英文取首字母大写（42/60px），字体「微软雅黑」（Linux 无此字体时的回退 `[需要确认]`）。

## 5. 输出到任意流

```java
// write：自动处理 data 缺失（从存储读）；param 传 null = 原图/自动压缩语义
imageService.write(fileMeta, imageParam, response.getOutputStream());
```

## 6. 注意事项

- `ImageService` 处理链路全部基于内存 `byte[]`（`fileMeta.data`），超大图注意堆内存；
- `write`/`cropPic(*, os)` 会**关闭传入的 OutputStream**（源码 `os.close()`），复用 response 输出流时注意；
- 水印能力**不存在于模块主代码**（测试类 `ImageWriter` 的 Caption 水印只是 Thumbnailator 用法演示）；需要水印请业务方直接用 Thumbnailator 实现；
- `imageParam.f` 非空时 `write` 会把 `fileMeta.extension` 改成 f（影响本次输出格式，若 fileMeta 是落库实体副本注意别回写 DB）。
