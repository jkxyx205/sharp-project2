# sharp-database API 参考

> 所有签名均摘自源码（`com.rick.db` 包，83 个主源文件）。推荐等级：
> ⭐ 推荐使用 / ⚠️ 特定场景使用 / ❌ 不推荐使用 / 🚫 内部 API，不允许业务代码调用 / 🗑 已废弃（源码标注 `@Deprecated`）
>
> 逐方法完整清单见 `docs/api/dao.md`；注解全属性见 `docs/api/annotations.md`；分页见 `docs/api/pagination.md`；配置见 `docs/api/configuration.md`。

## 目录

1. [实体建模与注解](#1-实体建模与注解)
2. [实体基类选择](#2-实体基类选择)
3. [DAO 定义方式](#3-dao-定义方式)
4. [CRUD API（EntityDAO）](#4-crud-apientitydao)
5. [按 code 操作（EntityCodeDAO）](#5-按-code-操作entitycodedao)
6. [批量操作](#6-批量操作)
7. [关联查询（级联语义）](#7-关联查询级联语义)
8. [原生 SQL：TableDAO](#8-原生-sqltabledao)
9. [分页与 Grid](#9-分页与-grid)
10. [条件查询构造与 SQLParamCleaner](#10-条件查询构造与-sqlparamcleaner)
11. [乐观锁与 @Version](#11-乐观锁与-version)
12. [Service 基类](#12-service-基类)
13. [Category 分类扩展](#13-category-分类扩展)
14. [代码生成器（建表）](#14-代码生成器建表)
15. [配置项](#15-配置项)
16. [方言与多数据库](#16-方言与多数据库)
17. [扩展点](#17-扩展点)
18. [support / 工具类清单](#18-support--工具类清单)
19. [Common Mistakes](#common-mistakes)

---

## 1. 实体建模与注解

包：`com.rick.db.repository`。**不是 jakarta.persistence，语义与 JPA 不同。**

| 注解 | 等级 | 可标注位置 | 一句话语义 |
|---|---|---|---|
| `@Table(value, comment, referenceColumnId)` | ⭐ | 类 | 声明实体对应表。`value` 缺省 = 类名驼峰转下划线；`referenceColumnId` 缺省 = `表实体类名下划线 + "_id"`（供他表 `@OneToMany` 默认外键名） |
| `@Id(strategy)` | ⭐ | 字段 | 主键。`strategy` 默认 `SEQUENCE`＝**应用侧雪花算法 Long**（`IdGenerator.getSequenceId()`，非数据库序列）；`IDENTITY`＝数据库自增（insert 后回填）；`ASSIGN`＝业务方自行赋值 |
| `@Column(value, updatable, nullable, comment, columnDefinition)` | ⭐ | 字段 | 列映射。`value` 缺省 = 属性名驼峰转下划线；`updatable=false` 的列不进 UPDATE 语句；`columnDefinition` 非空时 DDL 生成忽略 `nullable/comment`；PostgreSQL 下 `columnDefinition="json"/"jsonb"` 触发 `PGobject`/`::json` 自动处理 |
| `@Transient` | ⭐ | 字段 | 不映射为列。**所有非列字段必须加**，否则会被拼进 INSERT/UPDATE 导致 SQL 报错 |
| `@Version(value)` | ⚠️ | 字段 | 乐观锁版本号，行为见 [§11](#11-乐观锁与-version)。`value` 属性当前实现未读取（列名仍按 `@Column` 规则） |
| `@Embedded(columnPrefix, comment)` | ⭐ | 字段/方法 | 将嵌套对象字段**平铺为列**（属性路径变为 `field.nested`，列名可加前缀），非 JPA Embeddable 语义 |
| `@Select(value, entityClass, params, cascadeSelect, nullWhenParamsIsNull🗑)` | ⚠️ | 字段 | 字段值来自一段自定义 SQL：`entityClass=Void`（默认）时 `value` 是完整 SQL；指定 `entityClass` 时 `value` 拼在该实体的 `SELECT 列 FROM 表` 之后。`params` 格式 `"参数名@属性路径,..."`；任一参数为 null → **不查库，字段直接置 null**。对列表结果**逐行执行**（N+1），字段类型为 Collection 时取列表，否则取唯一行（多行抛 `IncorrectResultSizeDataAccessException`） |
| `@ManyToOne(value, updatable, comment, cascadeSelect=true, cascadeSave=false, referencedColumnName)` | ⭐ | 字段 | 多对一。字段本身映射为列（默认列名 `属性名下划线_id`），写入时取引用实体的 id；查询默认批量级联加载完整对象。`referencedColumnName` **已声明但当前实现未读取** |
| `@OneToMany(mappedBy, joinColumnId, oneToOne=false, cascadeSelect=true, cascadeSave=true, cascadeDelete=true, cascadeSaveItemDelete=true, cascadeSaveItemDeleteCheck=false)` | ⭐ | 字段 | 一对多（字段不映射列）。`mappedBy`＝**子实体的属性名**（非列名）；`joinColumnId`＝子表外键列，缺省用父实体 `@Table.referenceColumnId`。保存父实体时同步子表（含删除不在列表中的子行）；`oneToOne=true` 时字段是单个对象。无懒加载，查询时一次 IN 批量加载 |
| `@ManyToMany(tableName, joinColumnId, inverseJoinColumnId, cascadeSelect=true, cascadeSave=false, cascadeDelete=true, mappedBy, joinReferencedColumnName, inverseJoinReferencedColumnName)` | ⭐ | 字段 | 多对多，经中间表 `tableName`。保存 = 先删该实体的中间表行再重插；`cascadeDelete=true` 删主实体时删中间表行（**不删对端实体**）。`mappedBy/joinReferencedColumnName/inverseJoinReferencedColumnName` **已声明但当前实现未读取** |
| `@ToStringValue` | ⚠️ | 字段 | 写库前对字段值调用 `toString()`（如枚举/对象转字符串存储） |
| `@CodeFillIgnore` / `@CodeFillUncheck` | ⚠️ | 类/字段 | 配合 `EntityCodeIdFillService`：`CodeFillUncheck` 标在实体类上时，按 code 回填 id 找不到不抛 `BizException` 而返回 null。`CodeFillIgnore` 在当前代码库中**无引用点**，语义[需要确认]（注释为“忽略 fill code”） |

完整属性表、默认值、示例：`docs/api/annotations.md`。

## 2. 实体基类选择

包：`com.rick.db.repository.model`。全部 ⭐（按需选择）。均为 Lombok `@SuperBuilder`，业务实体请也用 `@SuperBuilder`。

| 基类 | 继承链 | 附带字段（列名） | 何时用 |
|---|---|---|---|
| `EntityId<ID>` | — | `@Id id`（默认 SEQUENCE 雪花） | 只要主键 |
| `EntityIdCode<ID>` | EntityId | + `code`（列 `code`，`nullable=false`，`@Length(max=32)`，`@Pattern("^[0-9a-zA-Z_#/%-]{0,}$")`） | 有外部可见唯一编号 |
| `BaseEntity<ID>` | EntityId + `BaseEntityInfoGetter` | + `@Embedded BaseEntityInfo`：`create_by`(不可更新)、`create_time`(不可更新,非空)、`update_by`、`update_time`(非空)、`is_deleted`(非空,@JsonIgnore)，另有 `@Transient createByName/updateByName` | 需要审计/逻辑删除字段的常规业务表 |
| `BaseCodeEntity<ID>` | EntityIdCode + `BaseEntityInfoGetter` | code + BaseEntityInfo | 编号 + 审计 |
| `BaseCodeDescriptionEntity<ID>` | BaseCodeEntity | + `description`（`@NotBlank`，`@Length(max=512)`） | 编号 + 描述 + 审计（字典类表） |

**审计字段不是框架无条件自动填充的**：`EntityDAOImpl` 只写实体上的值。只有当应用把 `ExtendTableDAOImpl` 注册为 `TableDAO` Bean（利用 `@ConditionalOnMissingBean(TableDAO.class)` 覆盖，范例 sharp-test `TestConfig`）时，insert/update 才自动填 `create_by/create_time/update_by/update_time/is_deleted`（`getUserId()` 默认返回 `1L`，**必须继承覆写为真实用户**），并且 delete 变为逻辑删除、单表 SELECT 自动追加 `is_deleted = false`。

> ⚠️ **`ExtendTableDAOImpl` 的启动时序陷阱**：其 `tableNameDAOMap` 无初始值，直到 `ApplicationReadyEvent` 才填充，而 insert/delete 路径直接解引用它 → **该事件之前的任何写入（`@PostConstruct`、`afterPropertiesSet`、`ContextRefreshedEvent`、`ApplicationRunner`/`CommandLineRunner`）都会 NPE**；select 路径不受影响，故启动期只读不报错，易误判。详见 `docs/troubleshooting.md` 第 27 条。

辅助 DTO（⚠️，非 `@Table` 实体）：`IdValue`（`id` + `@Transient description`，equals 按 id）、`IdCodeValue`（`id`+`code`+`@Transient description`，equals 按 code）——用于 `@Select`/嵌入映射的轻量引用值。`BaseEntityInfoGetter` 是 `getBaseEntityInfo()` 接口。`DatabaseType` 为枚举：`MySQL5, MySQL8, PostgreSQL, Oracle10g, Oracle11c, SQLServer2012, SQLite`。

## 3. DAO 定义方式

**结论（取证自 sharp-test 与下游模块）**：业务方**写一个 `@Repository` 类继承实现类**，不是继承接口（接口无自动代理机制，不存在 Spring Data 式的“interface extends EntityDAO 自动生成实现”）。

真实标准写法（sharp-test `UserDAO`、sharp-fileupload `DocumentDAO`、sharp-formflow `FormCpnValueDAO`）：

```java
@Repository
public class UserDAO extends EntityDAOImpl<User, Long> {
}
```

code 实体（sharp-test `IdCardDAO`、sharp-formflow `FormDAO`）：

```java
@Repository
public class IdCardDAO extends EntityCodeDAOImpl<IdCard, Long> {
}
```

其他被证实的方式：

| 方式 | 证据 | 说明 |
|---|---|---|
| `@Bean` 注册 DAO（无 `@Repository`） | sharp-meta `MetaServiceAutoConfiguration#dictDAO()` | 自动配置模块内使用 |
| 注入 `EntityDAOSupport`，`getEntityDAO(Pet.class)` | sharp-test `PetService` | 无专属 DAO 类的实体；实例由框架构造并注册为单例 Bean（名为实体名驼峰+`DAO`）。⚠️ 该动态实例不经过 Spring 注解注入生命周期，依赖 `ValidatorHelper` 的方法（如 `patch()`）在此实例上不可用 |
| `sharp.database.entity-base-package` 包扫描 | `EntityDAOSupport#init()`（`@PostConstruct`） | 扫描 `@Table` 类并预注册上述动态 DAO，供级联查询使用 |
| 继承 Category DAO | sharp-test `CodeDescriptionDAO` | 见 [§13](#13-category-分类扩展) |

等级：接口 `EntityDAO`/`EntityCodeDAO`/`TableDAO` ⭐；`EntityDAOImpl`/`EntityCodeDAOImpl` ⭐（仅作父类/由框架实例化，**不要手动 new**）；`TableDAOImpl` 🚫（通过 `TableDAO` Bean 使用）；`EntityDAOSupport` ⭐；`EntityDAOManager` 🚫（静态注册表，框架级联查询内部机制）；`JdbcTemplateCallback<T>` ⚠️（自定义行映射回调，`TableDAO.select(sql, paramMap, callback)` / `GridService.query(...)` 用）。

DAO 子类可覆写的 protected 钩子（`EntityDAOImpl`）：
- `itemDeletedCheckCallback(EntityDAO referenceDAO, Collection<ID> deletedIds)`：`@OneToMany(cascadeSaveItemDeleteCheck=true)` 时子行被删除前回调（sharp-test `UserDAO` 有覆写样例）。
- `handlerReferenceListBefore(EntityDAO, List<?> entities, String refColumnName, Object refValue)`：`@OneToMany` 级联保存前处理子表列表。
- `select(Class<E> clazz, String sql, Map<String,Object> paramMap)`：所有“Map 参数查询”的汇聚点（sharp-formflow `CpnConfigurerDAO` 覆写它给结果补充字典 options）。

## 4. CRUD API（EntityDAO）

接口 `com.rick.db.repository.EntityDAO<T, ID>`（⭐）。参数上的 `@NotNull/@NotEmpty/@NotBlank` 在 DAO 为 Spring Bean（类级 `@Validated`）时生效，违反抛 `ConstraintViolationException`。`insert/update/patch/insertOrUpdate` 的实体参数带 `@Valid`，级联校验实体约束。

### 查询（均带级联加载，除非名字含 WithoutCascade）

| 方法 | 返回 | 语义/注意 |
|---|---|---|
| `Optional<T> selectById(ID id)` | 不为 null；可 empty | **多行匹配抛 `IncorrectResultSizeDataAccessException`**。级联加载引用 |
| `List<T> selectByIds(Collection<ID> ids)` | 非 null | ⚠️ 条件硬编码 `id IN (:ids)`（主键列名不是 `id` 的表不可用，下同） |
| `<S> Optional<S> selectById(ID id, String columnName, Class<S> clazz)` | 可 empty | 只查单列（列名），不级联 |
| `<S> Map<ID,S> selectByIds(Collection<ID> ids, String columnName, Class<S> clazz)` | 非 null | id→列值 |
| `<S> Optional<S> selectById(ID id, SFunction<T,S> function)` | 可 empty | 属性方法引用（sharp-common `SFunction`）；实现硬编码 `id = ?` |
| `<S> Map<ID,S> selectByIds(Set<ID> ids, SFunction<T,S> function)` | 非 null | |
| `<K,V> Map<K,V> selectForKeyValue(String columns, String condition, Map/Object.../T example)` | 非 null，LinkedHashMap | `columns` 必须是**两列**（`"code, id"`），第 1 列为 key 第 2 列为 value |
| `List<T> selectAll()` | 非 null | 全表（级联） |
| `List<T> select(String condition, Object... args)` | 非 null | condition 是 WHERE 之后片段，`?` 占位 |
| `List<T> select(Map<String,Object> paramMap)` | 非 null | **不定参动态查询**：以全部列构造 `col = :prop AND ...`，经 `SQLParamCleaner` 剔除 null/空串条件；Map key = 实体属性名。这是 DAO 上**唯一走 cleaner** 的查询（空参自动剔除仅此处 + Grid 路径） |
| `List<T> select(String condition, Map<String,Object> paramMap)` | 非 null | `:name` 命名参数，**直接绑定（不走 cleaner）**：condition 中每个命名参数必须在 Map 有值（缺 key 抛 `IllegalArgumentException: No value registered for key`），null 绑定为 NULL；Map 多余的 key 被忽略 |
| `List<T> select(T example)` | 非 null | 样例查询：转全属性 Map 后走上面的 `select(Map)` cleaner 路径 → **非 null 属性**成为等值条件；`select(null)` = selectAll |
| `List<T> select(String condition, T example)` | 非 null | example 全属性转 Map + 自定义 condition，**不走 cleaner**（null 属性绑定为 NULL） |
| `List<T> select(T example, Predicate<String> nullColumnPredicate)` | 非 null | 同上，但 predicate 命中的 null 列生成 `col IS NULL` 条件 |
| `List<T> selectWithColumns(String columns, String condition, Object... args)` | 非 null | 指定查询列（列名） |
| `<E> List<E> select(Class<E> clazz, String columns, String condition, ...)` | 非 null | 投影到任意类（`NestedRowMapper` 按属性名映射，json 列自动反序列化） |
| `<E> List<E> selectWithoutCascade(...)` 系列 | 非 null | 不触发级联；`@ManyToOne` 字段得到只含 id 的实体桩（经 `IdToEntityConverterFactory`） |
| `void cascadeSelect(List<T> list)` | — | 对已有列表手动补级联加载（`EntityDAOSupport.select(Class,sql,params)` 内部即用它） |
| `Optional<T> selectByIdWithoutCascade(ID id)` | 可 empty | ⚠️ 实现硬编码 `id = ?` |

### 存在/计数

| 方法 | 返回 | 注意 |
|---|---|---|
| `boolean exists(ID id)` / `exists(String condition, Object.../Map/T example)` | boolean | 实现为 `SELECT 1 ... LIMIT 1`，⚠️ `LIMIT` 未经方言转换，Oracle/SQLServer 不适用 |
| `long count(String condition, Object.../Map/T example)` | long | `SELECT count(*)`；condition 可为 null/空（全表计数） |

### 写入

| 方法 | 返回 | 语义 |
|---|---|---|
| `T insert(T entity)` | 传入实体（已回填 id） | 强制 INSERT；id 为 null 且策略 SEQUENCE → 雪花 id；IDENTITY → 数据库自增回填；**id 已有值 → 按该值插入**。`EntityCodeDAOImpl` 重写：code 重复抛 `BizException("编号已经存在")` |
| `T update(T entity)` | 传入实体 | id 必须非 null（`Assert`）。**全列更新**：实体 null 属性会把库中列更新为 NULL。带 `@Version` 校验（§11） |
| `T patch(T entity)` | 传入实体 | **部分更新**：仅更新实体上非 null 且 updatable 的属性（逐属性校验约束）。依赖 `ValidatorHelper` Bean |
| `T insertOrUpdate(T entity)` | 传入实体 | id == null → insert；否则 update。`EntityCodeDAOImpl` 重写：id 为 null 但 code 已存在 → 回填 id 走 update |
| `Collection<T> insertOrUpdate(Collection<T> entityList)` | 传入集合 | 循环单条 insertOrUpdate（含级联），非 JDBC batch |
| `T insertOrUpdate(Map<String,Object> paramMap)` | 新实体实例 | Map key=属性名；保存后把 id 写回 paramMap |
| `int[] insertOrUpdate(List<Map<String,Object>> paramMap)` | `new int[0]` | 🗑 源码标注 `// TODO`，**未实现，不要使用** |
| `int update(String columns, String condition, Object... args)` | 影响行数 | `columns`＝**列名**逗号串（如 `"name, age"`），生成 `SET name = ?, age = ?`；args 顺序＝列顺序＋condition 参数 |
| `int update(String columns, String condition, Map<String,Object> paramMap)` | 影响行数 | 生成 `SET col = :属性名`；**Map key 必须是对应列的实体属性名**（含 `baseEntityInfo.xxx` 形式），condition 的命名参数也从此 Map 取 |
| `int update(String columns, String condition, T example)` | 影响行数 | 从 example 取属性值 |
| `int updateWithPropertyNames(String propertyNames, String condition, T example)` | 影响行数 | `propertyNames`＝**属性名**逗号串，自动转列名 |
| `int updateById(String columns, ID id, Object.../Map/T example)` | 影响行数 | 同上，条件固定为主键 |
| `int updateByIdWithPropertyNames(String propertyNames, ID id, T example)` | 影响行数 | |
| `int updateByIds(String columns, Collection<ID> ids, Map/T example)` | 影响行数 | ⚠️ 硬编码 `id IN (:ids)` |
| `int updateByIdsWithPropertyNames(...)` | 影响行数 | |
| `int[] batchUpdate(String columns, String condition, List<Object[]> paramsList)` | 每行影响数 | JDBC batch；每组参数顺序＝列顺序＋condition 参数 |
| `int deleteById(ID id)` / `int deleteByIds(Collection<ID> ids)` / `int deleteAll()` | 影响行数 | 触发 `@OneToMany/@ManyToMany` 级联删除（按注解开关）；`deleteByIds` 硬编码 `id IN (:ids)` |
| `int delete(String condition, Object.../Map)` | 影响行数 | 同上；**condition 为空 = 删全表**，调用前必须校验 |
| `Collection<T> insertOrUpdateTable(Collection<T> entityList)` | 传入集合 | “子表整表同步”：删除不在 list 中的所有行（全表范围！），再逐条 insertOrUpdate |
| `Collection<T> insertOrUpdateTable(Collection<T> entityList, String refColumnName, Object refValue)` | 传入集合 | ✅ **范围保存**：删除限定在 `refColumnName = refValue` 范围内（等价于五参版传 `deleteItem=true, deletedIdsConsumer=null`）。曾忽略范围参数，已修复 |
| `Collection<T> insertOrUpdateTable(Collection<T> entityList, boolean deleteItem, Consumer<Collection<ID>> deletedIdsConsumer)` | 传入集合 | deleteItem=true 时先回调“将被删除的 id”，再删 + 保存 |
| `Collection<T> insertOrUpdateTable(Collection<T> entityList, String refColumnName, Object refValue, boolean deleteItem, Consumer<Collection<ID>> deletedIdsConsumer)` | 传入集合 | ✅ 正确用法：删除限定在 `refColumnName = refValue` 范围内 |
| `T insertWithoutCascade(T)` / `updateWithoutCascade(T)` / 集合版本 / `insertOrUpdateWithoutCascade(Collection<T>)` | 实体/集合 | 跳过级联；集合版走 JDBC batch（IDENTITY 策略批量插入回填 id） |

### 元数据/杂项

| 方法 | 说明 |
|---|---|
| `TableMeta getTableMeta()` | ⭐ 取实体元数据：表名、列名↔属性名映射、idMeta、getSelectSQL() 等（`TableMeta` 本身 🚫 不要自行构造） |
| `TableDAO getTableDAO()` | 取底层 TableDAO |
| `Map<String,Object> entityToMap(T entity)` | 实体转 Map（列值 + getter 补充） |

## 5. 按 code 操作（EntityCodeDAO）

接口 `EntityCodeDAO<T, ID> extends EntityDAO<T, ID>`（⭐），实现 `EntityCodeDAOImpl<T extends EntityIdCode<ID>, ID>`。实体须继承 `EntityIdCode`/`BaseCodeEntity`/`BaseCodeDescriptionEntity`。

| 方法 | 返回 | 说明 |
|---|---|---|
| `Optional<T> selectByCode(String code)` | 可 empty | 级联加载；多行抛 `IncorrectResultSizeDataAccessException` |
| `List<T> selectByCodes(Collection<String> codes)` | 非 null | |
| `<S> Optional<S> selectByCode(String code, String columnName, Class<S> clazz)` | 可 empty | 单列 |
| `<S> Map<String,S> selectByCodes(Collection<String> codes, String columnName, Class<S> clazz)` | 非 null | code→列值 |
| `<S> Optional<S> selectByCode(String code, SFunction<T,S> function)` | 可 empty | 属性引用 |
| `<S> Map<String,S> selectByCodes(Set<String> codes, SFunction<T,S> function)` | 非 null | |
| `Optional<ID> selectIdByCode(String code)` | 可 empty | 只要 id |
| `List<ID> selectIdsByCodes(Collection<String> codes)` | 非 null | 按 code 集合查 id 列表。曾有命名参数不匹配 bug（条件 `:codes` vs Map key `"code"`），**已修复**，可正常使用 |
| `Map<String,ID> selectCodeIdMap(Collection<String> codes)` | 非 null | code→id |
| `Optional<T> selectByCodeWithoutCascade(String code)` | 可 empty | |

覆写行为：`insert` 在 code 已存在时抛 `BizException("编号已经存在")`；`update` 在 code 被**其他** id 占用时同样抛出；`insertOrUpdate` 先按 code 回填 id 再决定 insert/update。

## 6. 批量操作

- **JDBC batch（高性能，无级联）**：`insertWithoutCascade(Collection<T>)`、`updateWithoutCascade(Collection<T>)`、`insertOrUpdateWithoutCascade(Collection<T>)`（按 id 是否为 null 拆成前两批）、`batchUpdate(columns, condition, paramsList)`、`TableDAO.batchInsert(...)`（返回生成的主键列表）。
- **循环单条（有级联/校验）**：`insertOrUpdate(Collection<T>)`。
- IN 条件超过 1000 个值：查询经 `SQLParamCleaner` 自动拆成 `OR IN(...)` 组；`TableDAO.deleteIn` 自动分批删除；`deleteNotIn` 的 NOT IN **超过 1000 直接抛 `RuntimeException`**。

## 7. 关联查询（级联语义）

**全部是“主查询完成后按引用字段批量补查”，没有懒加载、没有 SQL JOIN**（`@Select` 例外：逐行执行）：

- 触发条件：`select*(...)`（不带 WithoutCascade）且实体有 `cascadeSelect=true` 的 `@ManyToOne/@OneToMany/@ManyToMany` 或 `@Select` 字段。
- `@ManyToOne`：收集所有行的外键 id → 一次 `IN` 查询 → 回填完整对象。
- `@OneToMany`：一次 `joinColumnId IN (父ids)` 查询子表 → 按外键分组回填 List（`oneToOne=true` 回填单个对象，无匹配为 null）。
- `@ManyToMany`：查中间表 `(joinColumnId IN 父ids)` → 再查对端实体 → 回填 List。
- `@Select`：**每行一次查询**（列表 N 行 = N 次），列表结果内若元素是 `EntityId` 实体且 `cascadeSelect=true` 还会继续级联。
- 防环机制：同一线程一次顶层 select 期间（`EntityDAOManager` 的 ThreadLocal 栈），已加载实体会被复用，避免无限递归级联。
- 写级联：`insertOrUpdate(entity)` 时按 `@ManyToOne(cascadeSave=true)`（先存父引用的对象）、`@OneToMany(cascadeSave=true)`（同步子表，`cascadeSaveItemDelete=true` 时删除不在列表中的子行——删除前可经 `cascadeSaveItemDeleteCheck=true` 触发 `itemDeletedCheckCallback`）、`@ManyToMany`（重建中间表行；`cascadeSave=true` 才保存对端实体本身）处理。
- 删级联：`delete*` 时 `@ManyToMany(cascadeDelete=true)` 删中间表行；`@OneToMany(cascadeDelete=true)` 且 `cascadeSaveItemDelete=true` 删子表行。
- **一对一主键关联特例**（`@OneToMany(oneToOne=true, joinColumnId="id", mappedBy="id")`，如 sharp-test `User.idCard`）：insert 时框架会把它当 update 处理，需要在 Service 中对子实体手动 `insert`（取证：sharp-test `UserService.insertOrUpdate` 注释与代码）。

关联完整示例：`docs/examples/relation.md`。

## 8. 原生 SQL：TableDAO

接口 `com.rick.db.repository.TableDAO` ⭐（Bean 注入；实现 `TableDAOImpl` 🚫 勿 new）。用于任意 SQL/任意表（无需实体）。

| 方法 | 说明 |
|---|---|
| `List<Map<String,Object>> select(String sql, Object... args / Map paramMap)` | 每行一个 Map（列名→值） |
| `<E> List<E> select(Class<E> clazz, String sql, ...)` | 映射到类（复杂对象走 JSON 反序列化，`PGobject` 自动取 value） |
| `<K,V> Map<K,V> selectForKeyValue(String sql, ...)` | 两列 SQL → Map |
| `Optional<Map<String,Object>> / Optional<E> selectForObject(...)` | 期望单行；多行抛 `IncorrectResultSizeDataAccessException` |
| `<E> List<E> select(String sql, Map paramMap, JdbcTemplateCallback<E> callback)` | 自定义 RowMapper |
| `boolean exists(String sql, ...)` | ⚠️ 拼接 `" LIMIT 1"`，Oracle/SQLServer 不适用 |
| `int update(String tableName, String columnsCondition, String condition, Object.../Map)` | `columnsCondition` 是完整的 `col = ?, col2 = :p` 串。`track-if-has-update=true` 时先查库比对，无变化返回 0 |
| `int[] batchUpdate(...)` | JDBC batch |
| `int deleteIn / deleteNotIn(String tableName, String deleteColumn, Collection<?> values)` | IN 自动分批（>1000）；NOT IN >1000 抛异常 |
| `int delete(String tableName, String condition, ...)` | condition 为空 = 删全表 |
| `int insert(String tableName, String columnNames, Object... args / Map paramMap)` | 列名逗号串；参数个数与列数不符抛 `IllegalArgumentException`；底层 `SimpleJdbcInsert` |
| `List<Object> batchInsert(String tableName, String columnNames, String columnsCondition, List<Object[]> paramsList)` | 返回生成的主键列表 |
| `Number insertAndReturnKey(String tableName, String columnNames, Map params, String... idColumnName)` | 自增主键回填 |
| `void updateRefTable(String refTableName, String keyColumn, String guestColumn, Object keyInstance, Collection<?> guestInstanceIds)` | 中间表差量同步（删除多余 + 插入新增） |
| `void execute(String sql)` | DDL/任意语句 |
| `<T> T execute(ConnectionCallback<T> action)` | ⚠️ 从 DataSource 取**新连接**（非 Spring 事务连接），异常被吞（printStackTrace）返回 null |
| `NamedParameterJdbcTemplate getNamedParameterJdbcTemplate()` | 逃生舱 |

异常：SQL 错误按 Spring `DataAccessException` 体系原样抛出（`BadSqlGrammarException`、`DuplicateKeyException` 等），模块未包装自定义异常。

## 9. 分页与 Grid

详细文档：`docs/api/pagination.md`。要点：

- **请求参数**：`page`（默认 1）、`size`（默认 15，`-1` = 不分页查全部，上限强制 1000）、`sidx`（排序列）、`sord`（asc/desc）。常量见 `PageModel.PARAM_*`。
- **入口选择**：
  - `GridService` Bean（⭐）：`query(sql, pageModel, params[, clazz|JdbcTemplateCallback][, countSQL])` → `Grid<T>`。
  - `GridUtils` 静态门面（⭐，自动配置注入）：`list(sql, params[, countSQL, sortableColumns...])`，params 里直接混含 page/size/sidx/sord。
  - `GridHttpServletRequestUtils`（⚠️ Web 层）：`list(sql, request[, extendParams, countSQL])` 从 request 自动取参。
  - `AbstractTableGridService`（⭐ 报表场景）：子类实现 `getListSQL()`，可选覆写 `getCountSQL()`/`getSummarySQL()`；`list(params|request)`、`summary(...)`。`DefaultTableGridService` 是它的字符串构造版。
- **total 自动计算**：未传 `countSQL` 时框架生成 `SELECT COUNT(*) FROM (<原 SQL 去掉 order by>) temp`（`AbstractDialect.formatSqlCount`）。**不需要业务写 count SQL**，仅当默认包装性能差时才传自定义 `countSQL`。
- **返回结构** `Grid<T>`：`page`、`pageSize`、`records`（总条数）、`totalPages`、`rows`、`additionalInfo`（query 路径不设置，为 null；仅 `Grid.emptyInstance` 初始化空 Map）。records==0 时直接返回 `Grid.emptyInstance(size)`（page=1, totalPages=0）。
- **排序白名单**：`GridUtils.list` 默认把请求里的 `sidx` 自身当白名单（= 不设防）；需要限制时显式传 `sortableColumns`。`sidx/sord` 拼接进 SQL，存在注入面，见 CLAUDE.md 禁止行为。
- **模糊查询**：SQL 里写 `col LIKE :param`，cleaner 自动改写为方言化的 `UPPER(col) LIKE '%…%'` 包含匹配并转义用户输入的 `%`/`_`。
- `QueryModel`（⚠️）：`QueryModel.of(requestMap)` 把请求 Map 拆成 `PageModel` + params。`PaginationHelper.limitPages(total, displayPage, activePage)`（⚠️）：UI 页码窗口计算，返回 `startPage/endPage` Map，与查询无关。

## 10. 条件查询构造与 SQLParamCleaner

`SQLParamCleaner`（`com.rick.db.repository.support`）等级 ⚠️（业务方一般不直接调）。

**生效范围（源码仅三处调用）**：`EntityDAO.select(Map paramMap)`（单参全列动态查询）、`GridService.query`（所有分页/Grid 查询）、`GridUtils.numericObject`。**其余 DAO/TableDAO 方法不走 cleaner**——`:name` 直接由 `NamedParameterJdbcTemplate` 绑定：命名参数缺 key 抛异常、null 绑定为 NULL、LIKE 不改写（模糊需自带 `%` 值或用 cleaner 路径）。

`static FormatParam formatSql(String srcSql, Map<String,Object> params)`（返回 record `FormatParam(String formatSql, Map<String,Object> formatMap)`）行为规则：

1. 参数为 null 或空白字符串 → **整个条件被剔除**（连带多余的 AND/OR/括号）；`formatSql(sql, params, isSetIsNull=true)` 则改写为 `col IS NULL`。
2. `IN (:coll)`：集合/数组/逗号串展开为 `IN (:name0, :name1, ...)`（null 元素剔除；空集合 → 条件剔除）；展开后超过 1000 个自动拆 `(col IN (...) OR col IN (...))`。
3. `LIKE :name` → 方言化包含匹配（`UPPER(col) LIKE CONCAT('%',UPPER(:name),'%') escape ...`），值中 `%`/`_` 自动转义。**大小写不敏感的 contains 语义**。
4. 枚举参数 → `EnumUtils.getCode()`；`Instant` → `Timestamp`；`String[]` → 逗号连接。
5. `${name}` 模板变量 → **原样字符串替换**（注入风险，勿放用户输入）。
6. `:obj.field` 形式 → 归一化为 `:objField`；`::json` 等 PostgreSQL cast 后缀在解析前被去掉。
7. SQL 中残留的未匹配命名参数 → 替换为 `''`。
8. 🗑 带外置 `formatMap` 出参的 `formatSql` 重载已 `@Deprecated`。

**业务方拼条件的注意**：condition 片段永远用 `?`（配 args）或 `:name`（配 Map）；不要字符串拼接值；`EntityDAO.select(Map)`/Grid SQL 中每个条件写成 `col = :prop` 即可白得“空参剔除”。

## 11. 乐观锁与 @Version

实体字段标 `@Version`（通常 `Integer`/`Long`），行为（取证 `EntityDAOImpl.insertOrUpdate0`）：

- `insert`：版本列强制写 `1`。
- `update/insertOrUpdate(带 id)`：
  - 实体版本为 null → 抛 `IllegalArgumentException("version field cannot be null")`；
  - 先 `SELECT` 库中版本，若 `dbVersion > entityVersion` → 抛 `IllegalArgumentException("version field is old")`（**没有专用的 OptimisticLockingFailureException，冲突就是 IllegalArgumentException，按消息区分**）；
  - 否则写入 `dbVersion + 1`（不是 entityVersion + 1）。
- ⚠️ **非原子**：校验是“先读后写”，UPDATE 的 WHERE 不含版本条件，读写窗口内的并发冲突检测不到。强一致场景请自行用 `update(columns, "id = ? AND version = ?", ...)` 做 CAS 并检查影响行数。
- `update(columns, condition, ...)` 等“列级更新”方法**不参与版本逻辑**。
- 当前代码库中没有实体使用 `@Version` 的下游实例（框架能力，行为以上述源码为准）。

## 12. Service 基类

- `BaseServiceImpl<D extends EntityDAO<T,ID>, T extends EntityId<ID>, ID>` ⭐：构造注入 DAO（`@RequiredArgsConstructor`，字段 `protected final D baseDAO`），**implements EntityDAO 全量委托**。业务 Service 继承它即获得全部 CRUD 方法；`getBaseDAO()` 可取 DAO。
  - 用法（sharp-test `UserService`）：`public class UserService extends BaseServiceImpl<UserDAO, User, Long> { public UserService(UserDAO baseDAO, ...) { super(baseDAO); ... } }`
  - `BaseServiceImpl.updateWithPropertyNames` 已修复：直接委托 `baseDAO.updateWithPropertyNames(propertyNames, condition, example)`，属性名→列名转换由 `EntityDAOImpl` 内部的 `propertyNamesToColumns()` 完成。Service 层与 DAO 层两条路径行为一致，任选其一。
  - 事务：**基类无任何 `@Transactional`**，业务在子类方法上加 `@Transactional(rollbackFor = Exception.class)`（sharp-test 惯例）。
- `BaseCodeServiceImpl<D extends EntityCodeDAO<T,ID>, T extends EntityIdCode<ID>, ID>` ⭐：再委托 EntityCodeDAO 的 selectByCode 系列。
- `DbUtils` ⚠️：脱离 Spring 的裸 JDBC 助手（`new DbUtils(url, user, pass)` 或 `new DbUtils(dataSource)`；`executeUpdate/executeQuery/execute`），脚本/工具场景。
- `DbScriptUtils` ⚠️：`importSQL(Connection, String|File|Reader)` 按 `;` 切分执行 SQL 脚本（跳过 `--` 注释行）——用于手工执行建表/初始化脚本，**不是自动 schema 初始化**（模块无 schema 自动迁移机制；建表走 §14 生成器或自备脚本）。当前工作区无调用点。

## 13. Category 分类扩展

解决的业务问题：**同一张 code 表按“分类”维度隔离唯一性与查询**（如 `sys_code_description` 里 MATERIAL / SALES_ORG 等多个枚举分类各自一套 code；或按动态值如工厂 `plant_code` 分组）。

- `RowCategory<T>` 接口 ⭐：实体实现 `getCategory()/setCategory(T)`。分类可以是实体自己的枚举字段（静态），也可以代理到某业务字段（动态，注释示例：`getCategory() { return plantCode; }`）。
- DAO 父类（按实体基类选择，均 ⭐）：
  - `CategoryEntityDAOImpl<T extends EntityId<ID> & RowCategory<E>, ID, E>`：无 code 唯一性约束的分组表。
  - `CategoryEntityCodeDAOImpl<T extends EntityIdCode<ID> & RowCategory<E>, ID, E>`：**code 在分类内唯一**（insert/update 查重条件 `code = ? AND category = ?`），提供 `selectByCategoryAndCode(...)` 三个重载、按 category+code 回填 id。
  - `CategoryEnumEntityDAOImpl` / `CategoryEnumEntityCodeDAOImpl`：E 限定为枚举的别名子类（存储值 = `枚举.toString()`）。
- 构造参数 `categoryColumnName` 默认 `"category"`，列名不同时子类构造传入（javadoc 示例：`super("plant_code")`）。
- 核心方法：
  - `void insertOrUpdate(E category, Collection<T> list[, boolean deleteItem, Consumer<Collection<ID>> deletedIdsConsumer])`：给每行 setCategory 后做**分类范围内**的整表同步（内部走五参 `insertOrUpdateTable`）。
  - `List<T> selectAll(E category)`：按分类查全部。
- 真实范例：sharp-test `CodeDescriptionDAO extends CategoryEnumEntityCodeDAOImpl<CodeDescription, Long, CodeDescription.CategoryEnum>`。示例文档：`docs/examples/category.md`。
- 已知编辑器噪音：Lombok `@SuperBuilder` + 多重边界泛型导致 IDE 可能标红 `builder()`，**可以通过编译**（源码 javadoc 明示）。
- 相关：`EntityCodeIdFillService`（Bean ⭐）在 fill id 时能识别 `RowCategory` 实体走 category+code 查询。

## 14. 代码生成器（建表）

`TableGenerator`（抽象类，Bean 自动按方言选型）⭐（**仅开发/测试期**）。

- 用法（取证 sharp-test `TableGeneratorTest`）：`@SpringBootTest` 中 `@Autowired TableGenerator tableGenerator; tableGenerator.createTable(User.class);`
- 行为：解析 `TableMeta` → 拼 `CREATE TABLE` DDL → `log.info` 打印 → `jdbcTemplate.execute`；同时为每个 `@ManyToMany` 创建中间表（含 `is_deleted` 与两列 UNIQUE 约束）；同线程内重复调用同一实体跳过（ThreadLocal 去重）。
- 列类型推断 `determineSqlType`（MySQL5 版）：`Long→BIGINT, Integer→INT, Short→SMALLINT, String→VARCHAR(32), Character→CHAR(1), 枚举→ENUM(codes)（getCode 返回数值则 INT）, Boolean→BIT DEFAULT b'0', BigDecimal→DECIMAL(10,2), LocalDateTime→DATETIME, Instant→TIMESTAMP, LocalDate→DATE, LocalTime→TIME, Map/List/JsonValue→TEXT(MySQL8: JSON), 实体类型→BIGINT, 其他纯对象→JSON, 兜底 VARCHAR(32)`。PostgreSQL/SQLite 各有映射（`GENERATED ALWAYS AS IDENTITY`、`TEXT` 等）。
- **字符串长度默认 VARCHAR(32)**，需要更长必须写 `@Column(columnDefinition = "VARCHAR(255)")` 之类。
- `@Table.comment/@Column.comment` 生成注释（PostgreSQL 用 `comment on` 语句）；`@Version` 列生成 `NOT NULL` 并注释“版本号”。
- 找不到 id 字段抛 `IllegalArgumentException("cannot find id field, forgot to extends BaseEntity??")`。
- Oracle/SQLServer 生成器为 TODO，自动配置回退 `MySQL5TableGenerator`（产出 MySQL DDL，别在 Oracle 上跑）。
- 无 main 方法、无 CLI 参数；`src/test/generated_tests` 目录当前为空。示例：`docs/examples/code-generator.md`。

## 15. 配置项

前缀 `sharp.database`（`SharpDatabaseProperties`，`@ConfigurationProperties`）。完整表见 `docs/api/configuration.md`。

| key | 类型 | 默认值 | 必填 | 说明 |
|---|---|---|---|---|
| `sharp.database.type` | `DatabaseType` 枚举 | `MySQL5` | 否 | 选择方言（分页/LIKE/count SQL）与建表生成器。**必须与实际数据库一致**，框架不从 DataSource 推断 |
| `sharp.database.entity-base-package` | String | 无（null） | **是** | `EntityDAOSupport.init()` 扫描 `@Table` 实体的包（多个用 `, ` 分隔，支持 `**` 通配，如 `com.rick.test.**.entity`）。**未配置启动即 NPE** |
| `sharp.database.init-database-meta-data` | boolean | `false` | 否 | true 时启动读取全库表/列/主键到静态 `DatabaseMetaData` Map（当前代码库无消费方，用途[需要确认]） |
| `sharp.database.track-if-has-update` | boolean | `false` | 否 | true 时 `TableDAO.update` 先 SELECT 比对，值无变化则跳过 UPDATE（返回 0）。副作用：每次 update 多一次查询；比较忽略 `id/create_by/create_time/update_by/update_time` |
| `sharp.database.database-product-version` | String | 无 | 否（勿配） | 启动时框架从连接元数据**回写**（`tableDAO` Bean 创建时），仅供读取 |

前提条件：应用必须自带 `spring-boot-starter-jdbc`（提供 DataSource/JdbcTemplate/NamedParameterJdbcTemplate；本模块对这些依赖是 compileOnly），PostgreSQL 还需自引 `org.postgresql:postgresql` 驱动（模块 compileOnly）。

## 16. 方言与多数据库

- 支持：MySQL5、MySQL8、PostgreSQL、Oracle10g、Oracle11c、SQLServer2012、SQLite（`DatabaseType`）。
- **选择方式：纯配置驱动**（`sharp.database.type`），启动时 `getDialect` Bean 实例化对应 `AbstractDialect` 并写入静态 `Context`；`Context.getDialect()`（public）业务可读（如判断 `getType() == DatabaseType.PostgreSQL`），`setDialect` 为包私有 🚫。
- 方言影响面：分页 SQL（LIMIT/OFFSET vs ROWNUM vs OFFSET-FETCH）、count 包装、LIKE 拼接与转义符、ORDER BY 生成、合计函数 `summaryFun`。
- **业务代码不需要为切库改写**：EntityDAO 生成的 SQL 与 Grid 分页均走方言；例外（已知限制，写 SQL 时避免依赖）：
  - `EntityDAO.exists` / `selectByIdWithoutCascade` / `TableDAO.exists` 硬编码 `LIMIT 1`（Oracle/SQLServer 会报错）；
  - PostgreSQL json 列必须 `@Column(columnDefinition="json"/"jsonb")` 声明才能正确读写（`PGobject` + `::json` 处理只对该声明生效）；
  - 业务手写 SQL 中的数据库函数（`IFNULL/NVL/CONCAT` 等）自行保证可移植。
- 新增方言扩展点：继承 `AbstractDialect` 实现 `pageSql/contactString/escapeString/getOrderBy/getType/summaryFun`，但 `SharpDatabaseAutoConfiguration.getDialect` 是硬编码 if-else 且 `DatabaseType` 是枚举——**新方言需改模块源码**，无 SPI 注册机制（`getDialect` Bean 无 `@ConditionalOnMissingBean`，也不能被业务 Bean 覆盖后同步 `Context`/`SQLParamCleaner`，⚠️ 覆盖需三处都处理）。

## 17. 扩展点

| 扩展点 | 方式 | 证据 |
|---|---|---|
| 替换 `TableDAO`（逻辑删除/审计字段自动填充） | 定义自己的 `TableDAO` Bean（通常 `new ExtendTableDAOImpl(namedParameterJdbcTemplate)`，可再继承覆写 `getUserId()`/`addInsertInfo(Map)`） | `@ConditionalOnMissingBean(TableDAO.class)`；sharp-test `TestConfig` |
| 保存后回调 | 提供 `InsertUpdateCallback` Bean（函数式：`handler(boolean insert, EntityId entity, Map<String,Object> args)`；insert 时 args key=列名，update 时 key=属性名）；现成实现 `ExtendInsertUpdateCallback`（把 args 中的审计字段回写到实体 `BaseEntityInfo`）。⚠️ 仅 `insertOrUpdate0` 路径触发且实体须是 `EntityId` 子类；`@Autowired(required=false)` 全局单例 | sharp-test `TestConfig#insertCallback` |
| DAO 行为钩子 | 子类覆写 `itemDeletedCheckCallback` / `handlerReferenceListBefore` / `protected select(Class,String,Map)` | sharp-test `UserDAO`、sharp-formflow `CpnConfigurerDAO` |
| 条件注入（如租户隔离） | 继承抽象类 `ConditionEntityCodeDAOImpl`，实现 `getMergeArgsCondition/getMergeArgs/getMergeMapCondition/getMergeMap`，delete/update 自动并入附加条件（当前无下游用例，⚠️） | 源码 |
| 报表 Grid 服务 | 继承 `AbstractTableGridService` 或 `new DefaultTableGridService(listSQL[, countSQL, summarySQL])` | 源码 |
| Web 参数转换 | 提供 `ConverterFactory` Bean 会被自动并入 `dbConversionService`；`IdToEntityConverterFactory`（id/字符串 → 只含 id 的实体桩）可加到 Web ConversionService | `SharpDatabaseAutoConfiguration#dbConversionService`、sharp-test `TestConfig` |
| 覆盖 `ValidatorHelper` | `@ConditionalOnMissingBean` + `@ConditionalOnBean(Validator.class)` | 自动配置 |

`dbConversionService`（Bean 名即 qualifier）默认注册：String→LocalDate、code→枚举、JSON 字符串→List/Map/Set/对象、LocalDateTime→Instant、`IdToEntityConverterFactory`，以及上下文中所有 `ConverterFactory` Bean。行映射（`NestedRowMapper`）与属性写入均用它做类型转换。

## 18. support / 工具类清单

| 类 | 等级 | 说明 |
|---|---|---|
| `Constants` | ⭐ | 列名常量：`id/code/description/create_by/create_time/update_by/update_time/is_deleted`、`ASC/DESC` 等，拼条件时引用避免手写 |
| `TableMeta` / `TableMeta.IdMeta` / `TableMeta.Reference` | 🚫（读 ⚠️） | 实体元数据缓存；通过 `dao.getTableMeta()` **只读**使用可以，不要构造/修改 |
| `TableMetaResolver` | 🚫 | 注解→TableMeta 解析器（框架内部；生成器也用它） |
| `SqlHelper` | 🚫 | SQL 片段拼接工具（buildSelect/buildWhere/getInsertSQL） |
| `SQLParamCleaner` | ⚠️ | 见 §10；`setDialect` 🚫（自动配置调用） |
| `CodeHelper` | ⚠️ | 复合 code 的 `join(":")/split` 工具（null→""，空段→null） |
| `EntityUtils` | ⚠️ | `copyPropertiesAndResetInfoFields(source, target)`（复制并清空 id/code/审计字段——“另存为新实体”场景）、`isEntityClass` |
| `EntityCodeIdFillService` | ⭐ | Bean；`fill(t)/fill(t, code)/fill(clazz, id, code)`：实体 id 为 null 时按 code（或 category+code）回填 id；找不到抛 `BizException("%s code %s 不存在")`，实体类标 `@CodeFillUncheck` 则返回 null |
| `InsertUpdateCallback` | ⭐ | 扩展点接口，见 §17 |
| `IdToEntityConverterFactory` | ⚠️ | Object(Long/String)→EntityId 桩转换器 |
| `DatabaseMetaData` | 🚫 | 静态表/列/主键 Map，仅 `init-database-meta-data=true` 时填充；无消费方，用途[需要确认] |
| `ConditionEntityCodeDAOImpl` | ⚠️ | 抽象条件合并 DAO，见 §17 |
| `baseinfo/ExtendTableDAOImpl` | ⭐（扩展） | 逻辑删除 + 审计字段版 TableDAO，见 §2/§17。`addIsDeletedCondition(sql)` public 可复用 |
| `baseinfo/ExtendInsertUpdateCallback` | ⭐（扩展） | 审计字段回写实体 |
| `baseinfo/SqlSingleTableChecker` | 🚫 | 判断 SELECT 是否单表无子查询（ExtendTableDAOImpl 决定是否注入 `is_deleted=false` 用） |
| `category/*` | 见 §13 | |
| `dialect/AbstractDialect` 及 7 个实现 | 实现 🚫 / 接口读 ⚠️ | 经 Bean 或 `Context.getDialect()` 获取，不要 new |
| `util/OperatorUtils` | 🗑 | 整类 `@Deprecated`，用 sharp-common `CollectionOps`（`expectedAsOptional`：空→empty，>1→抛 `IncorrectResultSizeDataAccessException`） |
| `util/PaginationHelper` | ⚠️ | UI 页码窗口计算 |
| `o.s.jdbc.core.namedparam.ParsedSqlHelper` | 🚫 | 见 ARCHITECTURE.md |

## 事务与异常速查

- **事务边界在业务 Service 层**（sharp-test 惯例 `@Transactional(rollbackFor = Exception.class)`）；DAO/TableDAO 无事务注解，单次调用 = 单条（或 batch）语句自动提交。级联保存/删除是多次 SQL，**不加事务不具原子性**。
- 异常谱系：Spring `DataAccessException` 原样透传（SQL 语法/约束/连接）；`IncorrectResultSizeDataAccessException`（期望单行返回多行）；`IllegalArgumentException`（Assert 参数校验、乐观锁 `version field cannot be null` / `version field is old`、insert 列数不匹配）；`ConstraintViolationException`（实体/参数 Bean Validation）；`BizException`（sharp-common，code 重复“编号已经存在”、code 回填失败“xxx code xxx 不存在”）；`RuntimeException`（NOT IN 超 1000、属性写入失败等）。处理建议见 `docs/troubleshooting.md`。

---

# Common Mistakes

**1. 用 JPA 心智推断注解行为**

```java
// 错误：以为 @OneToMany 懒加载、fetch = FetchType.LAZY 之类
@OneToMany(fetch = FetchType.LAZY, mappedBy = "user")  // 编译不过：没有 fetch 属性
List<Pet> petList;
// 正确：本框架注解只有文档列出的属性；级联是查询后批量加载，控制手段是
// cascadeSelect=false 或 selectWithoutCascade(...)
@OneToMany(mappedBy = "user", joinColumnId = "user_id")
List<Pet> petList;
```
原因：`com.rick.db.repository` 注解是自研体系，属性集合与 JPA 不同（无 fetch/cascade 枚举/orphanRemoval）。

**2. 非列字段忘加 `@Transient`**

```java
// 错误：score 被解析为列 score，INSERT 拼入 → BadSqlGrammarException（列不存在）
Integer score;
// 正确
@Transient
Integer score;
```
原因：`TableMetaResolver` 把所有未标 `@Transient`（且非关联/Embedded）的字段都映射为列。

**3. 把 `update(entity)` 当“只更新改动字段”**

```java
// 错误：user 从前端来，age=null → 库中 age 被清成 NULL
userDAO.update(user);
// 正确：部分更新
userDAO.patch(user);                                  // 仅非 null 属性
userDAO.updateById("name", id, Map.of("name", name)); // 或指定列
```
原因：`update(T)` 按 `getArgsFromEntity` 生成**全部** updatable 列的 SET，null 照写。

**4. 混淆 `insertOrUpdateTable` 各重载的删除范围**

```java
// ❌ 想只同步某个分类下的子表，却用了单参版 → 删除范围是全表！
dao.insertOrUpdateTable(list);

// ✅ 限定范围：三参版（refColumnName = refValue 范围内同步）
dao.insertOrUpdateTable(list, "category", category.getCode());
// ✅ 或五参版（需要拿到"将被删除的 id"回调时）
dao.insertOrUpdateTable(list, "category", category.getCode(), true, deletedIds -> {...});
// ✅ 分类实体直接用 Category DAO 的 insertOrUpdate(category, list)（内部走五参版）
```
原因：单参版 `insertOrUpdateTable(list)` 委托 `(list, true, null)`，`refColumnName` 为 null 即**无范围限定 → 全表同步**，会删掉不在 list 中的所有行。三参版曾错误地也委托到全表版本，**现已修复**为透传 `(list, refColumnName, refValue, true, null)`；若你在旧代码/旧注释里看到"三参版勿用"的提示，那是修复前的结论。

**5. 主键策略误解 / ID 未赋值**

```java
// 错误：以为 SEQUENCE 会用数据库序列建表；或以为 ASSIGN 也会自动生成
@Id(strategy = Id.GenerationType.ASSIGN)
private Long id;      // ASSIGN 下 insert 前必须自己 setId；id 为 null 时会走 IDENTITY 的
                      // insertAndReturnKey 自增回填路径，表无自增列则报错
// 正确：默认 SEQUENCE = 框架雪花 Long（insert 后回填实体），无需赋值；
// IDENTITY = 数据库自增（建表 DDL 用 AUTO_INCREMENT / GENERATED AS IDENTITY）
```
原因：`insertOrUpdate0`：id 为 null 且策略 SEQUENCE → `IdGenerator.getSequenceId()`；IDENTITY → `insertAndReturnKey` 回填；id 已有值 → 原值插入。

**6. 乐观锁冲突捕获错异常类型**

```java
// 错误：catch (OptimisticLockingFailureException e) —— 永远不会命中
// 正确
try {
    dao.update(entity);
} catch (IllegalArgumentException e) {  // "version field is old" / "version field cannot be null"
    // 版本冲突处理（提示刷新重试）
}
```
原因：`@Version` 校验抛 `IllegalArgumentException`；且校验为“先读后写”非原子，高并发下不能替代 CAS 更新。

**7. 分页 total 自己再查一遍 / 手写 LIMIT**

```java
// 错误：再写一条 count SQL、或自己在 sql 里拼 LIMIT 分页
// 正确：交给 GridService/GridUtils，records 即 total；size=-1 查全部
Grid<Map<String, Object>> grid = GridUtils.list(
        "SELECT id, name FROM t_user WHERE name LIKE :name", params, null, "id", "name");
long total = grid.getRecords();
```
原因：`GridService.query` 自动生成 count SQL 并按方言包装分页；手写 LIMIT 在 Oracle/SQLServer 方言下与框架包装叠加会出错。

**8. 信任请求里的 `sidx`**

```java
// 错误：GridUtils.list(sql, params) —— sidx 白名单=客户端自己传的值，等于不校验
// 正确：显式白名单
GridUtils.list(sql, params, null, "id", "create_time", "name");
```
原因：`sidx/sord` 由方言直接拼接进 `ORDER BY`（非绑定参数）。

**9. 误用内部实现类**

```java
// 错误
TableDAO dao = new TableDAOImpl(jdbcTemplate);        // 绕过 Bean，配置(如 ExtendTableDAOImpl 覆盖)失效
EntityDAOManager.register(MyEntity.class, myDao);     // 破坏框架注册表
ParsedSqlHelper.get(sql);                             // Spring 内部耦合，勿用
// 正确：注入 TableDAO Bean；DAO 走 @Repository 子类或 EntityDAOSupport
```

**10. 主键列不叫 `id` 却使用硬编码方法**

```java
// selectByIds / deleteByIds / updateByIds / selectByIdWithoutCascade / selectById(id, function)
// 的条件硬编码 "id IN (:ids)" / "id = ?"；@Table 自定义了主键列名时改用
dao.select(dao.getTableMeta().getIdMeta().idColumnName() + " IN (:ids)", Map.of("ids", ids));
```
原因：`EntityDAOImpl` 中这些方法未走 `idMeta.idColumnName()`（`selectById`/`deleteById` 走了）。

**11. `EntityCodeDAOImpl.insert` 当 upsert 用**

```java
// 错误：code 已存在时 insert 抛 BizException("编号已经存在")
idCardDAO.insert(idCard);
// 正确：按 code 幂等保存用 insertOrUpdate（自动按 code 回填 id 转 update）
idCardDAO.insertOrUpdate(idCard);
```

**12. `@Select` 用于大列表**

```java
// @Select 对结果集逐行执行 SQL（N 行 = N 次查询）；大列表改用 @ManyToOne（批量 IN）
// 或查询后手动批量填充
```
原因：`EntityDAOImpl.selectReference` 的 `@Select` 分支在 `for (T t : list)` 内查询。

**13. 忘记配置 `entity-base-package` / 实体不在扫描包内**

- 未配置：启动时 `EntityDAOSupport.init()` 对 null 调 `split` → NPE。
- 实体不在扫描包且无 `@Repository` DAO：该实体自身 CRUD 仍可用（有 DAO 时），但**被其他实体级联引用**时 `EntityDAOManager.getDAO(referenceClass)` 为 null → NPE。

**14. 跨方言硬编码 SQL**

```java
// 错误：tableDAO.select("SELECT * FROM t LIMIT 10")（Oracle 报错）；IFNULL/NVL 混用
// 正确：分页走 Grid/PageModel；函数用各方言都支持的写法或按 Context.getDialect().getType() 分支
```
