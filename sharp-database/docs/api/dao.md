# DAO 方法完整参考

> 签名逐一摘自 `EntityDAO.java`、`EntityCodeDAO.java`、`TableDAO.java`、`EntityDAOSupport.java`、`JdbcTemplateCallback.java` 源码。
> 通用约定：
> - `condition` = WHERE 之后的 SQL 片段（不含 WHERE 关键字；null/空 = 无条件）。`?` 配 `Object... args`，`:name` 配 `Map<String,Object>`。
> - **`SQLParamCleaner` 动态清理（空参条件剔除/LIKE 改写/IN 展开）只在 `select(Map)`（单参）与 Grid 分页路径生效**；带 condition 的 Map 参数方法是直接绑定：命名参数缺 key 抛 `IllegalArgumentException: No value registered for key ...`，null 绑定为 NULL，LIKE 不改写。
> - `columns` = **数据库列名**逗号串（如 `"name, age"`）；`propertyNames` = **实体属性名**逗号串。
> - List 返回值恒非 null（可为空 List）；Optional 返回值恒非 null。
> - 名字带 `WithoutCascade` 的方法不触发关联加载；其余 `select*` 会级联（见 `docs/examples/relation.md`）。
> - 期望单行的 API（`selectById/selectByCode/selectForObject`）匹配多行时抛 `IncorrectResultSizeDataAccessException`。
> - DAO 为 Spring Bean（类级 `@Validated`）时，参数上的 `@NotNull/@NotEmpty/@NotBlank` 与实体上的 `@Valid` 生效，违反抛 `ConstraintViolationException`。

## EntityDAO<T, ID>（接口 ⭐ / 实现 EntityDAOImpl 🚫勿new，仅作父类）

### 按 id 查询

```java
Optional<T> selectById(@NotNull ID id);                              // 级联；可 empty
List<T> selectByIds(@NotEmpty Collection<ID> ids);                   // ⚠️ 硬编码 "id IN (:ids)"
<S> Optional<S> selectById(@NotNull ID id, @NotBlank String columnName, Class<S> clazz);   // 单列，不级联
<S> Map<ID, S> selectByIds(@NotEmpty Collection<ID> ids, @NotBlank String columnName, Class<S> clazz);
<S> Optional<S> selectById(ID id, SFunction<T, S> function);         // 属性引用；⚠️ 硬编码 "id = ?"
<S> Map<ID, S> selectByIds(Set<ID> ids, SFunction<T, S> function);
Optional<T> selectByIdWithoutCascade(ID id);                         // ⚠️ 硬编码 "id = ?"
boolean exists(@NotNull ID id);
```

`SFunction` 用法：`userDAO.selectById(1L, User::getName)` → `Optional<String>`。

### 条件查询

```java
List<T> selectAll();
List<T> select(String condition, Object... args);
List<T> select(Map<String, Object> paramMap);            // 动态：全列等值条件，null/空参数自动剔除；key=属性名
List<T> select(String condition, Map<String, Object> paramMap);
List<T> select(T example);                                // 样例：非 null 属性做等值条件；example=null → selectAll
List<T> select(T example, Predicate<String> nullColumnPredicate); // predicate 命中的 null 列 → "col IS NULL"
List<T> select(String condition, T example);
List<T> selectWithColumns(@NotBlank String columns, String condition, Object... args);
List<T> select(@NotBlank String columns, String condition, Map<String, Object> paramMap);

List<T> select(@NotBlank String columns, String condition, T example);

<E> List<E> select(@NotNull Class<E> clazz, @NotBlank String columns, String condition, Object... args);
<E> List<E> select(@NotNull Class<E> clazz, @NotBlank String columns, String condition, T example);
<E> List<E> select(@NotNull Class<E> clazz, @NotBlank String columns, String condition, Map<String, Object> paramMap);

boolean exists(String condition, Object... args);         // ⚠️ 实现拼 " LIMIT 1"
boolean exists(String condition, Map<String, Object> paramMap);
boolean exists(String condition, T example);
long count(String condition, Object... args);             // SELECT count(*)
long count(String condition, Map<String, Object> paramMap);
long count(String condition, T example);

<K, V> Map<K, V> selectForKeyValue(@NotBlank String columns, String condition, Map<String, Object> paramMap);
<K, V> Map<K, V> selectForKeyValue(@NotBlank String columns, String condition, Object... args);
<K, V> Map<K, V> selectForKeyValue(@NotBlank String columns, String condition, T example);
// columns 必须是两列："code, id" → Map<code, id>（LinkedHashMap 保序）
```

### 不级联查询

```java
List<T> selectWithoutCascade(String condition, Object... args);
List<T> selectWithoutCascade(String columns, String condition, Object... args);
<E> List<E> selectWithoutCascade(@NotNull Class<E> clazz, @NotBlank String columns, String condition, Object... args);
<E> List<E> selectWithoutCascade(Class<E> clazz, String columns, String condition, Map<String, Object> paramMap);
<E> List<E> selectWithoutCascade(Class<E> clazz, String columns, String condition, T example);
void cascadeSelect(List<T> list);   // 对已查出的列表手动补级联
```

### 写入（单条）

```java
T insert(@Valid @NotNull T entity);              // 强制 INSERT；code 实体 code 重复抛 BizException
T update(@Valid @NotNull T entity);              // id 必须非 null；全列更新（null 清库）；@Version 校验
T patch(@NotNull T entity);                      // 部分更新：仅非 null 且 updatable 属性；逐属性 Bean Validation
T insertOrUpdate(@Valid @NotNull T entity);      // id==null → insert，否则 update；code 实体先按 code 回填 id
T insertOrUpdate(@Valid @NotNull Map<String, Object> paramMap);  // key=属性名；保存后 id 写回 paramMap
T insertWithoutCascade(@Valid @NotNull T entity);
T updateWithoutCascade(@Valid @NotNull T entity);
```

返回：传入的实体实例（id 已回填），恒非 null。

### 写入（批量 / 列级）

```java
Collection<T> insertOrUpdate(@Valid Collection<T> entityList);            // 循环单条（含级联）
int[] insertOrUpdate(@Valid List<Map<String, Object>> paramMap);          // 🗑 TODO 未实现，恒返回 new int[0]
Collection<T> insertWithoutCascade(Collection<T> entityList);             // JDBC batch
Collection<T> updateWithoutCascade(Collection<T> entityList);             // JDBC batch
Collection<T> insertOrUpdateWithoutCascade(Collection<T> entityList);     // 按 id null 与否拆两批 batch

int update(@NotBlank String columns, String condition, Object... args);
int update(@NotBlank String columns, String condition, Map<String, Object> paramMap); // SET col=:属性名 → key 必须是属性名
int update(@NotBlank String columns, String condition, T example);
int updateWithPropertyNames(@NotBlank String propertyNames, String condition, T example);
int[] batchUpdate(@NotBlank String columns, String condition, List<Object[]> paramsList); // 每组=列序参数+条件参数
int updateById(@NotBlank String columns, @NotNull ID id, Object... args);
int updateById(@NotBlank String columns, @NotNull ID id, Map<String, Object> paramMap);
int updateById(@NotBlank String columns, @NotNull ID id, T example);
int updateByIdWithPropertyNames(@NotBlank String propertyNames, @NotNull ID id, T example);
int updateByIds(@NotBlank String columns, Collection<ID> ids, Map<String, Object> paramMap); // ⚠️ 硬编码 "id IN (:ids)"
int updateByIds(@NotBlank String columns, Collection<ID> ids, T example);
int updateByIdsWithPropertyNames(@NotBlank String propertyNames, Collection<ID> ids, T example);
```

int = 影响行数（`track-if-has-update=true` 且值未变化时 update 返回 0）。

### 子表整表同步

```java
Collection<T> insertOrUpdateTable(Collection<T> entityList);
// 全表范围：删除 id 不在 list 中的所有行，再逐条 insertOrUpdate

Collection<T> insertOrUpdateTable(Collection<T> entityList, @NotNull String refColumnName, @NotNull Object refValue);
// ✅ 范围保存：删除限定在 refColumnName = refValue 内（曾忽略范围参数等效全表删除，已修复为透传五参版）

Collection<T> insertOrUpdateTable(Collection<T> entityList, boolean deleteItem, Consumer<Collection<ID>> deletedIdsConsumer);
// 🚨 仍有缺陷：该重载委托为 (entityList, null, null, deleteItem, deletedIdsConsumer)。
//    当 deleteItem=true 且 deletedIdsConsumer 非 null 时，五参实现首段会执行
//    select(..., refColumnName + " = :refValue", Map.of("refValue", refValue))，
//    而此处 refColumnName/refValue 均为 null → 拼出 "null = :refValue"，且 Map.of 拒绝 null 值直接抛 NPE。
//    需要“将被删除的 id”回调时，请改用下面的五参版并显式传 refColumnName/refValue。

Collection<T> insertOrUpdateTable(Collection<T> entityList, @NotNull String refColumnName, @NotNull Object refValue,
                                  boolean deleteItem, Consumer<Collection<ID>> deletedIdsConsumer);
// ✅ 范围限定在 refColumnName = refValue；deleteItem=true 时先把“将被删除的 id”交给 consumer，再删除+保存
```

返回：传入的 entityList。

### 删除

```java
int deleteById(@NotNull ID id);
int deleteByIds(@NotEmpty Collection<ID> ids);   // ⚠️ 硬编码 "id IN (:ids)"
int deleteAll();                                  // = delete(null)，全表
int delete(String condition, Object... args);
int delete(String condition, Map<String, Object> paramMap);
```

触发 `@OneToMany/@ManyToMany` 级联删除（按注解开关）；注册了 `ExtendTableDAOImpl` 时，**已注册实体表**的删除自动变为 `UPDATE is_deleted = true`（逻辑删除）。

### 元数据

```java
TableMeta getTableMeta();   // 表名/列映射/IdMeta/getSelectSQL() 等，只读使用
TableDAO getTableDAO();
Map<String, Object> entityToMap(T entity);
```

### 子类可覆写的 protected 钩子（EntityDAOImpl）

```java
protected void itemDeletedCheckCallback(EntityDAO referenceDAO, Collection<ID> deletedIds);
// @OneToMany(cascadeSaveItemDeleteCheck=true) 时，级联删除子行前回调（deletedIds 非空）

protected void handlerReferenceListBefore(EntityDAO entityCodeDAO, List<?> entities, String refColumnName, Object refValue);
// @OneToMany 级联保存前处理子表列表（EntityCodeDAOImpl 用它按 code 回填子行 id）

protected <E> List<E> select(Class<E> clazz, String sql, Map<String, Object> paramMap);
// 所有 Map 参数查询的汇聚点（sharp-formflow CpnConfigurerDAO 覆写做结果加工）

protected T insertOrUpdate0(T entity, boolean insert);            // 单条保存主流程
protected T insertOrUpdate0(T entity, boolean insert, boolean cascade);
protected <S> String obtainColumnName(SFunction<T, S> function);  // 属性引用 → 列名 AS "属性名"
```

## EntityCodeDAO<T, ID> extends EntityDAO（接口 ⭐ / 实现 EntityCodeDAOImpl）

实体约束：`T extends EntityIdCode<ID>`。完整签名见 API.md §5。要点复述：

```java
Optional<T> selectByCode(@NotBlank String code);
List<T> selectByCodes(@NotEmpty Collection<String> codes);
<S> Optional<S> selectByCode(@NotBlank String code, @NotBlank String columnName, Class<S> clazz);
<S> Map<String, S> selectByCodes(@NotEmpty Collection<String> codes, @NotBlank String columnName, Class<S> clazz);
<S> Optional<S> selectByCode(@NotBlank String code, SFunction<T, S> function);
<S> Map<String, S> selectByCodes(@NotEmpty Set<String> codes, SFunction<T, S> function);
Optional<ID> selectIdByCode(@NotBlank String code);
List<ID> selectIdsByCodes(@NotEmpty Collection<String> codes);   // ✅ 可用（曾有 :codes vs key "code" 命名参数 bug，已修复）
Map<String, ID> selectCodeIdMap(@NotEmpty Collection<String> codes);
Optional<T> selectByCodeWithoutCascade(@NotBlank String code);
```

覆写语义：`insert`（code 已存在 → `BizException("编号已经存在")`）、`update`（code 被其他 id 占用 → 同上）、`insertOrUpdate`（id 为 null 时按 code 查库回填 id → 转为 update）、`insertOrUpdateTable` 五参版（批量按 code 回填 id）。

## TableDAO（接口 ⭐ / 实现 TableDAOImpl 🚫）

任意 SQL / 任意表操作，无需实体。完整签名与语义见 API.md §8。参数注意：

- `sql` 为完整语句；命名参数经 `NamedParameterJdbcTemplate` 绑定（安全），`?` 经 `JdbcTemplate` 绑定。
- `update(tableName, columnsCondition, condition, ...)`：`columnsCondition` 是**完整 SET 片段**（`"name = ?, age = ?"` 或 `"name = :name"`）；PostgreSQL json 参数需自带 cast（接口 javadoc 原文：`?::json`）。
- `insert(tableName, columnNames, ...)`：列数与参数数不符抛 `IllegalArgumentException("列名数量与参数数量不一致")`。
- `execute(ConnectionCallback<T>)`：从 DataSource 取**新连接**（不参与 Spring 事务），SQLException 被 printStackTrace 后返回 null——事务性操作勿用。

## EntityDAOSupport（Bean ⭐）

```java
public <T, ID> EntityDAO<T, ID> getEntityDAO(@NonNull Class<?> entityClass);
// 取/建实体 DAO：EntityIdCode 子类 → EntityCodeDAOImpl，否则 EntityDAOImpl；
// 注册进 EntityDAOManager 并 registerSingleton（Bean 名 = 实体名驼峰 + "DAO"）。
// ⚠️ 动态实例不经 Spring 注解注入（validatorHelper/dbConversionService 为 null），patch() 等受限

public <T> List<T> select(Class<T> clazz, String sql, Map<String, Object> params); // 自定义 SQL + 自动级联
public <T> List<T> select(Class<T> clazz, String sql, Object... args);
public <T> T select(T t);            // 对单对象补级联（null 安全）
public <T> List<T> select(List<T> list);  // 对列表补级联
```

`@PostConstruct init()`：按 `sharp.database.entity-base-package`（`, ` 分隔，支持 `**`）扫描 `@Table` 类并预注册 DAO。

## JdbcTemplateCallback<T>（⚠️ 函数式接口）

```java
List<T> select(NamedParameterJdbcTemplate jdbcTemplate, String sql, Map<String, Object> paramMap);
```

用于 `TableDAO.select(sql, paramMap, callback)` 与 `GridService.query(..., callback, countSQL)` 自定义行映射，例：

```java
tableDAO.select(sql, params, (jdbcTemplate, s, p) ->
        jdbcTemplate.query(s, p, (rs, i) -> new Object[]{rs.getObject(1), rs.getObject(2)}));
```

## EntityDAOManager（🚫 内部）

静态注册表 `register/getDAO/getAllEntityDAO` + 级联防环 ThreadLocal。框架级联查询、`EntityCodeIdFillService`、`ExtendTableDAOImpl.onApplicationReady` 依赖它；业务代码不要调用 `register`，`getDAO` 仅在写通用框架代码时使用。
