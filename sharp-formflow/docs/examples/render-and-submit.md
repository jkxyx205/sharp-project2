# 示例：渲染表单页面与提交

> ⚠️ 示例依据模块内部真实调用链（PageInstanceController/AjaxInstanceController → FormService.post → CpnInstanceProcessor）与内置模板 tpl/form.html 的实际 JS 改写。仓库内暂无外部业务调用样例。

## 路线 1：服务端渲染（Thymeleaf，零前端代码）

前提：业务工程已加 `spring-boot-starter-thymeleaf`；表单定义时 `tplName="tpl/form"`（或自定义模板）。

```
① 打开新建页     GET  /forms/page/{formId}
② 打开编辑页     GET  /forms/page/{formId}/{instanceId}
③ 页面提交       POST /forms/page/{formId}[/{instanceId}]   (form-urlencoded，普通 form submit)
   成功 → success 视图；校验失败 → 表单页重渲染并标红错误字段
④ 只读页         GET  /forms/page/{formId}/{instanceId}?readonly
   （readonly 会规整进 model.query，内置模板未消费；自定义模板可用 ${query.readonly}）
```

内置模板已实现：按 propertyList 顺序渲染各类型控件、必填红星、HTML5 预校验、DATE 挂 datepicker、TABLE 挂 editableTable、FILE 走 /documents/upload 上传、两个提交按钮（form 原生提交 + ajax 保存走路线 2 接口）。

自定义模板时 model 契约（PageInstanceController 注入）：`formBO`（FormBO）、`model`（name→value Map）、`query`（参数 Map，含 readonly）、`errors`（仅校验失败重渲染时，Map\<字段名,FieldError\>）。参考 `templates/tpl/form.html` 源码。

⚠️ 内置模板依赖 bootcdn 的 Bootstrap 5.0.2 / jQuery 3.6.0 CDN；且其引用的 `/plugins/ajaxfileupload.js` 路径有误（实际在 `/ajaxfileupload.js`），内网/附件场景请复制模板到业务工程修正。

## 路线 2：前后端分离（JSON）

### 取表单结构（新建态）

```http
GET /forms/ajax/{formId}
```
响应 = FormBO JSON（见 docs/api/http-api.md §3.1）。前端据此渲染：`propertyList[i].configurer.cpnType` 决定控件，`options/datasource 填充结果` 给选项，`validatorList/validatorProperties` 做前端校验提示。

### 提交

```http
POST /forms/ajax/{formId}
Content-Type: application/json

{
  "userName": "张三",
  "entryDate": "2026-09-09",
  "skills": ["java", "sql"],
  "detail": [["Acme", "工程师"], ["Beta", "主管"]],
  "resume": [{"id": 487684156282011648, "url": "/documents/download?id=487684156282011648", "fullName": "resume.pdf"}]
}
```

- 成功：`{"success":true,"code":200,"message":"OK","data":"<instanceId字符串>"}` —— **记下 data 作为实例 id**。
- 校验失败（需业务工程注册 sharp-common ApiExceptionHandler 才是此结构）：
```json
{"success":false,"code":400,"message":"参数验证失败",
 "data":[{"field":"userName","message":"userName=,姓名必填项需要填写","rejectedValue":null}]}
```
- 更新：`PUT /forms/ajax/{formId}/{instanceId}`（body 同上，全量覆盖式保存；漏传的字段 INNER_TABLE 下会被删掉——post 内部先 deleteByInstanceId 再插入本次 values）。

### 各类型值的 JSON 形态（httpConverter 接受的输入）

| cpnType | 提交值 |
|---|---|
| TEXT/TEXTAREA/SELECT/RADIO/… | `"字符串"` |
| INTEGER_NUMBER | `123` 或 `"123"` |
| CURRENCY | `12000.5` 或 `"12000.5"` |
| NUMBER_TEXT | `"12000.5"`（数字字符串） |
| DATE / TIME | `"2026-09-09"` / `"14:30"` |
| SWITCH | `"1"`/`"0"`/`true`/`"true"`（httpConverter 统一转 "1"/"0"） |
| CHECKBOX / MULTIPLE_SELECT | `["a","b"]`（单个 `"a"` 也可，自动包装） |
| SINGLE_IMAGE | `{"id":...,"url":...,"fullName":...}`（或它的 JSON 字符串） |
| FILE | `[{...},{...}]`（或 JSON 字符串） |
| TABLE | `[["r1c1","r1c2"],["r2c1","r2c2"]]`（全空行自动丢弃） |

⚠️ 通用坑：body 里如带 `"id"` 必须是**字符串** `"487..."`，传 JSON 数字会 500（FormService.handle 强转 CharSequence）。

## 路线 3：Java 内部调用（业务代码里嵌表单）

```java
@RequiredArgsConstructor
@Service
public class EntryService {

    private final FormService formService;   // ⭐ 唯一推荐入口

    public Long submitEntry(Long formId, String name, String date) throws BindException {
        Map<String, Object> values = new HashMap<>();
        values.put("userName", name);
        values.put("entryDate", date);
        formService.post(formId, values);          // 校验失败抛 BindException（@Transactional 回滚）
        return Long.parseLong(String.valueOf(values.get("id"))); // post 会把生成的 instanceId 写回 values
    }
}
```

依据：`FormService.handle` 在 instanceId 为 null 且 INNER_TABLE 时 `values.put("id", instanceId)`；AjaxInstanceController.save 正是用 `values.get("id")` 返回新实例 id。

BindException 处理（对齐 ApiExceptionHandler 的做法）：

```java
try {
    formService.post(formId, values);
} catch (BindException e) {
    List<String> messages = e.getAllErrors().stream()
            .map(err -> ((FieldError) err).getField() + ": " + err.getDefaultMessage())
            .toList();
    // defaultMessage = "label + 校验消息"，如 "姓名必填项需要填写"
}
```
