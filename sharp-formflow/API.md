# sharp-formflow API 手册

> 推荐等级：⭐ 推荐使用 / ⚠️ 特定场景使用 / ❌ 不推荐使用 / 🚫 内部 API，不允许业务代码调用 / 🗑 已废弃（源码有 @Deprecated 才标）
>
> 本手册全部内容取自源码（sharp-formflow/src/main/java，67 个文件）。⚠️ 本模块当前无外部调用方，示例均依据模块内部真实调用链（controller → service → dao）改写。

## 目录

1. [领域模型](#1-领域模型)
2. [表单服务](#2-表单服务)
3. [组件体系与扩展](#3-组件体系与扩展)
4. [校验体系](#4-校验体系)
5. [HTTP API](#5-http-api)
6. [数据存取与查询](#6-数据存取与查询)
7. [缓存](#7-缓存)
8. [配置项](#8-配置项)
9. [异常与错误码](#9-异常与错误码)

---

## 1. 领域模型

### 1.1 四个核心实体的关系

| 实体 | 表 | 角色 | 类比 |
|---|---|---|---|
| `Form` | sys_form | 表单定义（一张表单） | 「表单头」 |
| `CpnConfigurer` | sys_form_configurer | 组件定义（一个字段：类型/标签/选项/校验），**可被多个表单复用** | 「字段库」 |
| `FormCpn` | sys_form_cpn_configurer | 表单↔组件的多对多关联 + 排序（formId + configId + orderNum） | 「表单明细行」 |
| `FormCpnValue` | sys_form_cpn_value | 用户提交值（仅 INNER_TABLE 策略），EAV 纵表：instanceId + formCpnId + formId + configId + value(String) | 「填报数据」 |

### 1.2 Form ⭐（`form/cpn/core/Form.java`）

`extends BaseCodeEntity<Long>`，`@Table("sys_form")`。

| 字段 | 类型 | 约束 | 含义 |
|---|---|---|---|
| id | Long | @Id | 主键（雪花序列） |
| code | String | ≤32，`^[0-9a-zA-Z_#/%-]{0,}$` | 外部可见唯一编号（继承自 BaseCodeEntity） |
| name | String | @NotBlank | 表单名 |
| formAdviceName | String | - | `FormAdvice` 回调 Bean 的名称（Spring bean name），空则无回调 |
| tableName | String | - | 外部表名（当前源码中读取/写入路径均被注释，**实际未使用**） |
| repositoryName | String | - | CREATE_TABLE 策略下业务 `EntityDAO` 的 **Spring Bean 名**（必填才能存取） |
| storageStrategy | StorageStrategyEnum | - | `NONE`(无存储) / `INNER_TABLE`(内部EAV表) / `CREATE_TABLE`(外部业务表) |
| tplName | String | - | 服务端渲染视图名（Thymeleaf），空则用 Controller 默认值（**默认值模板不存在**，见 troubleshooting） |
| additionalInfo | Map\<String,Object\> | - | 自定义扩展信息（内置模板仅 TABLE 组件读取 `additionalInfo.columns`；`FormConstants` 定义了 showSaveFormBtn/label-col/pane-list/css/js 等 key 但内置模板未消费，供自定义模板使用） |

### 1.3 CpnConfigurer ⭐（`form/cpn/core/CpnConfigurer.java`）

`extends BaseEntity<Long>`，`@Table("sys_form_configurer")`。**组件（字段）定义**。

| 字段 | 列 | 类型 | 含义 |
|---|---|---|---|
| name | name | String，@NotEmpty，**updatable=false** | 字段名（HTTP 参数名 / 业务表列的属性名）。⚠️ 落库后不可更新 |
| label | label | String，@NotBlank | 显示标签（也是校验错误消息前缀） |
| cpnType | type | CpnTypeEnum，@NotNull | 组件类型（存枚举名字符串） |
| validators | validators | Set\<Map\<String,?\>\>，varchar(512) | 校验器配置的 JSON 形态，如 `{"validatorType":"LENGTH","min":0,"max":32}`；`getValidatorList()` 会按 `validatorType` 反序列化为具体 Validator，并自动并入组件自带校验器（cpnValidators） |
| validatorList | -（@Transient） | List\<Validator\> | 运行态对象；也可代码里 set 它，`getValidators()` 会转回 Set\<Map\> 落库 |
| options | options | List\<CpnOption\> | 静态选项 `[{name,label}]`；`CpnOption(name,label)`，name 空时取 label |
| datasource | data_source | String | sharp-meta 字典 type；配置后 `CpnConfigurerDAO.select` 会用 `DictService.getDictByType(datasource)` 的 Dict(name,label) **覆盖** options |
| defaultValue | default_value | String | 无 instanceId 渲染时的默认值（经 Cpn.parseValue 转换） |
| placeholder | placeholder | String | 输入提示 |
| disabled | is_disabled | Boolean | 是否禁用（saveOrUpdate 时 null 会被置 false） |
| cpnValueConverterName | cpn_value_converter_name | String | `CpnValueConverter` Bean 名，读取实例值时先做自定义转换（如 LocalDateTime→String） |
| additionalInfo | additional_info | Map\<String,Object\> | 组件级扩展（TABLE 组件的 `columns` 放这里） |

### 1.4 FormCpn 🚫（`form/cpn/core/FormCpn.java`）

`@Table("sys_form_cpn_configurer")`：`formId`(@NotNull)、`configId`(@NotNull)、`orderNum`(Integer)、`additionalInfo`。纯关联记录，由 `FormCpnService` 维护，业务代码不应直接读写。

### 1.5 FormCpnValue 🚫（`form/cpn/core/FormCpnValue.java`）

`@Table("sys_form_cpn_value")`：`formCpnId`、`formId`、`configId`、`instanceId`（均 @NotNull，Long）、`value`(String)。所有复杂值（List/Map/BigDecimal）都经 `Cpn.getStringValue` 序列化为 String（JSON）存储。由 `FormService.post` 写入、`getFormBO` 读取，业务代码不应直接写。

### 1.6 FormBO ⭐（`form/service/bo/FormBO.java`）

运行态表单视图对象（`@Value` 不可变），是 **GET /forms/ajax/{formId} 的响应体**、也是模板渲染的 model 属性：

| 成员 | 类型 | 说明 |
|---|---|---|
| form | Form | 表单定义 |
| instanceId | Long | 实例 id（新建时为 null） |
| propertyList | List\<Property\> | 按 orderNum 排序的字段列表；`Property{id(=formCpnId), name, configurer, value(已 parseValue 的 UI 类型值，可 set)}` |
| data | Map\<String,Object\> | name → 值 |
| formAdvice | FormAdvice | @JsonIgnore，不出现在响应里 |
| getActionUrl() | String | `formId` 或 `formId/instanceId`（拼 ajax 提交 URL 用） |
| getMethod() | String | 有 instanceId → "PUT"，否则 "POST" |
| getPropertyMap()/getPropertyData() | Map | name 索引的 Property / 值 |

JSON 响应中 Long 型 id 序列化为字符串（`ToStringSerializer`）。

---

## 2. 表单服务

### 2.1 FormService ⭐（`form/service/FormService.java`，@Service @Validated）

业务方**最主要入口**。构造注入 4 个 DAO + `Map<String,FormAdvice>` + `Map<String,CpnValueConverter>`（Spring 按 Bean 名注入全部实现）。

#### `Form saveOrUpdate(@Valid Form form)`
- **用途**：新建/更新表单头（仅 sys_form，不含组件关联）。
- **参数**：form（必填；id 为空则插入，非空或 code 命中则更新——由 sharp-database `EntityCodeDAOImpl.insertOrUpdate` 决定）。
- **返回**：回填 id 后的 Form，不为 null。
- **示例**：`Form f = formService.saveOrUpdate(Form.builder().name("入职登记").storageStrategy(Form.StorageStrategyEnum.INNER_TABLE).build());`
- **场景**：单独改表单头（名称/模板/策略）。定义完整表单推荐用 `FormCpnService.saveOrUpdateByConfigurer(Form, configurers)` 一步到位。
- **不应**：改完组件关联后指望它刷新缓存（见 §7）。
- **相关**：`POST /forms`。

#### `FormBO getFormBO(Long formId, Long instanceId)` / `getFormBOById(Long formId)` / `getFormBOByIdAndInstanceId(Long formId, Long instanceId)`
- **用途**：组装运行态表单（定义 + 值），渲染页面或返回 JSON 的唯一读取入口。
- **参数**：formId 必填；instanceId 可空（空 = 新建态，值取 defaultValue）。
- **返回**：FormBO，非 null；formId 不存在时 `formDAO.selectById(formId).get()` 抛 `NoSuchElementException`。
- **行为**：走 FormUtils 缓存 → 深克隆 → 按 storageStrategy 读实例值 → CpnValueConverter（若配置）→ `Cpn.parseValue` → 触发 FormAdvice 的 init/beforeGetInstance/afterGetInstance/beforeReturn 钩子。
- **示例**：`FormBO bo = formService.getFormBO(formId, null); bo.getPropertyList().forEach(p -> ...);`
- **场景**：自定义页面渲染、给前端出表单结构 JSON、读取某实例数据。
- **不应**：在循环里高频调用（每次 `SerializationUtils.clone` 整个 FormCache，有开销）。
- **相关**：`GET /forms/ajax/{formId}[/{instanceId}]`、`GET /forms/page/{formId}[/{instanceId}]`。

#### `void post(Long formId, Map<String,Object> values) throws BindException` / `post(Long formId, Long instanceId, Map<String,Object> values)`
- **用途**：**提交表单数据**：校验 → 类型转换 → 按策略存储。@Transactional。
- **参数**：
  - `values`：key = CpnConfigurer.name，value = 前端原始值（String/List/Map/数字均可，经 `Cpn.httpConverter` 转换）。特殊 key：`"id"`（**必须是字符串**，非空则作为 instanceId 更新）、`"formId"` 会被自动放入。
  - `instanceId`：显式实例 id（两参版本由 values.id 推导）。
- **返回**：void。校验失败抛 `BindException`（内含每字段 FieldError，message = label + 校验消息）。
- **存储**：INNER_TABLE → 先 `deleteByInstanceId` 再批量插入 sys_form_cpn_value（**全量覆盖式保存**）；新建时 instanceId = `IdGenerator.getSequenceId()`。CREATE_TABLE → 先给 FormAdvice.insertOrUpdate(values) 机会（返回 true 则跳过默认存储），否则 `applicationContext.getBean(repositoryName, EntityDAO.class).insertOrUpdate(values)` 写业务表。NONE → 只校验不存储。
- **示例**：
  ```java
  Map<String, Object> values = new HashMap<>();
  values.put("userName", "张三");
  values.put("entryDate", "2026-09-09");
  values.put("skills", List.of("java", "sql")); // CHECKBOX/MULTIPLE_SELECT
  formService.post(formId, values);              // 失败抛 BindException
  ```
- **场景**：自定义提交通道；现成 HTTP 入口是 `/forms/ajax/**`。
- **不应**：values 里传 `"id": 123`（数字）——`(CharSequence)` 强转会 ClassCastException；也不要在 CREATE_TABLE 且未配 repositoryName 时调用（**静默不存储**，源码相关分支被注释）。
- **相关**：`FormAdvice.beforeInstanceHandle/afterInstanceHandle`。

#### `int delete(Long formId, Long instanceId)` / `int delete(Long formId, Long[] instanceIds)`
- **用途**：删除表单实例数据。@Transactional（单实例版本）。
- **行为**：INNER_TABLE → 物理删除 sys_form_cpn_value 中该 instanceId 的行；CREATE_TABLE → `EntityDAO.deleteById`。前后触发 FormAdvice.beforeDeleteInstance/afterDeleteInstance。
- **返回**：单实例版 = 受影响行数；⚠️ 数组版**恒返回 1**（源码写死）。
- **不应**：依赖数组版返回值判断删除条数。

#### `FormAdvice getFormAdviceByName(String formAdviceName)`
- **用途**：按 Bean 名取回调实现。⚠️ 特定场景（一般框架内部用）。

### 2.2 FormCpnService ⭐（定义态，全部 @Transactional）

| 方法 | 说明 |
|---|---|
| `saveOrUpdateByConfigurer(Form form, Collection<CpnConfigurer> configurerList)` | **一步定义完整表单**：存 Form → 存全部 CpnConfigurer → 重建 FormCpn 关联（orderNum 按集合顺序 0..n）。configurer 无 id 则新建 |
| `saveOrUpdateByConfigurer(Long formId, Collection<CpnConfigurer> configurerList)` | 同上，Form 已存在 |
| `saveOrUpdateByConfigIds(Long formId, Long... configIds)` / `(Long formId, Collection<Long> configIds)` | 用**已有**组件定义（按 id）关联到表单 |
| `saveOrUpdateByConfigIds(Long formId, List<FormCpn> formCpnList)` | 🚫 低层入口（Controller 未直接使用，前两个重载最终走它） |

关联重建逻辑：先读出旧 FormCpn，configId 已存在的沿用其 id，然后 `deleteByFormId`（物理删除）+ 重插。⚠️ **定义变更后 FormUtils 缓存不失效**（见 §7）。

### 2.3 CpnConfigurerService ⭐

| 方法 | 说明 |
|---|---|
| `Collection<CpnConfigurer> saveOrUpdate(List<CpnConfigurer> configurers)` | 存组件定义。先执行 `CpnManager.getCpnByType(cpnType).check(configurer)`（选项 label 不得重复，抛 IllegalArgumentException），`disabled` null → false。返回回填 id 的集合 |
| `CpnConfigurer findById(Long id)` | 按 id 查（不存在抛 NoSuchElementException）。⚠️ 走 `selectById`（varargs SQL 路径），**不经过** `CpnConfigurerDAO` 覆写的 `select(Class, String, Map)`，因此 datasource→options 的字典填充**不会执行**（FormService 内部用的 `selectByIds` 是 Map 参数路径，会填充）。查出来的 configurer 若配了 datasource，options 可能为旧值/空 |

私有方法 `checkIfAvailable`（校验器与组件兼容性检查）当前未被调用 🚫。

### 2.4 FormAdvice ⭐（`form/service/FormAdvice.java`，接口）

**不是 @ControllerAdvice**，是表单生命周期回调 SPI。实现类注册为 Spring Bean（Bean 名 = Form.formAdviceName 引用的名字），FormService 注入 `Map<String, FormAdvice>` 按名查找。所有方法有默认空实现，按需覆写：

| 钩子 | 时机 |
|---|---|
| `beforeInstanceHandle(FormBO, instanceId, values)` | post 校验通过、写库前。⚠️ 改 values 只影响 CREATE_TABLE/NONE（INNER_TABLE 的 FormCpnValue 列表此时已构建完） |
| `afterInstanceHandle(FormBO, instanceId, values)` | 写库后 |
| `insertOrUpdate(Map values)` | CREATE_TABLE 策略写库前；**返回 true 表示业务已自行存储，跳过默认 EntityDAO 写入** |
| `afterGetInstance(Form, instanceId, propertyList, valueMap)` | 读实例、值已填充后 |
| `beforeReturn(Form, instanceId, propertyList, valueMap)` | getFormBO 返回前（新建/回显都会走） |
| `beforeRender(Map parameterMap, FormBO)` | PageInstanceController 渲染前，可替换 FormBO |
| `beforeDeleteInstance(Long)` / `afterDeleteInstance(Long)` | 删除前后 |
| `init(...)` 🗑 / `beforeGetInstance(...)` 🗑 | @Deprecated，用 afterGetInstance 代替 |

自动配置提供默认匿名实现（Bean 名 `formAdvice`，@ConditionalOnMissingBean，可被业务同类型 Bean 替换）。

### 2.5 CpnValueConverter ⚠️（`form/service/CpnValueConverter.java`）

```java
public interface CpnValueConverter<K, V> { V convert(K k); }
```
读实例值时，若 `CpnConfigurer.cpnValueConverterName` 命中某个 Bean 名，则先 `convert(value)` 再 `Cpn.parseValue`。用途：DB 列类型 ≠ 组件期望类型时做桥接。内置实现：`DateTimeToStringConverter`（Bean 名 `dateTimeToStringConverter`，LocalDateTime → "yyyy-MM-dd HH:mm:ss" 格式字符串，CREATE_TABLE 下日期字段回显 TEXT 类组件时用）。自定义：注册 Bean 并在 configurer 上填 Bean 名。

### 2.6 FormUtils ⚠️ / FormConstants ⚠️

- `FormUtils.getFormCacheById(Long)` / `FormUtils.update(Long, FormCache)`：静态缓存读写（见 §7）。业务方仅在「改了定义需要刷缓存」时调用 `update(formId, null)`。
- `FormConstants`：模板 additionalInfo 约定 key：`showSaveFormBtn`、`label-col`、`pane-list`、`css`、`js`。内置模板未消费，供自定义模板使用。

---

## 3. 组件体系与扩展

### 3.1 契约

- `Cpn<T>` 🚫 接口（`form/cpn/core/Cpn.java`）：`getCpnType()`、`parseValue(Object)→T`（DB→UI）、`getStringValue(T)→String`（UI→DB，非 String 值 JSON 化）、`httpConverter(Object)→T`（HTTP 入参→T）、`valid(T, options)`（选项合法性）、`check(CpnConfigurer)`（定义合法性）、`validatorSupports()`、`hasValidator(Validator)`、`cpnValidators()`（组件自带校验器）。
- `AbstractCpn<T>` ⭐ 抽象基类：实现了通用的 JSON 转换（借助 `afterPropertiesSet` 反射解析泛型 T）；`valid` 默认校验「值必须在 options.name 集合内」；`validatorSupports` = REQUIRED + cpnValidators 的类型 + `internalValidatorSupports()`（子类可覆写追加 LENGTH/SIZE/TEXT_NUMBER_SIZE 等）。
- **扩展自定义组件**：新建 `@Component class MyCpn extends AbstractCpn<T>`，覆写 `getCpnType()`。**前提**：类型必须在 `CpnTypeEnum` 中（枚举无法外部扩展 → 真正的「新类型」需要改模块源码，这是设计限制）。因此业务扩展实际限于：为现有枚举类型替换实现（会与内置 Bean 冲突，CpnManager 的 toMap 遇重复 key 抛异常）或覆写值转换行为。→ 结论：**自定义组件类型在不改源码前提下不可行** `[需要确认：模块作者预期的扩展路径]`，详见 docs/examples/custom-component.md。

### 3.2 CpnManager 🚫（`form/cpn/core/CpnManager.java`）

组件注册中心。`@Autowired setCpnList(Set<Cpn>)` 收集**容器内全部 Cpn Bean** 建静态 `Map<CpnTypeEnum, Cpn>`；重复初始化抛 `IllegalArgumentException("cpnMap has been init")`。`CpnManager.getCpnByType(type)` 静态查找；**未知类型返回 null**（如 SINGLE_CHECKBOX → 调用方 NPE）。业务代码不需要调用它（FormService 内部使用）。

### 3.3 CpnInstanceProcessor 🚫（`form/cpn/core/CpnInstanceProcessor.java`）

提交时单字段的「实例化处理器」：构造时完成 `httpConverter`（HTTP 值→强类型）+ `getStringValue`（→存储字符串）；`valid()` 执行选项校验 + validators 逐个校验，错误写入 BindingResult（FieldError，objectName="form"，field=字段名，message=label+校验消息）。`FormService.handle` 每字段 new 一个。业务不直接使用。

### 3.4 内置组件（21 个实现类，对应 22 个枚举值中的 21 个）

完整表格（每个组件的值类型/JSON 形态/校验/配置项/依赖）见 **`docs/api/components.md`**。要点：

- 选项类组件（SELECT/GROUP_SELECT/SEARCH_SELECT/RADIO/CHECKBOX/MULTIPLE_SELECT）选项来源两种：`options` 静态配置，或 `datasource` = sharp-meta 字典 type（查询时动态覆盖 options）。**没有远程接口选项源**。
- 附件类（FILE/SINGLE_IMAGE）值是**文件元数据 JSON**（id/url/fullName/name/extension/size/groupName/path），由前端调 sharp-fileupload `POST /documents/upload` 后回填；**同时包含 id 和 url**，不是只存 ID。Java 侧不感知 fileupload 类型。
- TABLE 是可编辑子表：值 = `List<List>`（JSON 二维数组，行内单元格都是字符串）；列定义放 `configurer.additionalInfo.columns`。
- 组件类均为 🚫 内部实现（由 CpnManager 调度），业务不 new、不注入。

---

## 4. 校验体系

**自研体系，与 Jakarta Bean Validation 无关**（后者只用于 Form/CpnConfigurer 定义实体）。完整规则表见 **`docs/api/validation.md`**。

- `Validator<T>` 🚫 接口：`valid(T)`（失败抛 IllegalArgumentException）、`getValidatorType()`、`getMessage()`。
- `AbstractValidator<T>` ⭐ 扩展基类（equals/hashCode 按类判等）。
- `ValidatorManager` 🚫：收集容器内 Validator Bean 建 `Map<ValidatorTypeEnum, Class<? extends Validator>>`（用于把 configurer.validators 的 JSON 反序列化成具体类）；REGEX 手动映射到 `CustomizeRegex`（非 Bean，带 regex/message 参数）。⚠️ POSITIVE_INTEGER 有两个实现（PositiveInteger、StringIntegerNumber），map 注册结果取决于 Set 迭代顺序。
- `ValidatorTypeEnum`（12 个）：LENGTH、POSITIVE_INTEGER、NUMBER、REQUIRED、SIZE、REGEX、TEXT_NUMBER_SIZE、DATE、TIME、EMAIL、MOBILE、DECIMAL。
- **配置方式**（JSON，存 CpnConfigurer.validators）：
  ```json
  [{"validatorType": "REQUIRED", "required": true},
   {"validatorType": "LENGTH", "min": 0, "max": 32},
   {"validatorType": "REGEX", "regex": "^[A-Z]+$", "message": "必须大写"}]
  ```
  Java 方式：`configurer.setValidatorList(List.of(new Required(), new Length(0, 32)))`。
- **触发时机**：仅服务端提交时（`FormService.post` → CpnInstanceProcessor.valid）。同时内置模板把部分规则映射为 HTML5 属性（required/maxlength/min/max/pattern）做浏览器端预校验，但**权威校验在服务端**。
- **组件适配**：每个组件只执行 `hasValidator` 通过的校验器（validatorSupports = REQUIRED + 自带 + internalValidatorSupports）。DATE/TIME/EMAIL/MOBILE/NUMBER_TEXT/CURRENCY 的格式校验是组件自带（cpnValidators），**无需也不能**再配 DATE/TIME/EMAIL/MOBILE 之外的重复项；给不支持的组件配某校验器会被静默跳过（不报错）。
- **扩展自定义校验**：`@Component class Xxx extends AbstractValidator<T>`，覆写三方法；⚠️ 新类型需要 `ValidatorTypeEnum` 有对应枚举值（同样无法外部扩展）；已有类型的自定义参数校验可用 REGEX（CustomizeRegex 支持任意 regex+message，配置级即可满足大多数场景）。详见 docs/examples/custom-validator.md。

---

## 5. HTTP API

完整参数/响应/示例见 **`docs/api/http-api.md`**。全部接口**无任何认证/权限注解**（安全性由业务工程的 Security 配置决定 `[需要确认]`）。

| Controller | 前缀 | 类型 | 接口 |
|---|---|---|---|
| `FormController` ⭐ | /forms | JSON | POST /forms（存表单头）；POST /forms/configs（Form+组件一步定义）；POST /forms/{formId}（给表单挂组件定义列表）；POST /forms/{formId}/configs（按已有 configId 数组挂接） |
| `CpnConfigurerController` ⭐ | /forms/configurers | JSON | POST /forms/configurers（批量存组件定义，返回 id 数组） |
| `AjaxInstanceController` ⭐ | /forms/ajax | JSON | GET /{formId}（表单结构+默认值 FormBO）；GET /{formId}/{instanceId}（回显）；POST /{formId}（提交，返回 Result\<String\>=新实例id）；PUT\|POST /{formId}/{instanceId}（更新）；DELETE /{formId}?ids=1,2（批量）；DELETE /{formId}/{instanceId} |
| `PageInstanceController` ⚠️ | /forms/page | **页面** | GET /{formId}[/{instanceId}]（渲染 tplName 视图）；POST /{formId}[/{instanceId}]（form-urlencoded 提交，成功→"success" 视图，校验失败→带 errors 重渲染） |

**业务方要不要自己写 Controller？** 不需要——增删改查、页面渲染都有现成接口；只有需要定制鉴权、包装业务语义时才自己写（内部调用 FormService）。

典型端到端序列（详见 http-api.md）：
`POST /forms/configs` 定义 → `GET /forms/page/{formId}` 打开页面 → 浏览器 `POST /forms/page/{formId}`（或前端 ajax `POST /forms/ajax/{formId}`）→ 服务端校验+存储 → `GET /forms/ajax/{formId}/{instanceId}` 回显 JSON。

---

## 6. 数据存取与查询

### 存储模型（关键设计）

`Form.storageStrategy` 决定数据落在哪：

| 策略 | 数据位置 | 形态 | 查询方式 |
|---|---|---|---|
| `INNER_TABLE` | sys_form_cpn_value | **EAV 纵表**：一个实例 N 行（每字段一行），value 是 String/JSON | 只能整实例读取（`FormService.getFormBO(formId, instanceId)` / `FormCpnValueDAO.selectByInstanceIdAsMap`）；按字段条件查询需自写 SQL 行转列，**本模块未提供列表/条件查询 API** |
| `CREATE_TABLE` | 业务自己的表（宽表） | 一个实例一行，列 = CpnConfigurer.name | 用业务自己的 EntityDAO 任意查询——**需要复杂查询的表单应选此策略**。要求 Form.repositoryName = 业务 EntityDAO 的 Bean 名 |
| `NONE` | 不存储 | - | 仅渲染+校验（配合 FormAdvice 自存，如注释提到的 MongoDB 场景） |

### DAO 层（全部 🚫，业务不要直接调用；均继承 sharp-database）

| DAO | 基类 | 表 | 特有方法 |
|---|---|---|---|
| `FormDAO` | EntityCodeDAOImpl\<Form,Long\>（支持按 code 存取） | sys_form | 无 |
| `FormCpnDAO` | EntityDAOImpl\<FormCpn,Long\> | sys_form_cpn_configurer | `listByFormId`（按 order_num 升序）、`deleteByFormId`（物理删除） |
| `FormCpnValueDAO` | EntityDAOImpl\<FormCpnValue,Long\> | sys_form_cpn_value | `deleteByInstanceId`（物理删除）、`selectByInstanceIdAsMap`（key=formCpnId） |
| `CpnConfigurerDAO` | EntityDAOImpl\<CpnConfigurer,Long\> | sys_form_configurer | 覆写 `protected select(Class, String sql, Map paramMap)`：对 datasource 非空的 configurer 用 DictService 填充 options。⚠️ 只有 **Map 参数查询路径**（selectByIds、select(example) 等）经过该覆写；varargs 路径（selectById）会绕过。@Resource 强依赖 sharp-meta `DictService` |

⚠️ 注意：删除均为**物理 DELETE**（TableDAOImpl.buildDeleteSql），BaseEntityInfo 虽有 is_deleted 列但 delete 路径未使用逻辑删除。

### 取到的值是什么类型？

`getFormBO(...).getPropertyList().get(i).getValue()` 是 `Cpn.parseValue` 之后的 **UI 类型**：TEXT→String、INTEGER_NUMBER→Integer、CURRENCY→BigDecimal、CHECKBOX/MULTIPLE_SELECT→List\<String\>、FILE→List\<Map\>、SINGLE_IMAGE→Map、TABLE→List\<List\>、DATE/TIME→格式化 String……（全表见 components.md）。业务取值推荐 `formBO.getData().get("字段name")`。

---

## 7. 缓存

- **实现**：`FormUtils.formCacheMap = new HashMap<>()`（静态、JVM 内、**非线程安全**、无过期、无容量上限）。缓存内容 `FormCache{form, formCpnList, configIdMap}`（Serializable）。
- **填充**：`getFormBOByIdAndInstanceId` 缓存 miss 时查库并 `FormUtils.update(formId, formCache)`。
- **使用**：每次命中后 `SerializationUtils.clone` 深拷贝再加工（防止实例态数据污染缓存）。
- **失效**：❗ **没有任何自动失效逻辑**。`FormCpnService` / `CpnConfigurerService` / `FormController` 修改定义后不碰缓存。改了表单定义后：**重启应用**，或手动 `FormUtils.update(formId, null)`（下次读取重建）。多实例部署时每台都要处理 `[集群一致性方案需要确认]`。
- **风险**：HashMap 并发读写可能死循环/丢数据（JDK17 下主要是数据竞争），高并发 + 定义变更场景需警惕。

---

## 8. 配置项

**本模块无任何 `@ConfigurationProperties`/application.yml 配置项**（源码核实，无 `@ConfigurationProperties`、无 `@Value`）。

生效前提与自动配置详情（@Bean 清单、@Conditional、组件扫描、可覆盖性、建表、前端资源契约）见 **`docs/api/configuration.md`**。

---

## 9. 异常与错误码

| 异常/响应 | 触发 | 说明 |
|---|---|---|
| `BindException`（HTTP 400，若注册了 sharp-common `ApiExceptionHandler` 则 `Result{success:false, code:400, message:"参数验证失败", data:[{field,message,rejectedValue}]}`） | post 时字段校验失败 | message 格式：`字段=被拒值,label+校验消息`；页面提交通道会重渲染表单并注入 `errors` model |
| `IllegalArgumentException`（→400 同上） | 选项值不在 options 内（"没有找到正确的选项"）、选项 label 重复（"选项不能重复"）、CpnManager 重复初始化 | |
| `NoSuchElementException`（→500） | formId/configId 不存在（`selectById(...).get()`） | |
| `ClassCastException`（→500） | 提交 JSON 的 `id` 是数字而非字符串 | FormService.handle 强转 CharSequence |
| `NullPointerException`（→500） | 使用 SINGLE_CHECKBOX 等无实现类型（CpnManager 返回 null） | |
| 模板解析异常 | tplName/默认视图名不存在、未引入 thymeleaf | 见 troubleshooting |

Result 包装（sharp-common）：`{success, code, message, data}`；code 取值 ResultCode：200 OK / 500 服务器端异常 / 400 参数验证失败 / 403 访问未授权 / 404 资源不存在 / 422 请求出现错误。若业务工程未注册 `ResultWrappedConfig`，`GET /forms/ajax/**` 返回**裸 FormBO JSON**（不包 Result）；注册了则包一层 `Result{data: FormBO}`。内置模板 JS 按「POST/PUT 显式返回 Result」编写，不受此影响。
