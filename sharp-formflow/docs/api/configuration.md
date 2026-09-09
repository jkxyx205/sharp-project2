# 自动配置、建表与前端资源契约

## 1. 自动配置

### 1.1 注册链

```
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
  → com.rick.formflow.config.FormFlowServiceAutoConfiguration
META-INF/spring.factories（EnableAutoConfiguration 同款，Boot 2.x 兼容双注册）

FormFlowServiceAutoConfiguration                 🚫 内部
  @Configuration
  @ConditionalOnSingleCandidate(GridService.class)          ← 唯一前提条件
  @AutoConfigureAfter(SharpDatabaseAutoConfiguration.class) ← sharp-database 自动配置之后
  @Import(FormServiceConfiguration.class)

FormServiceConfiguration                          🚫 内部
  @Configuration
  @ComponentScan("com.rick.formflow.form")        ← Controller/Service/DAO/组件/校验器全靠它注册
  @Bean dateTimeToStringConverter                 （无条件）
  @Bean @ConditionalOnMissingBean formAdvice      （默认匿名空实现）
```

### 1.2 分工与关键结论

- **谁注册 Controller**：`FormServiceConfiguration` 的 `@ComponentScan("com.rick.formflow.form")`（`FormFlowServiceAutoConfiguration` 只负责条件判断 + @Import）。
- **业务方只加 Gradle 依赖能否让 4 个 Controller 自动生效**：**能**，前提是：
  1. Web 应用（spring-boot-starter-web 在运行时 classpath——本仓库全部 compileOnly，业务必须自带）；
  2. 单一 DataSource → sharp-database 自动配置产出单候选 `GridService`（`@ConditionalOnSingleCandidate(GridService.class)` 才通过）；
  3. sharp-meta 的 `DictService` Bean 存在（`CpnConfigurerDAO` `@Resource` 强依赖，缺失启动失败）；
  4. 业务主类**不需要**扫描 `com.rick.formflow`（自动配置已 Import ComponentScan）。
- `@ConditionalOnMissingBean` 仅 `formAdvice` 一处 → **唯一可被业务覆盖的 Bean**（业务注册任意 `FormAdvice` 类型 Bean 即替换默认空实现；注意替换后 Bean 名变化需与 Form.formAdviceName 对应）。
- Service/Controller/DAO/组件/校验器全部无条件注册，**不可覆盖**；重复注册同 CpnType 组件会导致 CpnManager `Collectors.toMap` 重复 key 抛 IllegalStateException（启动失败）。
- **@ConfigurationProperties：无。application.yml 配置项：无**（源码 grep 核实，无 @ConfigurationProperties/@Value/@ConditionalOnProperty）。
- `spring.factories` 与 `.imports` 内容一致，Boot 3 下 `.imports` 生效。

### 1.3 运行时需要业务工程提供

| 依赖 | 原因 |
|---|---|
| spring-boot-starter-web | compileOnly（根 build.gradle） |
| spring-boot-starter-jdbc + DataSource 驱动 | sharp-database 需要 |
| spring-boot-starter-validation | Form/CpnConfigurer 实体上的 Jakarta 注解 |
| **spring-boot-starter-thymeleaf** | 模板是 Thymeleaf（xmlns:th）；**全仓库任何 build.gradle 都没有 thymeleaf（已 grep 核实）**，PageInstanceController 要用必须自己加 |
| sharp-meta（含 DictService 自动配置） | CpnConfigurerDAO 强依赖 |
| sharp-fileupload 端点可用 | 附件/图片组件前端上传 `/documents/upload` |
| sharp-common `ApiExceptionHandler`/`ResultWrappedConfig` 注册 | 校验错误转 Result JSON / FormBO 包 Result（内置模板 JS 依赖前者）；formflow 不会注册它们 `[业务工程惯用注册方式需要确认]` |

## 2. 需要建的表

模块内**无建表脚本**（无 .sql、无 DbScriptUtils 调用，已核实）；sharp-database 有 `TableGenerator.createTable(Class)` 工具但 formflow 未调用。→ **业务需自行建表**，或调用 TableGenerator 生成 `[推荐建表方式需要确认]`。

列名规则：sharp-database `camelToSnake`（TableMetaResolver，源码核实）；公共列来自 BaseEntityInfo：`create_by, create_time(NOT NULL), update_by, update_time(NOT NULL), is_deleted(NOT NULL)`。以下 DDL 为**从实体注解反推的字段清单**（PostgreSQL 风格示例；具体列类型 sharp-database 如何映射 `[需要确认]`，尤其 Map/List 字段的 JSON 列类型）：

### sys_form（Form，BaseCodeEntity）
| 列 | 来源 | 说明 |
|---|---|---|
| id | @Id Long | 主键（应用侧雪花 id，非自增） |
| code | EntityIdCode，NOT NULL，≤32 | 外部唯一编号 |
| name | @NotBlank | 表单名 |
| form_advice_name | 注释"formAdviceName服务的名称" | FormAdvice Bean 名 |
| table_name | | 未使用 |
| repository_name | | CREATE_TABLE 的 EntityDAO Bean 名 |
| storage_strategy | 枚举 | NONE/INNER_TABLE/CREATE_TABLE（存枚举名字符串） |
| tpl_name | | Thymeleaf 视图名 |
| additional_info | Map | JSON |
| create_by/create_time/update_by/update_time/is_deleted | BaseEntityInfo | |

### sys_form_configurer（CpnConfigurer）
id、name(NOT NULL, **不可更新**)、label、type(枚举名)、validators(varchar(512), JSON 数组)、options(JSON 数组)、data_source、default_value、placeholder、is_disabled、cpn_value_converter_name、additional_info(JSON)、+ 5 个公共列。

### sys_form_cpn_configurer（FormCpn）
id、form_id(NOT NULL)、config_id(NOT NULL)、order_num、additional_info(JSON)、+ 公共列。

### sys_form_cpn_value（FormCpnValue）
id、form_cpn_id(NOT NULL)、form_id(NOT NULL)、config_id(NOT NULL)、instance_id(NOT NULL)、value(字符串/JSON 文本)、+ 公共列。

### 依赖表
- sharp-meta `sys_dict`（用 datasource 字典选项时）：type/name/label/sort/remark + 公共列（Dict 实体核实）。
- sharp-fileupload 的文档表（用附件组件时，见该模块文档）。

## 3. 前端资源契约

### 3.1 模板引擎与内置模板

- **Thymeleaf**（form.html `xmlns:th="http://www.thymeleaf.org"`；FormAdvice.beforeRender javadoc 亦写明 thymeleaf）。
- `templates/tpl/form.html` → 视图名 **`tpl/form`**；`templates/success.html` → 视图名 **`success`**。
- ⚠️ Controller 默认视图名是 `tpl/form/form`（GET）和 `form`（POST 校验失败），**模块内都不存在** → 用页面接口必须设置 `Form.tplName = "tpl/form"`（或自定义模板名）。

### 3.2 tpl/form.html 的 model 数据契约（自定义模板需照此提供，或直接用 PageInstanceController 注入的）

| model 属性 | 类型 | 说明 |
|---|---|---|
| formBO | FormBO | 必需。模板消费：`formBO.form.name/id/code`、`formBO.getActionUrl()`、`formBO.method`、`formBO.instanceId`、`formBO.propertyList`（p.name/p.value/p.configurer.{cpnType,label,placeholder,options,validatorList,validatorProperties,additionalInfo.columns}） |
| model | Map\<String,Object\> | name→value（LinkedHashMap 保序） |
| query | Map\<String,String\> | 请求参数（多值逗号拼接）；含规整后的 `readonly`（"true"/"false"） |
| errors | Map\<String,FieldError\> | 仅 POST 校验失败重渲染时存在 |

模板内直接引用了 Java 类型：`T(com.rick.formflow.form.cpn.core.CpnTypeEnum)`、`new com.rick.formflow.form.valid.Required(true)`、`T(com.rick.common.util.JsonUtils).toJson(...)` —— 自定义模板可复用这些表达式。

### 3.3 TABLE 组件的 additionalInfo.columns 契约（模板消费处）

```json
"additionalInfo": {
  "columns": [
    {"label": "名称", "validatorProperties": {"Required.required": true}},
    {"label": "数量"}
  ]
}
```
模板取 `c.label` 渲染表头、`c.validatorProperties.get('Required.required')` 决定红星；JS 只用 `columns.length` 初始化 editableTable。列对象更完整的 schema（如列级校验是否真正参与服务端校验——服务端 Table.valid 只跑 cpnValidators，**不校验单元格**）`[需要确认]`。

### 3.4 静态资源（jar 内 static/ → 映射到 URL 根）

| 资源 | URL | 说明 |
|---|---|---|
| static/jquery.form2json.js | /jquery.form2json.js | form 序列化：单值取 input value；checkbox/select 多值聚成数组（multiValSelector）；key 支持 `a.b` 层级 |
| static/ajaxfileupload.js | /ajaxfileupload.js | ⚠️ 模板引用的是 `/plugins/ajaxfileupload.js` → **404**，需业务修正 |
| static/editable-table/editable-table.js | /editable-table/editable-table.js | jQuery 插件 `$.fn.editableTable`：`getValue()` 返回二维数组（跳过末尾空行）；`readonly(true/'true')` 只读；另有 addEmptyLine、beforeRemoveCallback 等 |
| static/editable-table/index.html | /editable-table/index.html | 插件 demo 页（会随 jar 暴露到生产 URL，注意） |
| static/plugins/bootstrap-datepicker/* | /plugins/bootstrap-datepicker/... | 日期控件（min.js + zh-CN 语言包），DATE 组件初始化 format 'yyyy-mm-dd' |

### 3.5 外部 CDN 依赖（tpl/form.html 硬编码，非本地）

- Bootstrap 5.0.2 CSS：`https://cdn.bootcdn.net/ajax/libs/twitter-bootstrap/5.0.2/css/bootstrap.min.css`
- jQuery 3.6.0：`https://cdn.bootcdn.net/ajax/libs/jquery/3.6.0/jquery.min.js`
→ 内网/离线环境内置模板不可直接使用，需自定义模板改本地引用。

### 3.6 路径冲突风险

- `static/` 根下的 `jquery.form2json.js`、`ajaxfileupload.js` 映射到 `/**`，与业务工程同名文件互相覆盖（classpath 顺序决定）；`/plugins/**`、`/editable-table/**` 同理。业务若有自己的 `/plugins/` 目录需检查冲突。
- 表单页 JS 调用的上传端点写死 `/documents/upload`，业务网关若改路径需自定义模板。
