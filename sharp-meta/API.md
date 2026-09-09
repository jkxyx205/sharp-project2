# sharp-meta API 文档

所有签名均取自 `sharp-meta/src/main/java` 源码（30 个文件全部核对）。推荐等级：

```
⭐ 推荐使用 / ⚠️ 特定场景使用 / ❌ 不推荐使用 / 🚫 内部 API，不允许业务代码调用 / 🗑 已废弃（源码中无任何 @Deprecated 类）
```

包结构总览：

| 包 | 内容 |
|---|---|
| `com.rick.meta.dict.service` | `DictService`(⭐)、`DictServiceImpl`(🚫)、`DictUtils`(⚠️)、`DictDOSupplier`(⭐扩展点) |
| `com.rick.meta.dict.entity` | `Dict`(⭐ 实体) |
| `com.rick.meta.dict.model` | `DictType`(⭐ 约束注解)、`DictValue`(⭐)、`DictProperties`(⚠️ 配置类) |
| `com.rick.meta.dict.dao` | `DictDAO`(⚠️) |
| `com.rick.meta.dict.convert` | `ValueConverter`(⭐ SPI)、`DictConverter`、`ArrayDictConverter`、`BoolConverter`、`LocalDateTimeConverter`、`SqlDateConverter`、`SqlTimestampConverter` |
| `com.rick.meta.props.service` | `PropertyService`(⭐)、`PropertyServiceImpl`(🚫)、`PropertyUtils`(⚠️) |
| `com.rick.meta.props.model` | `KeyValueProperties`(⚠️ 配置类) |
| `com.rick.meta.props.dao.dataobject` | `PropertyDO`(🚫 现行代码无有效引用) |
| `com.rick.meta.config` | `MetaServiceAutoConfiguration`(🚫) |
| `com.rick.meta.config.validator` | 8 个校验器类，全部 🚫（框架内部，业务只用 `@DictType` 注解） |

---

# 一、字典查询

## 1.1 DictService ⭐（`com.rick.meta.dict.service.DictService`）

字典查询的唯一推荐入口。接口共 7 个方法，实现类 `DictServiceImpl` 由自动配置注册，直接 `@Resource`/构造注入使用。**所有查询走内存缓存 `DictUtils.dictMap`，不查库**；缓存由启动时 `rebuild()` 全量构建。

### `List<Dict> getDictByType(String type)`

- **用途**：按字典类型取全部字典项（最常用，如构建下拉选项）。
- **参数**：`type`（String，必填，字典类型编码，如 `"UNIT"`）。
- **返回值**：`List<Dict>`，按 `sort` 升序（null sort 按 0），**不可变列表**（`ListUtils.unmodifiableList`），永不为 null；类型不存在时返回空列表。
- **异常**：`type` 为空白 → `IllegalArgumentException`（`Assert.hasText(type, "type cannot be empty")`）。
- **示例**（真实用例：sharp-formflow `CpnConfigurerDAO`）：

```java
@Resource
private DictService dictService;

List<Dict> dictList = dictService.getDictByType(cpnConfigurer.getDatasource());
List<CpnOption> options = dictList.stream()
        .map(d -> new CpnOption(d.getName(), d.getLabel()))
        .collect(Collectors.toList());
```

- **使用场景**：下拉/单选/多选组件数据源、枚举列表接口、前端字典同步。
- **不应该**：对返回的 List 做 add/remove（抛 `UnsupportedOperationException`）；把「空列表」当作异常处理（这是类型不存在的正常降级）。
- **相关**：`getDictByTypeAndName`、`DictUtils.getDict`。

### `Optional<Dict> getDictByTypeAndName(String type, String name)`

- **用途**：按类型 + 编码取单个字典项（典型：code → label 翻译）。
- **参数**：`type`（String，必填）、`name`（String，必填，字典编码，对应 `Dict.name`）。
- **返回值**：`Optional<Dict>`；查不到为 `Optional.empty()`，不抛异常。
- **异常**：任一参数空白 → `IllegalArgumentException`。
- **示例**：

```java
String label = dictService.getDictByTypeAndName("UNIT", "EA")
        .map(Dict::getLabel)
        .orElse("EA"); // 自行决定降级值
```

- **不应该**：直接 `.get()`（查不到抛 `NoSuchElementException`）。
- **相关**：`DictConverter.convert`（查不到抛异常的翻译版本）。

### `Map<String, List<Dict>> getDictsByCodes(Collection<String> codes)` / `getDictsByCodes(String... codes)`

- **用途**：批量按类型编码取多个字典。注意：参数语义是**字典类型 type 的集合**，不是字典项 name。
- **参数**：`codes`（Collection\<String\> 或 String...，每个元素为字典类型编码）。
- **返回值**：`Map<类型编码, List<Dict>>`；**入参为 null/空时返回 `null`（不是空 Map）**；单个类型不存在时对应 value 为空列表（value 不为 null）。
- **异常**：无。
- **示例**：

```java
Map<String, List<Dict>> dicts = dictService.getDictsByCodes("UNIT", "MATERIAL_TYPE");
if (dicts != null) { ... } // 必须判空
```

- **使用场景**：一次性给前端返回多个字典。
- **不应该**：不判 null 直接使用。
- **相关**：`getDictByType`。

### `DictProperties.Item getDictPropertyItemByType(String type)`

- **用途**：取 yml 中某字典类型的静态配置项（`dict.items` 里定义的那条 Item）。
- **参数**：`type`（String，必填）。
- **返回值**：`DictProperties.Item`，不为 null。
- **异常**：**该 type 未在 yml `dict.items` 中配置时抛 `NoSuchElementException`**（实现为 `.findFirst().get()`，源码 `DictProperties.getItemByType`）。
- **使用场景**：极少；需要拿到字典的原始 yml 定义（如 sql/map）时。
- **不应该**：用它判断字典是否存在（用 `getDictByType(...).isEmpty()`）。

### `void rebuild()` / `void rebuild(String type)`

- **用途**：刷新内存缓存。**直接改 `sys_dict` 表后必须调用**，否则查询仍是旧数据。
- **参数**：无参 = 全量重建；`type` = 只重建该类型。
- **行为细节**（源码 `DictServiceImpl.rebuild`）：
  - 全量：查 `sys_dict` 全表 + `DictDOSupplier.get()`（若注册）合并，按 type 分组、按 sort 排序写入缓存；随后处理 yml `dict.items`——**同一 type，yml 配置会覆盖 db/supplier 数据**（yml 最后 put）。
  - 单类型：先匹配 yml（命中即返回）；否则查 `sys_dict WHERE type=:type`，查到即写入；否则用 supplier 过滤该 type 写入；**三者都没有时缓存保持旧值不变**（不清空）。
  - `sys_dict` 查询异常（如表不存在）被捕获，仅打 warn 日志「sys_dict表没有创建成功！」，按空列表继续。
- **返回值**：void。**异常**：无声明异常。
- **示例**：

```java
dictDAO.insert(Dict.builder().type("UNIT").name("BOX").label("箱").sort(3).build());
dictService.rebuild("UNIT"); // 或 rebuild()
```

- **不应该**：假设它会被自动定时调用——**模块内没有任何定时/事件触发的刷新**，只能手动调。
- **并发提示**：全量 `rebuild()` 先替换 `dictMap` 引用再逐条填充，期间读线程可能读到不完整缓存；`dictMap` 是普通 `HashMap`，无同步保护。低频管理操作可接受，勿在请求路径高频调用。

## 1.2 DictUtils ⚠️（`com.rick.meta.dict.service.DictUtils`，final 静态工具类）

静态方法直接读缓存 `dictMap`。**初始化前提**：`DictServiceImpl` bean 创建时（`@PostConstruct init()` 注入静态 `tableDAO`，`afterPropertiesSet()` 调用 `rebuild()` 填充 `dictMap`）。**在 Spring 容器未启动、或 meta 自动配置未生效（无 `TableDAO` bean）时，`dictMap` 为 null，任何查询直接 NPE**。

### `static List<Dict> getDict(String key)`

同 `DictService.getDictByType` 但不做非空断言、不经过 Spring bean。`key` 不存在返回空的不可变列表；`dictMap` 未初始化抛 NPE。

### `static Optional<Dict> getDictLabel(String key, String name)`

同 `getDictByTypeAndName`（缓存中线性匹配 `Dict.name`），无参数断言。

### `static void fillDictLabel(Object obj)` ⭐（本类核心用途）

- **用途**：递归遍历任意对象（entity / Map / List / DictValue），为其中带 `@DictType` 注解的 `DictValue` 字段（含 `List<DictValue>` 字段）回填 `label`（及 `type`）。
- **参数**：`obj`（Object，可为 null，null 直接返回）。
- **返回值**：void，就地修改 `DictValue.label`。
- **回填规则**（源码逐行核对）：
  1. obj 本身是 `DictValue`：当其自身 `code` 和 `type` 字段均非空时按 `(type, code)` 回填 label。
  2. obj 是 Iterable/Map：递归处理元素/键值。
  3. obj 是普通对象：反射遍历全部字段；字段类型为 `DictValue` 且有 `@DictType` 注解时：
     - `@DictType.type()` 非空 → 从缓存回填 label + type；
     - label 仍为空且 `@DictType.sql()` 非空 → 用静态 `tableDAO.select(DictValue.class, sql, code)` 查库回填（SQL 用 `?` 位置参数接 code，结果映射 DictValue 的 code/label 列；**返回多行抛 `IncorrectResultSizeDataAccessException`**）。
  4. 字段是 `List<DictValue>`：逐个回填，**直接用 `dictType.type()`——该字段若没加 `@DictType` 注解会 NPE**（源码 `DictUtils.java` 第 129 行，无判空）。
  5. 用 `IdentityHashMap` 记录已访问对象，循环引用安全。
- **示例**（真实用例：sharp-test `ComplexModelTest`）：

```java
ComplexModel model = complexModelDAO.selectById(id).get();
DictUtils.fillDictLabel(model); // unit/materialType/categoryDictList 的 label 被填充
```

- **使用场景**：查询结果返回前端前统一补 label；不想给每个字段写 `@Select` 时的替代方案。
- **不应该**：在非 Spring 环境调用；对含 `List<DictValue>` 但没加 `@DictType` 的实体调用。
- **相关**：`@DictType` 注解、sharp-database `@Select`（另一种 label 回填方式）。

## 1.3 DictDOSupplier ⭐ 扩展点（`com.rick.meta.dict.service.DictDOSupplier`）

```java
public interface DictDOSupplier {
    List<Dict> get();
}
```

- **用途**：由应用程序在启动/rebuild 时提供字典数据（第三种来源，与 sys_dict、yml 并列）。典型：把枚举、业务表注册成字典。
- **注册方式**：实现该接口并声明为 Spring bean（`@Component`）即可，自动配置以 `@Autowired(required = false)` 注入，**最多支持一个**（单 bean 注入，多个实现会启动冲突）。
- **调用时机**：`DictServiceImpl.afterPropertiesSet()`（启动）和 `rebuild()`/`rebuild(type)` 时调用 `get()`。返回的 Dict 只需填 `type/name/label/sort`（`Dict(type,name,label,sort)` 四参构造可用）。
- **示例**（真实用例：sharp-test `DictDOSupplierImpl`，节选）：

```java
@Component
@RequiredArgsConstructor
public class DictDOSupplierImpl implements DictDOSupplier {
    private final CodeDescriptionService codeDescriptionService;

    @Override
    public List<Dict> get() {
        List<Dict> dictList = new ArrayList<>();
        // 枚举 → 字典（type = 枚举类 SimpleName）
        for (CodeDescription.CategoryEnum e : CodeDescription.CategoryEnum.values()) {
            dictList.add(new Dict(CodeDescription.CategoryEnum.class.getSimpleName(),
                    e.getCode(), e.getLabel(), e.ordinal()));
        }
        // 业务表 → 字典
        for (CodeDescription cd : codeDescriptionService.findAll()) {
            dictList.add(new Dict(cd.getCategory().name(), cd.getCode(), cd.getDescription(), cd.getSort()));
        }
        return dictList;
    }
}
```

- **不应该**：在 `get()` 里做重操作/远程调用（每次 rebuild 都执行）；返回的 Dict 不带 `type`（`rebuild` 按 `Dict::getType` 分组，null type 会 NPE）。

## 1.4 DictDAO ⚠️（`com.rick.meta.dict.dao.DictDAO`）

```java
public class DictDAO extends EntityDAOImpl<Dict, Long> {}
```

- **用途**：`sys_dict` 表的标准 CRUD DAO（能力全部继承 sharp-database `EntityDAO`：`insert/update/deleteById/selectById/selectAll/select(condition, args)` 等）。自动配置注册为 bean。
- **使用场景**：字典管理后台（增删改字典项）。
- **不应该**：用它做字典**查询**展示（`select` 查库，绕过缓存；查询一律 `DictService`）；**写入后忘记 `dictService.rebuild()`**（缓存不会自动感知）。
- 相关机制详见 sharp-database 文档。

---

# 二、字典模型

## 2.1 Dict ⭐（`com.rick.meta.dict.entity.Dict`）

`@Table(value = "sys_dict", comment = "字典表")`，继承 `BaseEntity<Long>`（sharp-database）。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | Long | 继承自 `EntityId`，`@Id` |
| `type` | String | 字典类型编码（分组 key），如 `UNIT` |
| `name` | String | 字典项编码（业务存的 code），如 `EA` |
| `label` | String | 展示名称，如 `个` |
| `sort` | Integer | 排序号，缓存内按此升序；null 视为 0 |
| `remark` | String | 备注 |
| 审计字段 | — | 继承 `BaseEntityInfo`：`createBy/createTime/updateBy/updateTime/deleted`（列 `is_deleted`） |

- **无启用/禁用字段**——「禁用」只能删行；删行是物理删除还是逻辑删除、逻辑删除的行是否还会进缓存，**取决于装配的 `TableDAO` 实现**（见下方「已知限制」表格与 Common Mistakes 第 8 条）。
- 构造：`Dict()`、`Dict(type,name,label,sort,remark)`、`Dict(type,name,label,sort)`、`Dict.builder()`。
- **`is_deleted` 行为取决于业务方装配的 `TableDAO` 实现**（本模块自身不做过滤）：

  缓存加载 SQL 为 `SELECT id, type, name, label, sort, remark FROM sys_dict WHERE type = :type ORDER BY sort`（`DictServiceImpl.SELECT_SQL`），**SQL 文本本身没有 `is_deleted = false` 条件**；它通过注入的 `TableDAO` 执行，因此实际行为分两种：

  | 装配的 TableDAO | 逻辑删除行是否进缓存 | `dictDAO.deleteById` 的实际行为 |
  |---|---|---|
  | `TableDAOImpl`（`SharpDatabaseAutoConfiguration` 默认，`@Bean @ConditionalOnMissingBean({TableDAO.class})`） | **会进缓存**（SQL 原样执行） | **物理删除** `DELETE FROM sys_dict WHERE id = ?` |
  | `ExtendTableDAOImpl`（业务方自行声明 `TableDAO` bean 覆盖；sharp-test `config/TestConfig.java` 即此配置） | **不会进缓存**——`select` 系列重载调用 `addIsDeletedCondition(sql)`，对单表查询（`SqlSingleTableChecker.isSingleTableQuery`）且 SQL 中尚无 is_deleted 条件时，在 `ORDER BY` 前插入 ` AND is_deleted = false ` | **逻辑删除** `UPDATE sys_dict SET is_deleted = true ... AND is_deleted = false`（前提：该表已在 `tableNameDAOMap` 中，该 map 于 `ApplicationReadyEvent` 由 `EntityDAOManager.getAllEntityDAO()` 填充；`sys_dict` 有 `DictDAO` 故命中） |

  → 使用本模块前**先确认工程里 `TableDAO` bean 的实际类型**，再决定「下线一个字典项」该用逻辑删除还是物理删除。两种装配下 `rebuild()` 都必须手动调用。

## 2.2 DictValue ⭐（`com.rick.meta.dict.model.DictValue`）

实体字段中承载「字典引用」的值对象：**持久化只存 `code`**，`label/type` 是 `@Transient`（sharp-database）展示/回填用。

| 字段 | 说明 |
|---|---|
| `code` | 对应 `Dict.name`。命名取 code 是为了配合 sharp-common 的 `EntityWithCodePropertyDeserializer`（JSON 传字符串时反射调 `setCode` 注入） |
| `label` | 展示名，`@Transient`，由校验器/`fillDictLabel`/`@Select` 回填 |
| `type` | 字典类型，`@Transient` |

- 构造：`DictValue()`、`DictValue(code)`、`DictValue(code, type)`；`toString()` 返回 code；`equals/hashCode` 只比较 `(code, type)`。
- **典型实体用法**（真实用例：sharp-test `ComplexModel`）：

```java
@Embedded(columnPrefix = "unit_")                                  // 持久化为 unit_code 列
@JsonAlias("unit")
@JsonDeserialize(using = EntityWithCodePropertyDeserializer.class) // JSON 可直接传 "EA"
@DictType(type = "UNIT")                                           // 校验 + label 回填标记
DictValue unit;

@Column(columnDefinition = "json")
@DictType(type = "CategoryEnum")                                   // List 字段必须加，否则 fillDictLabel NPE
private List<DictValue> categoryDictList;
```

## 2.3 @DictType 注解 ⭐（`com.rick.meta.dict.model.DictType`）

**一个注解两种身份**（这是本模块最容易误解的点）：

1. **Jakarta Bean Validation 约束注解**：`@Constraint(validatedBy = {DictDictValueValidator, DictDictValueListValidator, DictStringValidator, DictStringListValidator, DefaultDictValidator})` —— 校验字段值必须是合法字典编码，见「三、字典校验」。
2. **label 回填标记**：`DictUtils.fillDictLabel` 依据它找到 `DictValue` 字段并回填 label。

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `message` | String | `"code %s 不存在"` | 校验失败消息模板，`%s` 会被替换为非法 code（`String.format(message, code)`） |
| `type` | String | `""` | 字典类型编码，从缓存校验/回填 |
| `sql` | String | `""` | 自定义校验 SQL（`?` 接 code，恰好 1 行为合法），type 校验不通过时兜底；也用于 fillDictLabel 的 SQL 回填 |
| `groups` / `payload` | — | `{}` | Bean Validation 标准属性 |

- `@Target(ElementType.FIELD)` **只能标注字段**，不能标 getter/方法参数/类。

## 2.4 DictProperties ⚠️（`com.rick.meta.dict.model.DictProperties`）

`@ConfigurationProperties(prefix = "dict")` 配置绑定类，见「七、配置项」。业务代码一般只通过 `DictService.getDictPropertyItemByType` 间接接触它。

---

# 三、字典校验（@DictType 作为约束注解）

**约束注解就是 `@DictType` 本身**（定义于 `com.rick.meta.dict.model.DictType`，模块内没有单独的 annotation 包）。生效方式：字段标 `@DictType` + 走 Spring 管理的 `jakarta.validation.Validator` 触发（MVC `@Valid`/`@Validated`、sharp-database `EntityDAO.insert/update`（其接口参数标了 `@Valid`）、`ValidatorHelper` 等）。

## 3.1 按字段类型自动分派（源码 `validatedBy` 列表逐一核对）

| 字段类型 | 实际执行的校验器 | 校验逻辑 |
|---|---|---|
| `String` | `DictStringValidator` | 空白 → 直接通过；否则 code 必须存在于字典 |
| `List<String>` | `DictStringListValidator` | null/空 → 通过；逐元素校验，任一失败即失败 |
| `DictValue` | `DictDictValueValidator` | null 或 code 空白 → 通过；校验 code，**通过时顺带回填 `label`**（副作用） |
| `List<DictValue>` | `DictDictValueListValidator` | 同上逐元素，通过时回填各元素 label |
| **其他任意类型** | `DefaultDictValidator` | **恒返回 true —— 静默不校验！**（如把 `@DictType` 标在 Integer/枚举字段上没有任何效果） |

单项校验逻辑（`AbstractDictValidator.isValid`）：
1. `@DictType.type()` 非空 → `dictService.getDictByTypeAndName(type, code)` 命中即通过（并回填 label）。
2. 否则/未命中且 `@DictType.sql()` 非空 → `tableDAO.select(sql, new Object[]{code})`，**恰好返回 1 行**即通过，label 取结果行的 `label` 列（所以 sql 必须查出名为 `label` 的列）。
3. 都不满足 → 校验失败，消息为 `String.format(message, code)`，如 `code XXX 不存在`。失败即标准 `ConstraintViolationException`/BindingResult 流程，模块本身不抛自定义异常。

**注意**：`type` 和 `sql` 都为空（`@DictType` 裸用）时第 1、2 步都跳过 → **恒校验失败**（除非值本身为空白/null）。

## 3.2 示例（真实用例：sharp-test `ComplexModel` + `EmbeddedValue`）

```java
@DictType(type = "MATERIAL_TYPE")   // 值必须是 MATERIAL_TYPE 字典中的编码
DictValue materialType;

@DictType(type = "CategoryEnum")    // type 可以来自 DictDOSupplier 注册的枚举
DictValue categoryDict;

@DictType(type = "CategoryEnum")
private List<DictValue> categoryDictList;

// sql 兜底：字典缓存没有时查自定义 SQL（? 为 code，需返回 label 列）
@DictType(type = "MATERIAL_TYPE", sql = "SELECT name code, label FROM sys_dict WHERE type='MATERIAL_TYPE' AND name = ?")
DictValue materialType2;
```

## 3.3 校验器类清单（全部 🚫 内部 API）

| 类 | 等级 | 说明 |
|---|---|---|
| `AbstractDictValidator` | 🚫 | 公共校验逻辑基类（非抽象类，普通 class） |
| `DictStringValidator` / `DictStringListValidator` / `DictDictValueValidator` / `DictDictValueListValidator` / `DefaultDictValidator` | 🚫 | 由 Bean Validation 框架实例化调度，业务不要 new/注入 |
| `DictValidator`（校验 DictValue）、`Dict2Validator`（校验 String） | ❌ | 标了 `@Component` 但**不在 `@DictType` 的 `validatedBy` 列表中**，仓库内无任何调用点——注解校验永远不会走到它们。与 `DictDictValueValidator`/`DictStringValidator` 逻辑重复，属遗留代码（无 `@Deprecated`，故不标 🗑）。不要使用 |

**环境要求**（从代码事实推断的必要条件）：
- 校验器只有 `(DictService, TableDAO)` 有参构造、无无参构造 → 必须经 Spring 的 Validator（Spring Boot 默认 `LocalValidatorFactoryBean` + `SpringConstraintValidatorFactory` 会构造注入）触发；用原生 `Validation.buildDefaultValidatorFactory()` 会实例化失败。
- `AbstractDictValidator` 将 context 强转为 Hibernate 的 `ConstraintValidatorContextImpl` → 校验实现必须是 Hibernate Validator（Spring Boot 默认即是）。

---

# 四、字典值转换与展示（convert 包）

**解决的问题**：把存储值（字典 code、布尔、日期时间）转成展示文本（String），如导出、报表、详情展示。**SPI 接口是 `ValueConverter<C, T>`**：

```java
public interface ValueConverter<C, T> extends Serializable {
    String convert(C context, T value);   // C=上下文（DictConverter 里是字典类型），T=原始值
}
```

**分派方式**：模块内**没有**按类型自动分派/注册的机制——6 个实现各自注册为 Spring bean，调用方按需注入具体某个 Converter 使用（仓库内 sharp-meta 之外暂无调用点；sharp-formflow 的 `CpnValueConverter` 是另一个不相干的接口，勿混淆）。扩展自定义转换器 = 实现 `ValueConverter` + 声明为 bean，然后自行注入调用。

| 转换器 | 等级 | 签名 | 行为（null 一律返回 null） |
|---|---|---|---|
| `DictConverter` | ⭐ | `ValueConverter<String, Object>`，context=字典 type | value 为 null→null；String 且以 `[` 开头→委托 `ArrayDictConverter`；转字符串后为空白→`""`；否则查缓存翻译 label，**查不到抛 `IllegalArgumentException("<type> doesn't contain <value>")`** |
| `ArrayDictConverter` | ⚠️ | `ValueConverter<String, String>`，context=字典 type，value=JSON 数组字符串 | 先按 `List<String>` 解析，失败再按 `List<DictValue>` 解析取 code；逐个翻译后 `","` 拼接；**任一 code 查不到抛 `NoSuchElementException`**（内部 `.get()`） |
| `BoolConverter` | ⭐ | `ValueConverter<Object, Object>`，context 忽略 | Boolean / Number(intValue==1) / String("true"/"1" 忽略大小写) → `"是"`/`"否"`；**其他类型抛 `IllegalArgumentException`** |
| `LocalDateTimeConverter` | ⭐ | `ValueConverter<Object, LocalDateTime>` | `Time2StringUtils.format` → `"yyyy-MM-dd HH:mm:ss"` |
| `SqlTimestampConverter` | ⚠️ | `ValueConverter<Object, java.sql.Timestamp>` | `toLocalDateTime()` 后同上格式 |
| `SqlDateConverter` | ⚠️ | `ValueConverter<Object, java.sql.Date>` | `Date.toString()` → `"yyyy-MM-dd"` |

示例：

```java
@Resource
private DictConverter dictConverter;

dictConverter.convert("UNIT", "EA");            // "个"
dictConverter.convert("UNIT", "[\"EA\",\"KG\"]"); // "个,千克"（自动走 ArrayDictConverter）
```

**不应该**：把 `DictConverter` 用于「查不到就降级」的场景（它抛异常，降级场景用 `DictService.getDictByTypeAndName(...).map(Dict::getLabel)`）；给 `ArrayDictConverter` 传非 JSON 数组字符串（解析异常链不可控）。

---

# 五、系统参数（Property）

## 5.1 PropertyService ⭐（`com.rick.meta.props.service.PropertyService`）

接口只有 2 个方法：

### `String getProperty(String name)`

- **用途**：读系统参数。
- **参数**：`name`（String，参数名）。
- **返回值**：String；**name 空白返回 null；key 不存在返回 null；不抛异常、无默认值机制**（要默认值自己 `Objects.requireNonNullElse` / `StringUtils.defaultIfBlank`）。
- **数据来源**：内存缓存 `PropertyUtils.map`（启动时 yml `props.items` 先放、`sys_property` 全表后放——**同名 key 数据库值覆盖 yml 值**）。查询不访问数据库。
- **示例**：

```java
@Resource
private PropertyService propertyService;

String threshold = propertyService.getProperty("order.amount.threshold");
int t = threshold == null ? 100 : Integer.parseInt(threshold); // 类型转换/默认值由调用方负责
```

### `void setProperty(String name, String value)`

- **用途**：写系统参数（先 UPDATE `sys_property`，影响 0 行则 INSERT——upsert），并同步更新内存缓存。
- **参数**：`name`（String，必填，空白抛 `IllegalArgumentException`）、`value`（String，可为 null 写入）。
- **异常/限制**：UPDATE+INSERT 两条 SQL **非事务、无唯一约束保证**，并发写同一新 key 可能插入重复行；直接改表（不走本方法）缓存不感知，且**本模块没有 Property 缓存刷新方法**，只能重启。
- **相关**：`PropertyUtils.getProperty`（静态等价读）。

## 5.2 PropertyUtils ⚠️（`com.rick.meta.props.service.PropertyUtils`，final 静态工具类）

```java
public static String getProperty(String name)  // name 空白或不存在 → null
```

- 与 `PropertyService.getProperty` 完全同源（后者就是委托它）。适用场景：拿不到 Spring bean 的静态上下文。
- 缓存 `map` 是普通静态 `HashMap`（非并发容器），初始化在 `PropertyServiceImpl.afterPropertiesSet`；未初始化时返回 null 而**不抛 NPE**（map 有初值），这点与 `DictUtils`（dictMap 初值 null，会 NPE）不同。

## 5.3 KeyValueProperties ⚠️（`com.rick.meta.props.model.KeyValueProperties`）

**它只是 `@ConfigurationProperties(prefix = "props")` 绑定类（`Map<String,String> items`），不是 Spring `PropertySource`/`EnvironmentPostProcessor`——数据库或 yml 里的这些值不会进入 Spring Environment，`@Value("${...}")` 读不到 `sys_property` 的值**。它的唯一作用：启动时把 `props.items.*` 作为参数缓存的种子数据（见 5.1）。业务代码没有直接使用它的场景（`DictProperties`/`KeyValueProperties` 均由自动配置 `@EnableConfigurationProperties` 注册）。

## 5.4 PropertyDO 🚫（`com.rick.meta.props.dao.dataobject.PropertyDO`）

普通 `@Data` 类（name/value 两字段），非 `@Table` 实体。现行代码中仅出现在 `PropertyServiceImpl` 的注释代码里，**无有效引用**；`props/dao` 下没有 PropertyDAO。不允许业务使用（表操作全部通过 `PropertyServiceImpl` 内联 SQL + `tableDAO.selectForKeyValue`）。

---

# 六、自动配置与扩展点

## 6.1 MetaServiceAutoConfiguration 🚫（`com.rick.meta.config.MetaServiceAutoConfiguration`）

注册于 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。

- **生效条件**：`@ConditionalOnSingleCandidate(TableDAO.class)` + `@AutoConfigureAfter(SharpDatabaseAutoConfiguration.class)`。即：容器有唯一 `TableDAO` bean（依赖 `DataSource`，由 sharp-database 自动配置提供）。**条件不满足时整个模块静默不生效**，`DictUtils.dictMap` 保持 null（使用时 NPE）。
- **注册的 bean**（内部类 `MetaGridServiceConfiguration`，`@EnableConfigurationProperties({DictProperties, KeyValueProperties})`）：

| Bean 方法 | 类型 | @ConditionalOnMissingBean |
|---|---|---|
| `dictDAO()` | `DictDAO` | 无 |
| `getDictService(TableDAO, DictProperties, @Autowired(required=false) DictDOSupplier)` | `DictService`(Impl) | 无 |
| `getPropertyService(TableDAO, KeyValueProperties)` | `PropertyService`(Impl) | 无 |
| `dictConverter` / `arrayDictConverter` / `sqlDateConverter` / `sqlTimestampConverter` / `localDateTimeConverter` / `boolConverter` | 各 Converter | 无 |

- **所有 bean 都没有 `@ConditionalOnMissingBean` → 业务方不能通过声明同类型 bean 覆盖**（会 bean 定义冲突）。可替换的只有 `DictDOSupplier`（可选注入）和上游的 `TableDAO`（sharp-database 侧有 `@ConditionalOnMissingBean`）。
- 业务代码应面向接口注入 `DictService`/`PropertyService`，不要引用两个 Impl 类。

## 6.2 扩展点汇总

| 扩展点 | 方式 | 说明 |
|---|---|---|
| 自定义字典数据来源 | 实现 `DictDOSupplier` 并 `@Component` | 见 1.3；仅支持一个实现 |
| 自定义值转换器 | 实现 `ValueConverter<C,T>` 并声明 bean | 无自动分派，调用方自行注入使用 |
| 自定义字典校验数据源 | `@DictType(sql = "...")` | 不改代码即可让校验走任意 SQL |
| 覆盖 DictService/PropertyService | ❌ 不支持 | 无 `@ConditionalOnMissingBean` |

---

# 七、配置项

本模块共 2 个 `@ConfigurationProperties`，无其他配置 key：

## `dict.*`（DictProperties）

| yml key | 类型 | 默认值 | 用途 |
|---|---|---|---|
| `dict.items[n].type` | String | 无（必填，rebuild 时 `item.getType().equals(type)` 调用，null 会 NPE） | 字典类型编码 |
| `dict.items[n].map` | Map<String,String> | null | key=编码、value=名称，直接定义字典项 |
| `dict.items[n].sql` | String | null | 查询 SQL，结果两列作为 key/value（`tableDAO.selectForKeyValue`，键值都会 `String.valueOf` 转换） |
| `dict.items[n].list` | List<String> | null | 纯字符串列表，编码=名称（label 与 name 相同） |

- 同一 Item 配多个来源时只生效一个，优先级 **map > sql > list**（`initYml` 的 if/else-if 链）。
- 副作用/注意：yml 字典在 `rebuild()` 中**覆盖**同 type 的 db/supplier 数据；`sort` 按 map/list 的遍历顺序 0,1,2... 生成；yml 字典**同样只进缓存**，改了 yml 需重启（或调 rebuild）。

```yaml
dict:
  items:
    - type: AUDIT_STATUS
      sql: "SELECT code, name FROM audit_status"   # 两列：编码、名称
    - type: YES_NO
      map: { "Y": "是", "N": "否" }
    - type: TAGS
      list: [red, green, blue]
```

## `props.*`（KeyValueProperties）

| yml key | 类型 | 默认值 | 用途 |
|---|---|---|---|
| `props.items.<name>` | String | 空 Map | 系统参数种子值；启动时载入缓存，同名 key 被 `sys_property` 表值覆盖 |

```yaml
props:
  items:
    order.amount.threshold: "5000"
```

---

# 八、数据模型与表结构

## `sys_dict`（实体 `Dict`，`@Table("sys_dict")`）

| 列 | 来源字段 | 说明 |
|---|---|---|
| `id` | `EntityId.id`（`@Id`） | Long 主键 |
| `type` | `Dict.type` | 字典类型编码 |
| `name` | `Dict.name` | 字典项编码 |
| `label` | `Dict.label` | 展示名称 |
| `sort` | `Dict.sort` | 排序（Integer） |
| `remark` | `Dict.remark` | 备注 |
| `create_by` / `create_time` / `update_by` / `update_time` / `is_deleted` | 继承 `BaseEntityInfo`（列名按 sharp-database 命名策略映射，逻辑删除列名常量 `Constants.LOGIC_DELETE_COLUMN_NAME = "is_deleted"`） | createTime/updateTime/is_deleted 非空约束 |

- **建表**：模块内无 DDL/初始化脚本。可用 `TableGenerator.createTable(Dict.class)`（sharp-database，参考 sharp-test `TableGeneratorTest` 用法），或按上表手写。
- **初始化数据**：无内置数据。可以不建表——`getDbDictList` 捕获异常降级为空列表（warn 日志），此时字典只能来自 yml / DictDOSupplier。
- 缓存加载是否过滤 `is_deleted` 由装配的 `TableDAO` 实现决定（见 2.1 已知限制表格）；`is_deleted` 列本身必须有（`BaseEntityInfo.deleted` 标了 `nullable = false`）。

## `sys_property`（无实体映射，SQL 内联于 `PropertyServiceImpl`）

| 列 | 说明（由 INSERT/UPDATE/SELECT SQL 反推） |
|---|---|
| `name` | 参数名（`SELECT name, value FROM sys_property`；建议加唯一约束，代码 upsert 依赖 name 定位） |
| `value` | 参数值字符串 |

- `PropertyDO` 不是 `@Table` 实体，`TableGenerator` 不适用，**必须手写 DDL**；除 name/value 外模块不读写任何其他列。
- 建表脚本仓库内未找到 `[需要确认]`（是否有配套的库外初始化脚本）——功能上仅需上述两列即可工作。

---

# Common Mistakes

### 1. 非 Spring 环境 / 自动配置未生效时调用 DictUtils → NPE

```java
// ❌ 单元测试里不起 Spring 容器直接调
DictUtils.getDict("UNIT"); // NullPointerException：dictMap 为 null

// ✅ 起 Spring 上下文；或确保容器中存在唯一 TableDAO bean（DataSource 已配置）
```

原因：`dictMap` 只在 `DictServiceImpl.afterPropertiesSet()` 中赋值；meta 自动配置的前提是 `@ConditionalOnSingleCandidate(TableDAO.class)`，不满足则整个模块静默失效。`PropertyUtils` 不会 NPE（map 有初值）但恒返回 null，同样是「静默失效」。

### 2. 直接改了 sys_dict / sys_property 表，查询结果不变

```java
// ❌ 用 SQL 客户端改了 sys_dict，接口返回还是旧 label
// ✅ 字典：改表后调用
dictService.rebuild();          // 或 rebuild("UNIT") 单类型
// ⚠️ 系统参数：没有任何刷新 API，直改 sys_property 只能重启应用生效；
//    运行时改值必须走 propertyService.setProperty(name, value)（写库+更新缓存）
```

原因：两者都是启动时全量加载的内存缓存，模块内无定时刷新、无 DB 变更监听。

### 3. @DictType 标在不支持的字段类型上 → 静默不校验

```java
// ❌ Integer 字段：命中 DefaultDictValidator，isValid 恒 true，非法值照样通过
@DictType(type = "AUDIT_STATUS")
Integer status;

// ✅ 用支持的类型：String / List<String> / DictValue / List<DictValue>
@DictType(type = "AUDIT_STATUS")
String status;
```

原因：`@DictType` 的 `validatedBy` 中 `DefaultDictValidator` 是 `ConstraintValidator<DictType, Object>` 兜底，直接 `return true`。

### 4. @DictType 的 type 和 sql 都不配 → 非空值恒校验失败；List<DictValue> 字段不配 @DictType → fillDictLabel NPE

```java
// ❌ 裸注解：非空白值必然失败（无 type 查不了缓存，无 sql 查不了库）
@DictType
String code;

// ❌ List<DictValue> 字段缺注解：DictUtils.fillDictLabel 第 129 行 dictType.type() NPE
private List<DictValue> tags;

// ✅
@DictType(type = "TAGS")
private List<DictValue> tags;
```

### 5. 用 @Value 读 sys_property / props.items 里的业务参数

```java
// ❌ KeyValueProperties 不是 PropertySource，Environment 里没有这些 key
@Value("${order.amount.threshold}")  // 启动报 placeholder 无法解析（除非 yml 里恰好有同名顶层 key）
private int threshold;

// ✅
int threshold = Integer.parseInt(
    Objects.requireNonNullElse(propertyService.getProperty("order.amount.threshold"), "100"));
```

原因：`props.items.*` 只被灌进 `PropertyUtils.map` 静态缓存，从未注册进 Spring Environment；混用 `@Value` 和 `PropertyService` 读同一个名字，取到的可能是两个不同来源的值。

### 6. 用原生 Bean Validation 工厂触发 @DictType → 校验器实例化失败

```java
// ❌ 校验器只有 (DictService, TableDAO) 有参构造，Hibernate 默认工厂无法实例化；
//    且 AbstractDictValidator 强转 ConstraintValidatorContextImpl，依赖 Hibernate Validator
Validator v = Validation.buildDefaultValidatorFactory().getValidator();

// ✅ 注入 Spring 的 Validator（Boot 默认 LocalValidatorFactoryBean，会构造注入依赖）
@Resource
private jakarta.validation.Validator validator;
```

### 7. 对「查不到」的返回值假设错误

```java
// ❌ getDictsByCodes 空入参返回 null，不是空 Map
dictService.getDictsByCodes().forEach(...);           // NPE

// ❌ getDictPropertyItemByType：yml 未配置该 type 直接抛 NoSuchElementException
dictService.getDictPropertyItemByType("NOT_IN_YML");

// ❌ DictConverter/ArrayDictConverter：code 不在字典 → IllegalArgumentException / NoSuchElementException
dictConverter.convert("UNIT", "XX");

// ✅ 可能缺失的翻译用 Optional 风格
dictService.getDictByTypeAndName("UNIT", code).map(Dict::getLabel).orElse(code);
```

### 8. 假设 `is_deleted` 的过滤/删除语义，而不先确认装配的 TableDAO

```java
// ❌ 想下线一个字典项，随手写：
dictDAO.deleteById(id);
dictService.rebuild();
// 然后假设「行没了」或「行还在但被过滤了」——两种装配下结果完全相反
```

原因（均已核实源码）：`DictServiceImpl.SELECT_SQL` 文本里**没有** `is_deleted = false`，过滤与删除语义完全由注入的 `TableDAO` 实现决定：

- 默认 `TableDAOImpl`（自动配置 `@ConditionalOnMissingBean({TableDAO.class})`）：`deleteById` 是**物理** `DELETE FROM`；即使你手工把某行 `is_deleted` 置 true，该行**仍会进缓存**。
- `ExtendTableDAOImpl`（业务方自行声明 bean 覆盖，sharp-test `config/TestConfig.java` 即此配置）：`deleteById` 变成**逻辑删除** `UPDATE ... SET is_deleted = true`；`select` 会自动为单表查询追加 ` AND is_deleted = false `，故逻辑删除行**不进缓存**。

```java
// ✅ 先确认工程里 TableDAO bean 的实际类型，再选择做法：
//    - 默认 TableDAOImpl：deleteById 即物理删除，rebuild() 后生效
//    - ExtendTableDAOImpl：deleteById 是逻辑删除，rebuild() 后该行被自动过滤
// 两种情况下 rebuild() 都必须手动调用（DAO 写入不会自动触发）
```

额外注意（`ExtendTableDAOImpl`）：其 `tableNameDAOMap` 在 `ApplicationReadyEvent` 才填充，且 `getUserId()` 硬编码返回 `1L`，业务方需继承覆写才能写入真实操作人——详见 sharp-database 文档。

### 9. 绕过 DictService 自建字典查询/缓存，或硬编码字典值

```java
// ❌ 业务代码里 if ("EA".equals(unit)) ... / 自己 select sys_dict 再缓存一份
// ✅ 注入 DictService 查询；字典编码在业务侧用常量/枚举引用，label 一律从字典取
```

原因：模块的价值就是单一缓存源 + 校验 + 回填闭环；旁路查询会引入第二份不一致缓存。（注：sharp-database `@Select` 注解查 `sys_dict` 回填 label 是仓库既有模式，如 `ComplexModel.materialType`，不算旁路缓存。）

### 10. 使用 DictValidator / Dict2Validator

```java
// ❌ 这两个类不在 @DictType 的 validatedBy 列表里，注解校验永远不会调度到它们，仓库内也无调用点
// ✅ 业务只使用 @DictType 注解；校验器实现类一律视为内部 API
```
