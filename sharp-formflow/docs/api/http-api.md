# HTTP API 完整参考（4 个 Controller）

> 取证：form/controller/*.java、form/controller/instance/*.java 源码逐行。
> 通用说明：
> - **无任何认证/权限注解**（无 @PreAuthorize/@Secured），接口安全由业务工程 Security 配置决定 `[业务工程应如何保护 /forms/** 需要确认]`。
> - `Result` 包装（sharp-common）：`{"success":bool, "code":int, "message":str, "data":...}`；成功 code=200 message="OK"，失败 code=500/400/403/404/422。
> - 若业务工程注册了 sharp-common 的 `ResultWrappedConfig`（本模块不会自动注册），非 Result 返回值（如 FormBO）会被再包一层 Result；未注册则返回裸 JSON。下文按「未注册」描述裸结构，注册时自行加一层 `{success,code,message,data:...}`。
> - 校验失败（BindException/IllegalArgumentException 等）若业务注册了 sharp-common `ApiExceptionHandler`：HTTP 400，`Result{success:false, code:400, message:"参数验证失败", data:[{"field":"字段名","message":"字段名=被拒值,label+校验消息","rejectedValue":...}]}`；未注册则走 Spring Boot 默认错误响应。内置模板 JS 按前者编写。⚠️ ApiExceptionHandler 仅对「ajax 请求」返回 Result JSON（判定：`Accept` 含 application/json，或 `X-Requested-With: XMLHttpRequest`，或 URI 以 .json/.xml 结尾，或参数 `__ajax=json|xml`），否则 forward `/error` —— **调用 JSON 接口请带 `Accept: application/json`**。
> - Long id 在 JSON 中序列化为**字符串**（EntityId 上 ToStringSerializer）。

## 1. FormController ⭐ —— 表单定义（JSON）

类级前缀：`forms`（即 `/forms`）。

### 1.1 POST /forms —— 保存表单头
- 请求体：`Form` JSON。字段：`id`(Long，更新时传)、`code`(String，≤32，`^[0-9a-zA-Z_#/%-]{0,}$`)、`name`(String **必填** @NotBlank)、`formAdviceName`、`tableName`(当前未使用)、`repositoryName`(CREATE_TABLE 必填=业务 EntityDAO Bean 名)、`storageStrategy`(`"NONE"|"INNER_TABLE"|"CREATE_TABLE"`)、`tplName`、`additionalInfo`(对象)。
- 响应：`Result<Form>`（data 为回填 id 的 Form）。
- 语义：`formService.saveOrUpdate`（insertOrUpdate，id/code 命中则更新）。
- ⚠️ 不刷新 FormUtils 缓存。

### 1.2 POST /forms/configs —— 一步定义完整表单（推荐）
- 请求体：`{"form": Form, "configs": [CpnConfigurer...]}`（FormController.FormConfig，@Valid 级联校验）。
  CpnConfigurer 字段：`id`、`name`(**必填** @NotEmpty，落库后不可改)、`label`(**必填**)、`cpnType`(**必填**，枚举名字符串如 `"TEXT"`)、`validators`(数组，元素含 `validatorType` 及规则参数)、`options`(`[{"name","label"}]`)、`datasource`(字典 type，与 options 二选一)、`defaultValue`、`placeholder`、`disabled`、`cpnValueConverterName`、`additionalInfo`(TABLE 组件放 `columns`)。
- 响应：`Result<Long>`，data = form.id（JSON 中为字符串）。
- 语义：存 Form + 批量存/更新组件定义 + 重建关联（orderNum = configs 数组顺序）。
- 错误：name/label/cpnType 缺失 → 400（@Valid 级联）。⚠️ 此路径（FormCpnService）**不执行** `Cpn.check`：options label 重复不会被拦截（只有 §2.1 接口会）；cpnType=SINGLE_CHECKBOX 能存成功，但后续渲染/提交 500 NPE（无实现类）。

### 1.3 POST /forms/{formId} —— 给已有表单挂组件定义列表
- 路径参数：formId(Long)。请求体：`[CpnConfigurer...]`。
- 响应：`Result`（data=null）。
- 语义：同 1.2 的后半段（formCpnService.saveOrUpdateByConfigurer(formId, list)）。⚠️ 此入口**不校验 Form 存在性**（formId 直接写关联）`[不存在的 formId 行为需要确认——关联会插入但表单头缺失]`。

### 1.4 POST /forms/{formId}/configs —— 按已有组件 id 挂接
- 请求体：`[configId, configId...]`（Long 数组裸 JSON）。
- 响应：`Result`（data=null）。
- 语义：按 id 从 sys_form_configurer 查出组件（**保持传入 id 顺序**作为 orderNum；查不到的 id 会以 null 元素进入列表 → 后续 NPE 风险，**传入前确保 id 有效**）。

## 2. CpnConfigurerController ⭐ —— 组件定义库（JSON）

类级前缀：`forms/configurers`。

### 2.1 POST /forms/configurers —— 批量保存组件定义
- 请求体：`[CpnConfigurer...]`。
- 响应：`Result<Object[]>`，data = 各定义 id 数组（保存顺序）。
- 语义：`cpnConfigurerService.saveOrUpdate`：逐个 `Cpn.check`（options label 查重）→ disabled null→false → insertOrUpdate。
- 场景：先建「字段库」，之后多个表单通过 1.4 接口按 id 复用。

## 3. AjaxInstanceController ⭐ —— 表单实例数据（JSON，给前端 ajax / 前后端分离）

类级前缀：`forms/ajax`。

### 3.1 GET /forms/ajax/{formId} —— 取表单结构（新建态）
- 响应：**FormBO JSON**（非 Result，除非注册了包装器）：
```json
{
  "form": {"id":"4876...", "code":"entry", "name":"入职登记", "storageStrategy":"INNER_TABLE", "tplName":null, "formAdviceName":null, "repositoryName":null, "tableName":null, "additionalInfo":null},
  "instanceId": null,
  "propertyList": [
    {"id":"5001...", "name":"userName",
     "configurer": {"id":"4900...","name":"userName","label":"姓名","cpnType":"TEXT","validators":[{"validatorType":"REQUIRED","required":true}],"options":null,"datasource":null,"defaultValue":null,"placeholder":"请输入","disabled":false,"additionalInfo":null},
     "value": null}
  ],
  "data": {"userName": null},
  "actionUrl": "4876...",
  "method": "POST",
  "propertyMap": {"userName": {"...Property...": "..."}},
  "propertyData": {"userName": null}
}
```
（formAdvice @JsonIgnore 不输出；value 为各组件 UI 类型，defaultValue 配置时非 null。）
- 错误：formId 不存在 → NoSuchElementException → 500。

### 3.2 GET /forms/ajax/{formId}/{instanceId} —— 取实例数据（回显）
- 响应：同上，`instanceId` 有值、`method`="PUT"、`actionUrl`="formId/instanceId"、propertyList[].value/data 为已存储值（UI 类型）。
- 语义：按 Form.storageStrategy 从 sys_form_cpn_value 或业务表读取；触发 FormAdvice 钩子。

### 3.3 POST /forms/ajax/{formId} —— 提交（新建）
- 请求体：`Map<String,Object>` JSON，key = 组件 name，value = 原始值；**可选 `"id"`（必须是字符串）** 指定 instanceId。
  示例：`{"userName":"张三","entryDate":"2026-09-09","skills":["java","sql"],"salary":12000.5,"files":[{"id":123,"url":"/documents/download?id=123","fullName":"a.pdf"}]}`
- 响应：`Result<String>`，data = 新实例 instanceId 字符串（INNER_TABLE 由 IdGenerator.getSequenceId 生成；CREATE_TABLE 为业务表回填的 values["id"]）。
- 错误：校验失败 → 400（见通用说明 data 结构）；`"id"` 传数字 → 500 ClassCastException。
- 语义：全量覆盖式保存（INNER_TABLE 先删该 instanceId 旧值再插）。

### 3.4 PUT|POST /forms/ajax/{formId}/{instanceId} —— 更新
- 同 3.3，instanceId 走路径。响应 `Result<String>` data=instanceId。

### 3.5 DELETE /forms/ajax/{formId}?ids=1,2,3 —— 批量删除
- 参数：`ids` Long 数组（query 参数，逗号分隔或重复参数）。
- 响应：`Result<Integer>`，⚠️ data **恒为 1**（源码写死，不代表删除行数）。

### 3.6 DELETE /forms/ajax/{formId}/{instanceId} —— 删除单个
- 响应：`Result<Integer>`，data = 受影响行数（INNER_TABLE 为删除的 value 行数；CREATE_TABLE 为 deleteById 结果）。

## 4. PageInstanceController ⚠️ —— 服务端渲染页面（Thymeleaf）

类级前缀：`forms/page`。**返回视图，不是 JSON**——把它当 JSON 接口调（Accept: application/json / XHR）拿到的仍是 HTML。

### 4.1 GET /forms/page/{formId} 或 /forms/page/{formId}/{instanceId}
- Query 参数：任意；`readonly`（存在即 true，或 `readonly=true`）会被 HtmlTagUtils 规整为 `"true"/"false"` 放入 model.query（内置模板未消费，供自定义模板做只读渲染）。
- 流程：formService.getFormBO → FormAdvice.beforeRender → model 注入：
  - `formBO`：FormBO 对象
  - `model`：LinkedHashMap name→value
  - `query`：请求参数 Map\<String,String\>（多值逗号拼接 + readonly 规整）
- 视图名：`form.tplName`，为空时默认 **`"tpl/form/form"`——该模板在模块内不存在**（模块只有 `tpl/form`），必须设置 tplName 或自建模板，否则渲染 500。
- 前置：业务工程必须有 Thymeleaf（spring-boot-starter-thymeleaf），否则启动/渲染失败。

### 4.2 POST /forms/page/{formId} 或 /forms/page/{formId}/{instanceId}
- 请求体：`application/x-www-form-urlencoded`（普通 form submit），参数名 = 组件 name；多值参数（checkbox 组、`name[]`）自动转 List\<String\>；隐藏域 `id` 携带 instanceId。
- 成功 → 视图 **`"success"`**（模块内置 templates/success.html，仅一个 `<h1>success</h1>`）。
- 校验失败（BindException）→ model 追加 `errors`（Map\<字段名, FieldError\>）+ formBO/model（value 已回填为提交值）→ 重渲染 `tplName`，默认 **`"form"`——同样不存在**。
- ⚠️ 表单页 JS 的「ajax保存」按钮实际走 3.3/3.4 的 /forms/ajax 接口，「form保存」按钮走本接口。

## 5. 端到端调用序列（典型）

```
① 定义表单
   POST /forms/configs
   {"form":{"name":"入职登记","storageStrategy":"INNER_TABLE","tplName":"tpl/form"},
    "configs":[{"name":"userName","label":"姓名","cpnType":"TEXT",
                "validators":[{"validatorType":"REQUIRED","required":true},{"validatorType":"LENGTH","min":2,"max":20}]},
               {"name":"entryDate","label":"入职日期","cpnType":"DATE"},
               {"name":"skills","label":"技能","cpnType":"CHECKBOX",
                "options":[{"name":"java","label":"Java"},{"name":"sql","label":"SQL"}]}]}
   → Result.data = formId（字符串）

② 打开填写页面（服务端渲染）
   GET /forms/page/{formId}          → HTML（tpl/form 或自定义模板）
   （前后端分离则 GET /forms/ajax/{formId} 拿 FormBO JSON 自行渲染）

③ 提交
   页面通道：浏览器 POST /forms/page/{formId}（form-urlencoded，含隐藏域 id 可空）
            成功 → success 视图；校验失败 → 带 errors 重渲染
   ajax通道：POST /forms/ajax/{formId}  Content-Type: application/json
            成功 → Result{success:true,data:"<instanceId>"}
            失败 → Result{success:false,code:400,data:[{field,message,rejectedValue}]}
   服务端内部：FormService.post → CpnInstanceProcessor 逐字段 httpConverter+valid
            → BindException(回滚) 或 写 sys_form_cpn_value（先删后插）

④ 回显/查询
   GET /forms/ajax/{formId}/{instanceId}   → FormBO JSON（value 已是 UI 强类型）
   GET /forms/page/{formId}/{instanceId}   → 预填页面（method 变 PUT）
   Java 内部：formService.getFormBO(formId, instanceId).getData().get("userName")

⑤ 更新 / 删除
   PUT|POST /forms/ajax/{formId}/{instanceId}
   DELETE /forms/ajax/{formId}/{instanceId}（或 ?ids= 批量）
```

## 6. URL 汇总

| Method | URL | 类型 | Controller.方法 |
|---|---|---|---|
| POST | /forms | JSON | FormController.save |
| POST | /forms/configs | JSON | FormController.formCpnMapping |
| POST | /forms/{formId} | JSON | FormController.formIdCpnMapping |
| POST | /forms/{formId}/configs | JSON | FormController.formIdConfigIdsMapping |
| POST | /forms/configurers | JSON | CpnConfigurerController.save |
| GET | /forms/ajax/{formId} | JSON(FormBO) | AjaxInstanceController.get |
| GET | /forms/ajax/{formId}/{instanceId} | JSON(FormBO) | AjaxInstanceController.get |
| POST | /forms/ajax/{formId} | JSON(Result\<String\>) | AjaxInstanceController.save |
| PUT/POST | /forms/ajax/{formId}/{instanceId} | JSON(Result\<String\>) | AjaxInstanceController.update |
| DELETE | /forms/ajax/{formId}?ids= | JSON(Result) | AjaxInstanceController.delete |
| DELETE | /forms/ajax/{formId}/{instanceId} | JSON(Result) | AjaxInstanceController.delete |
| GET | /forms/page/{formId}[/{instanceId}] | **页面** | PageInstanceController.gotoFormPage |
| POST | /forms/page/{formId}[/{instanceId}] | **页面** | PageInstanceController.saveOrUpdate |

外部关联端点（sharp-fileupload，附件组件前端使用）：`POST /documents/upload?name=<fileInputId>`（multipart，字段名默认 `file`）→ `Result<List<Document>>`；`GET /documents/{id}`、`GET /documents/download?id=` 等见 sharp-fileupload 模块。
