# sharp-formflow 模块使用指南（AI 助手入口文档）

## 模块定位

**动态表单引擎**：表单结构（有哪些字段、什么控件、什么校验）以数据形式存库，运行时由本模块渲染页面 / 提供 JSON、执行服务端校验、并按配置的存储策略落库。

- **适用**：字段经常变化的登记/申报/问卷类表单；希望不写 Controller/实体/建表就能收集结构化数据（INNER_TABLE 策略）；表单结构由「表单设计器」类接口动态维护的场景。
- **不适用**：固定结构的业务表 CRUD（直接写实体 + EntityDAO 更简单高效）；需要复杂 SQL 报表查询的业务数据（EAV 存储查询困难，见下）；纯前后端分离且已有自己的表单渲染层的场景（本模块核心价值在服务端定义 + 校验 + 存储，前端模板可选）。
- ⚠️ **成熟度提示**：本模块当前**无任何外部调用方**（全仓库 grep 确认 `com.rick.formflow` 只在本模块内部出现），未经生产验证。使用时应保守：优先走现成的 4 个 Controller 和 `FormService` 公开方法，不要深入或绕过内部类。

## 核心心智模型

```
 Form (sys_form)                 表单定义：名称、存储策略、模板、回调 Bean 名
   │ 1:N（通过 FormCpn 关联，含排序）
   ▼
 FormCpn (sys_form_cpn_configurer)   表单↔组件关联：formId + configId + orderNum
   │ N:1
   ▼
 CpnConfigurer (sys_form_configurer) 组件定义：字段名 name、标签 label、类型 cpnType、
   │                                  选项 options/datasource、校验器 validators、默认值
   │ 提交时按 configurer 校验并转值
   ▼
 FormCpnValue (sys_form_cpn_value)   提交值（仅 INNER_TABLE 策略）：EAV 纵表，
                                     instanceId + formCpnId + configId + value(String/JSON)
```

一句话数据流：**定义**（POST /forms/configs 存 Form + CpnConfigurer + FormCpn）→ **渲染**（GET /forms/page/{formId} 或 /forms/ajax/{formId}，FormService.getFormBO 组装 FormBO）→ **提交**（POST /forms/ajax/{formId}，CpnInstanceProcessor 逐字段校验+转值）→ **存储**（INNER_TABLE 写 sys_form_cpn_value；CREATE_TABLE 经业务 EntityDAO 写业务宽表）→ **回显**（GET .../{formId}/{instanceId}，按策略读值 + Cpn.parseValue 转回 UI 类型）。

## 组件类型速查表（CpnTypeEnum 全量 22 个，code = 枚举名大写）

| code | 含义 | 实现类 | 提交值类型 |
|---|---|---|---|
| HIDDEN | 隐藏域 | Hidden | String |
| LABEL | 标签 | Label | String |
| TEXT | 短文本 | Text | String |
| TEXTAREA | 长文本 | TextArea | String |
| SELECT | 选项(单选下拉) | Select | String |
| GROUP_SELECT | 选项(分组) | GroupSelect | String |
| MULTIPLE_SELECT | 多选项 | MultipleSelect | List\<String\> |
| SEARCH_SELECT | 查询单选 | SearchSelect | String |
| SWITCH | 二选一 | Switch | String("1"/"0") |
| RADIO | 单选 | Radio | String |
| NUMBER_TEXT | 数字(文本存储) | NumberText | String |
| INTEGER_NUMBER | 数字(整数) | IntegerNumber | Integer |
| CURRENCY | 金额 | Currency | BigDecimal |
| CHECKBOX | 多选 | CheckBox | List\<String\> |
| SINGLE_CHECKBOX | 单选 | **无实现类！** 使用会 NPE | - |
| MOBILE | 手机号 | Mobile | String |
| SINGLE_IMAGE | 单图片 | SingleImage | Map\<String,Object\> |
| FILE | 文件(附件) | Attachment | List\<Map\<String,Object\>\> |
| EMAIL | 邮箱 | Email | String |
| DATE | 日期 | Date | String(yyyy-MM-dd) |
| TIME | 时间 | Time | String(HH:mm) |
| TABLE | 表格(可编辑子表) | Table | List\<List\>（JSON 二维数组） |

详见 `docs/api/components.md`。

## 使用原则

1. **业务入口只用**：`FormService`（⭐ 主要）、`FormCpnService` / `CpnConfigurerService`（定义态）、4 个现成 Controller（HTTP）。
2. **组件类不要直接 new**：`Text`/`Select` 等是 Spring Bean，由 `CpnManager` 按 `CpnTypeEnum` 静态分派；手动 new 会导致泛型类型未解析（`afterPropertiesSet` 未执行）、校验/转值行为异常。扩展组件 = 新建 `@Component` 类继承 `AbstractCpn<T>`，自动注册。
3. **值校验用本模块自研体系**（`CpnConfigurer.validators` + `com.rick.formflow.form.valid.*`），**不是** Jakarta Bean Validation。Jakarta `@NotBlank/@NotNull` 只用于 Form/CpnConfigurer 等**定义实体本身**的合法性校验，两套体系并存、职责不同，不要混用。
4. **修改表单定义后缓存不会自动失效**：`FormUtils.formCacheMap`（静态 HashMap）只在首次读取时填充，`FormCpnService`/`CpnConfigurerService` 更新定义**不刷新缓存**，需重启或手动 `FormUtils.update(formId, null)` 后重读（传 null 会使下次 getFormBO 重建缓存——注意 `getFormBOByIdAndInstanceId` 判空后重新加载）。
5. 提交 JSON 中 `id` 字段必须是**字符串**（`FormService.handle` 里 `(CharSequence) values.get("id")` 强转），传数字会 ClassCastException。

## 源码阅读规则

```
默认不要扫描整个模块源码。
使用模块时：
1. 优先阅读 CLAUDE.md
2. 再阅读 API.md
3. 如果 API.md 已经可以解决问题，不要继续阅读源码
4. 只有在文档无法解决问题、排查 Bug 或确认特殊行为时，才阅读相关源码
```

## API 文档索引

| 文档 | 内容 |
|---|---|
| `API.md` | 主手册：领域模型、FormService、组件/校验体系摘要、HTTP API 摘要、缓存、配置、错误 |
| `ARCHITECTURE.md` | 分层、数据流、注册分派机制、存储模型、自动配置、扩展点、已知限制 |
| `docs/api/components.md` | 21 个内置组件完整参考 |
| `docs/api/validation.md` | 12 个 ValidatorTypeEnum + 13 个校验规则 + 扩展方式 |
| `docs/api/http-api.md` | 4 个 Controller 全部接口 + 端到端调用序列 |
| `docs/api/configuration.md` | 自动配置、建表清单、前端资源/模板契约 |
| `docs/examples/*.md` | 定义表单、渲染提交、查询数据、自定义组件/校验器、FormAdvice |
| `docs/troubleshooting.md` | 常见错误与排查 |

## 禁止行为

- 🚫 直接 new 组件类（`new Text()` 等）或校验器注册绕过 `CpnManager`/`ValidatorManager`。
- 🚫 调用 `CpnConfigurerDAO` 的 `select` 覆写以外方式绕过 dict 数据源填充（直接用它没问题，但别绕过它自己查 sys_form_configurer，会丢 datasource→options 的字典转换）。
- 🚫 用 Jakarta `@Valid` 去校验用户提交的表单值（值校验走 `FormService.post` 内部机制）。
- 🚫 对 INNER_TABLE 表单数据手写 JOIN SQL 当宽表用（EAV 结构，见 ARCHITECTURE.md）。
- 🚫 使用 `SINGLE_CHECKBOX` 类型（无实现，NPE）。
- 🚫 修改本模块源码/static/templates 来适配业务（用 tplName 指向自定义模板、用扩展点）。

## 引入清单

**Gradle**（group=`com.rick.formflow`, version=`0.0.1-SNAPSHOT`，发布到 mavenLocal）：
```gradle
implementation("com.rick.formflow:sharp-formflow:0.0.1-SNAPSHOT")
```

⚠️ **已知坑（已核实）**：`sharp-formflow/build.gradle` 内部以 Maven 坐标引入 `com.rick.fileupload:sharp-fileupload:3.0-SNAPSHOT`，而本仓库 sharp-fileupload 实际版本是 `0.0.1-SNAPSHOT`（libs.versions.toml `sharp = "0.0.1-SNAPSHOT"`）。`3.0-SNAPSHOT` 会解析到 `~/.m2` 里 2024-08-11 的**陈旧产物**（已确认该目录存在），而不是当前源码构建的 fileupload。使用附件/图片组件前必须核实实际生效的 fileupload 版本，或在自己工程里显式声明正确版本的 sharp-fileupload 覆盖。

**业务方必须自备**（本模块全部 compileOnly，根 build.gradle 未提供运行时依赖）：
- `spring-boot-starter-web`、`spring-boot-starter-jdbc`、`spring-boot-starter-validation`、DataSource（PostgreSQL/MySQL 等）
- `spring-boot-starter-thymeleaf` —— **整个仓库无任何 thymeleaf 依赖（已 grep 核实）**，不加大概率启动失败（PageInstanceController 返回视图名无法解析）或页面 500
- sharp-meta 的 `DictService` Bean 必须在容器中（`CpnConfigurerDAO` 用 `@Resource` 强依赖，缺失则启动失败）
- 附件功能需 sharp-fileupload 的 `/documents/upload` 端点生效
- 若需要统一 `Result` 包装和 `BindException → Result` 转换，需注册 sharp-common 的 `ResultWrappedConfig` 与 `ApiExceptionHandler`（本模块 ComponentScan 只扫 `com.rick.formflow.form`，不会自动注册它们）[业务工程注册方式需要确认]

**需要建的表**（模块内无建表脚本，已核实；列名按 sharp-database camelToSnake 规则从实体反推）：
`sys_form`、`sys_form_configurer`、`sys_form_cpn_configurer`、`sys_form_cpn_value`；用字典数据源还需 sharp-meta 的 `sys_dict`。字段清单见 `docs/api/configuration.md`。

**自动配置**：`FormFlowServiceAutoConfiguration`（AutoConfiguration.imports 注册）→ `@ConditionalOnSingleCandidate(GridService.class)`（即 sharp-database 自动配置生效）→ `@Import(FormServiceConfiguration)` → `@ComponentScan("com.rick.formflow.form")`。**只加 Gradle 依赖（+ 上述运行时前提），4 个 Controller、全部 Service/DAO/组件/校验器即自动注册，无需业务方 @Import 或扫描**。无任何 `@ConfigurationProperties` 配置项。
