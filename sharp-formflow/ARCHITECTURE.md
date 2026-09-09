# sharp-formflow 架构文档

> 依据源码：sharp-formflow/src/main/java 全部 67 个文件 + resources + 根/模块 build.gradle。
> ⚠️ 本模块当前**无外部调用方**（全仓库 grep 核实），成熟度未经生产验证。

## 1. 依赖链位置

```
sharp-common ← sharp-database ← {sharp-meta, sharp-fileupload} ← sharp-formflow（最上层）
```

| 依赖 | 引入方式 | 用途（源码证据） |
|---|---|---|
| sharp-database | `implementation project(":sharp-database")` | 实体注解（@Table/@Column/@Transient）、`EntityDAOImpl`/`EntityCodeDAOImpl` 基类、BaseEntity/BaseCodeEntity、IdGenerator、GridService（自动配置条件）、TableGenerator（未被 formflow 调用） |
| sharp-meta | `implementation project(":sharp-meta")` | `DictService.getDictByType(type)`：`CpnConfigurerDAO` 把 configurer 的 `datasource`（字典 type）转成 options；`CheckBox.parseValue` 识别 `DictValue` 类型值 |
| sharp-fileupload | **Maven 坐标** `com.rick.fileupload:sharp-fileupload:3.0-SNAPSHOT`（exclude sharp-common/sharp-database） | 提供 `POST /documents/upload` 端点给前端附件/图片组件用。**Java 代码零引用**（grep 核实），集成完全发生在浏览器 JS ↔ fileupload HTTP 层，formflow 只把返回的文件元数据 JSON 当字符串存 |
| sharp-common | 传递依赖（经 database/meta） | Result/ResultUtils/ResultCode、HttpServletRequestUtils、HtmlTagUtils、JsonUtils、ClassUtils、ReflectUtils、Time2StringUtils、IdGenerator |

### ❗ fileupload 版本坐标不一致（已核实）

`sharp-formflow/build.gradle` 写死 `3.0-SNAPSHOT`，而仓库内 sharp-fileupload 模块实际版本是 `libs.versions.toml` 的 `sharp = "0.0.1-SNAPSHOT"`。根 build.gradle 仓库列表 **mavenLocal() 排第一**，且 `~/.m2/repository/com/rick/fileupload/sharp-fileupload/3.0-SNAPSHOT/` 存在（2024-08-11 产物）→ 编译/运行解析到的是**陈旧的 mavenLocal jar**，不是当前源码。后果：/documents/upload 行为可能与当前 sharp-fileupload 源码不一致；升级 fileupload 源码不会影响 formflow。**处理**：业务工程显式声明正确版本覆盖，或修正 formflow build.gradle（需模块 owner 同意）。

## 2. 分层结构与职责

```
config/        FormFlowServiceAutoConfiguration（入口，条件装配）
               FormServiceConfiguration（@ComponentScan + 2 个 @Bean）
form/
  controller/  FormController、CpnConfigurerController        —— 定义态 JSON API
  controller/instance/ AjaxInstanceController（JSON）、PageInstanceController（Thymeleaf 页面）—— 运行态
  service/     FormService（核心门面）、FormCpnService/CpnConfigurerService（定义态）、
               FormAdvice（生命周期 SPI）、CpnValueConverter（值转换 SPI）、
               FormUtils（静态缓存）、FormConstants（模板 key 约定）、
               bo/FormBO（运行态视图对象）、model/FormCache、convert/DateTimeToStringConverter
  cpn/core/    Form、CpnConfigurer、FormCpn、FormCpnValue（实体）；
               Cpn/AbstractCpn（组件契约）、CpnManager（注册分派）、CpnInstanceProcessor（提交期处理）
  cpn/         21 个内置组件实现（@Component）
  valid/core/  Validator/AbstractValidator、ValidatorManager、ValidatorTypeEnum
  valid/       13 个校验规则实现
  dao/         FormDAO、FormCpnDAO、FormCpnValueDAO、CpnConfigurerDAO（全部继承 sharp-database）
resources/
  META-INF/spring/…AutoConfiguration.imports + spring.factories（新旧两种注册都有）
  templates/tpl/form.html、templates/success.html（Thymeleaf）
  static/ jquery.form2json.js、ajaxfileupload.js、editable-table/*、plugins/bootstrap-datepicker/*
```

## 3. 完整数据流

### 3.1 定义态（设计表单）

```
POST /forms/configs {form, configs:[CpnConfigurer…]}
  → FormController.formCpnMapping
  → FormCpnService.saveOrUpdateByConfigurer(Form, configurers)   [@Transactional]
      1. formDAO.insertOrUpdate(form)                    → sys_form
      2. cpnConfigurerDAO.insertOrUpdate(configurers)    → sys_form_configurer（回填 id）
      3. 按集合顺序生成 FormCpn(formId, configId, orderNum=i)
         （configId 已关联过则沿用旧 FormCpn.id）
      4. formCpnDAO.deleteByFormId + insertOrUpdate      → sys_form_cpn_configurer（物理删重建）
```
备选：先 `POST /forms/configurers` 存组件库拿 id，再 `POST /forms/{formId}/configs` 传 id 数组挂接。
❗ 定义变更**不会**失效 FormUtils 缓存。

### 3.2 运行态（渲染）

```
GET /forms/page/{formId}[/{instanceId}]（页面） 或 GET /forms/ajax/{formId}[/{instanceId}]（JSON）
  → FormService.getFormBO → getFormBOByIdAndInstanceId
      1. FormUtils.getFormCacheById(formId)  miss → 查 sys_form + sys_form_cpn_configurer
         + sys_form_configurer(selectByIds，datasource→DictService 填充 options) → 写缓存
      2. SerializationUtils.clone(formCache)（深拷贝，防污染）
      3. 有 instanceId 时按策略取值：
         INNER_TABLE → formCpnValueDAO.selectByInstanceIdAsMap(instanceId)（key=formCpnId）
         CREATE_TABLE → getBean(form.repositoryName, EntityDAO).selectById + entityToMap
                        + 列名→属性名双写进 valueMap
      4. FormAdvice 钩子：init → (CREATE_TABLE) beforeGetInstance → 逐字段填充 →
         afterGetInstance → beforeReturn
      5. 每字段：cpnValueConverter(若配置).convert → Cpn.parseValue(DB值→UI类型)
         → FormBO.Property(formCpnId, name, configurer, value)；同时写 valueMap
  → 页面：model{formBO, model(name→value), query(含 readonly 标记)} → 视图 tplName（默认 "tpl/form/form"，模块内不存在！）
  → JSON：FormBO 直接序列化（若业务注册了 ResultWrappedConfig 则包 Result）
```

### 3.3 提交态（校验 + 存储）

```
POST /forms/ajax/{formId}（JSON body Map）或 POST /forms/page/{formId}（form 参数）
  → FormService.post → handle   [@Transactional]
      1. getFormBOById(formId)（走缓存，新建态）
      2. values.put("formId"); values."id" 非空字符串 → instanceId=parse(id)
         否则 INNER_TABLE 新建 → instanceId = IdGenerator.getSequenceId()
      3. 每个 Property：new CpnInstanceProcessor(property, values.get(name), bindingResult)
         - cpn.httpConverter(原始值→强类型)  - cpn.getStringValue(→存储字符串)
         - valid()：cpn.valid(选项校验) + configurer.getValidatorList() 中 hasValidator 通过的逐个 valid
         - 失败 → FieldError("form", name, 拒绝值, label+消息)
      4. 有错误 → throw BindException（整个提交回滚，不落库）
      5. FormAdvice.beforeInstanceHandle(form, instanceId, values)（可改 values）
      6. 存储：
         INNER_TABLE → formCpnValueDAO.deleteByInstanceId + insertOrUpdate(List<FormCpnValue>)
                       （全量覆盖，value=getStringValue 的 String/JSON）
         CREATE_TABLE → formAdvice.insertOrUpdate(values)==true ? 跳过
                        : getBean(repositoryName, EntityDAO).insertOrUpdate(values)（Map→实体，支持级联）
         NONE → 不存储
      7. FormAdvice.afterInstanceHandle
  → ajax 返回 Result<String>(instanceId)；page 返回 "success" 视图（BindException → 带 errors 重渲染 tplName/"form"）
```

### 3.4 查询态（回显 / 业务查询）

- 单实例回显：`GET /forms/ajax/{formId}/{instanceId}` 或 `formService.getFormBO(formId, instanceId)` → FormBO.data / propertyList[i].value（UI 强类型）。
- 列表/条件查询：**模块未提供**。INNER_TABLE（EAV）需自写 SQL 按 instance_id/form_id/config_id 行转列；CREATE_TABLE 直接用业务 EntityDAO 查自己的宽表。

### 3.5 删除

`DELETE /forms/ajax/{formId}[/{instanceId}]` → FormService.delete → FormAdvice.beforeDeleteInstance → INNER_TABLE 物理删 sys_form_cpn_value / CREATE_TABLE EntityDAO.deleteById → afterDeleteInstance。

## 4. 组件注册与分派机制

- 每个组件类是 `@Component`，`ComponentScan("com.rick.formflow.form")` 扫描注册。
- `CpnManager`（@Component）通过 `@Autowired setCpnList(Set<Cpn>)` 收集**全部** Cpn Bean，构建静态 `Map<CpnTypeEnum, Cpn>`；重复初始化抛异常（多 Spring 上下文场景会炸）。
- 分派：`CpnManager.getCpnByType(configurer.getCpnType())`，纯枚举 key 查表，O(1)。**未注册类型返回 null**（SINGLE_CHECKBOX 即如此）→ 调用方 NPE。
- 泛型解析：`AbstractCpn.afterPropertiesSet`（InitializingBean）用 `ClassUtils.getClassGenericsTypes` 反射取 T 的实际类型，供 parseValue 做 JSON→对象转换。**手动 new 组件不会执行该逻辑**。
- `CpnConfigurer`（实体，别与 SPI 混淆）= 组件的配置数据；`CpnInstanceProcessor` = 提交期把「配置 + 原始 HTTP 值」实例化为「校验 + 存储字符串」的执行器，每字段每请求 new 一个。
- 线程安全：cpnMap 初始化后只读 → 安全；组件自身无状态 → 安全。

## 5. 校验机制

- `ValidatorManager`（@Component）注入 `Set<Validator>`（全部 @Component 校验器 Bean），`afterPropertiesSet` 建静态 `Map<ValidatorTypeEnum, Class<? extends Validator>>`；REGEX 手动 put CustomizeRegex（它不是 Bean，因为要携带实例级 regex/message 参数）。
- `CpnConfigurer.getValidatorList()`：把落库的 `validators`（Set\<Map\> JSON）按 `validatorType` 查 ValidatorManager 得到 Class，Jackson 反序列化为带参校验器；再并入组件 `cpnValidators()`（Date→DateRegex 等自带格式校验）。
- 提交期 `CpnInstanceProcessor.valid()`：先组件选项校验，再逐个校验器（仅 `cpn.hasValidator(v)` 通过的），失败抛 IllegalArgumentException → 收集为 FieldError → BindException。
- ⚠️ POSITIVE_INTEGER 有 2 个实现（PositiveInteger\<Integer\> 与 StringIntegerNumber\<String\>），validatorMap 后写覆盖，取决于 Set 迭代顺序，**反序列化目标类不确定** `[实际生效者需要确认]`。
- 前端：tpl/form.html 把 Required→`required`、Length.max→`maxlength`、Size.min/max→`min/max`、Mobile→`pattern` 映射为 HTML5 属性 + Bootstrap needs-validation；校验器 message 渲染进 `.invalid-feedback`。**服务端才是权威**。

## 6. 缓存与一致性

`FormUtils.formCacheMap`：静态 `HashMap<Long, FormCache>`（JVM 内）。
- 写：仅 FormService 缓存 miss 时。
- 读：每次命中后 SerializationUtils.clone 深拷贝（FormCache/CpnConfigurer 均 Serializable）。
- 失效：**无自动失效**；定义变更（FormCpnService/CpnConfigurerService/Controller）不触碰缓存 → 必须重启或 `FormUtils.update(formId, null)`。
- 并发：HashMap 非线程安全，读写并发存在数据竞争。
- 多实例部署：各 JVM 各自缓存，无广播机制 `[集群方案需要确认]`。

## 7. 自动配置生效条件全貌

```
AutoConfiguration.imports / spring.factories（双注册，Boot 2/3 均兼容）
  → FormFlowServiceAutoConfiguration
      @ConditionalOnSingleCandidate(GridService.class)   ← sharp-database 自动配置
      @AutoConfigureAfter(SharpDatabaseAutoConfiguration.class)   （其前提：单候选 DataSource）
      @Import(FormServiceConfiguration)
          @ComponentScan("com.rick.formflow.form")
            → 4 Controller + FormService/FormCpnService/CpnConfigurerService
            + 4 DAO + CpnManager + ValidatorManager + 21 组件 Bean + 12 校验器 Bean
          @Bean dateTimeToStringConverter（无条件）
          @Bean formAdvice（@ConditionalOnMissingBean → 业务提供任意 FormAdvice Bean 即替换默认空实现）
```

- **Controller 注册答案**：`FormServiceConfiguration` 的 `@ComponentScan("com.rick.formflow.form")` 负责（AutoConfiguration 只是 Import 它）。**业务方只加 Gradle 依赖 + 满足前提（web 应用、DataSource→GridService、DictService Bean），4 个 Controller 自动生效**，无需 @Import/扫描。
- 可覆盖 Bean：仅 `formAdvice`（@ConditionalOnMissingBean）。Service/Controller/DAO/组件均无条件注册，**不可覆盖**；同类型再注册会冲突（CpnManager toMap 重复 key 抛 IllegalStateException）。
- **无 @ConfigurationProperties、无任何 yml 配置项**。
- 硬前提：sharp-meta 的 `DictService` Bean（CpnConfigurerDAO @Resource，缺失 → 启动失败）；页面功能需业务自加 `spring-boot-starter-thymeleaf`（全仓库无此依赖，已 grep 核实）；模块所有 Spring/Servlet 依赖都是根 build.gradle 的 compileOnly，运行时由业务工程提供。

## 8. 数据存储模型（EAV vs 宽表）

**两种都支持，由 Form.storageStrategy 选择**——这是使用本模块最关键的决策：

| | INNER_TABLE（EAV） | CREATE_TABLE（宽表） |
|---|---|---|
| 数据位置 | sys_form_cpn_value，每字段一行：(instance_id, form_cpn_id, form_id, config_id, value:String/JSON) | 业务自己的表，一实例一行，列对应 CpnConfigurer.name |
| 建表成本 | 零（用模块 4 张表） | 需业务建表 + 实体 + EntityDAO Bean，并把 Bean 名写进 Form.repositoryName |
| 查询能力 | 只能按 instanceId 整取；条件查询要行转列 SQL，无索引友好性 | 业务 EntityDAO 全能力（条件、分页、JOIN） |
| 写入 | deleteByInstanceId + 批量 insert（全量覆盖） | `entityDAO.insertOrUpdate(Map)`（Map key 混入 formId/id，由 DAO 做 Map→实体） |
| 类型 | 一律 String（JSON 序列化） | 实体列的真实类型；回显经 CpnValueConverter/parseValue 桥接 |
| 适用 | 低频查询、结构多变、快速上线的登记表 | 需要报表/检索的核心业务数据 |

依据：`FormService.handle`（L255-280）、`getFormBOByIdAndInstanceId`（L110-136）、`Form.StorageStrategyEnum`。
⚠️ CREATE_TABLE 未配 repositoryName 时：读路径 valueMap 为空、写路径**静默不存储**（MapDAO 分支被注释）。NONE 策略只校验不存（配合 FormAdvice 自存，源码注释提到 mongoDB 文档场景）。

## 9. 扩展点汇总

| 扩展点 | 方式 | 备注 |
|---|---|---|
| 表单生命周期 | 实现 `FormAdvice` 注册 Bean，Form.formAdviceName = Bean 名 | 默认空实现 @ConditionalOnMissingBean |
| 值转换 | 实现 `CpnValueConverter<K,V>` 注册 Bean，configurer.cpnValueConverterName = Bean 名 | 内置 dateTimeToStringConverter |
| 自定义校验 | `@Component` 继承 `AbstractValidator<T>` | ⚠️ ValidatorTypeEnum 无对应值则无法经 JSON 配置反序列化（枚举不可外部扩展）；配置级自定义优先用 REGEX |
| 自定义组件 | `@Component` 继承 `AbstractCpn<T>` | ⚠️ 同样受 CpnTypeEnum 封闭限制；替换内置类型的实现会与内置 Bean 在 CpnManager.toMap 冲突。**不改模块源码无法新增组件类型** |
| 自定义页面 | Form.tplName 指向业务自己的 Thymeleaf 模板 | model 契约：formBO/model/query/errors，见 docs/api/configuration.md |
| 自定义存储 | CREATE_TABLE + FormAdvice.insertOrUpdate 返回 true 完全接管 | 或 NONE + afterInstanceHandle 自存 |

## 10. 设计约束与已知限制（均有源码依据）

1. fileupload 坐标 3.0-SNAPSHOT vs 0.0.1-SNAPSHOT 不一致（见 §1）。
2. `CpnTypeEnum.SINGLE_CHECKBOX` 无实现类 → getCpnByType 返回 null → NPE。
3. 枚举封闭：CpnTypeEnum/ValidatorTypeEnum 不可外部扩展，组件/校验扩展受限。
4. PageInstanceController 默认视图名 `"tpl/form/form"`（GET）与 `"form"`（POST 校验失败）**在模块 templates 中不存在**（实际只有 `tpl/form`、`success`）→ 不设 tplName 必渲染失败。
5. tpl/form.html 引用 `/plugins/ajaxfileupload.js`，但静态文件在 `static/ajaxfileupload.js`（根路径）→ 内置模板附件上传 JS 404。
6. FormUtils 缓存无失效、非线程安全（见 §6）。
7. 提交 values 的 `"id"` 必须字符串，否则 ClassCastException（FormService.handle L212）。
8. `delete(formId, Long[])` 恒返回 1；FormCpnValueDAO/FormCpnDAO 的 delete 是物理删除。
9. CpnConfigurer.name `updatable=false`：字段名建后不可改。
10. CREATE_TABLE 缺 repositoryName 静默丢数据；tableName 字段全链路未使用（读写路径均被注释）。
11. POSITIVE_INTEGER 双实现注册歧义（见 §5）。
12. 无列表/分页/条件查询 API；EAV 数据业务查询需自写 SQL。
13. 无任何认证/鉴权注解；4 个 Controller 自动暴露在 /forms/**，业务必须自行做安全控制。
14. 每次 getFormBO 深克隆整个 FormCache（SerializationUtils.clone），大表单高频访问有性能开销。
15. `CpnConfigurerService.findById`（selectById varargs 路径）绕过字典 options 填充覆写（见 API.md §2.3）。
