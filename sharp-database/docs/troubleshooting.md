# 故障排查（异常 → 触发条件 → 业务含义 → 处理方式）

> 全部条目依据源码（sharp-database 83 个主文件）与 sharp-test/下游模块真实用法整理。

## 启动期

### 1. 启动 NPE：`EntityDAOSupport.init()`
- **现象**：应用启动失败，`@PostConstruct` 处 NullPointerException。
- **触发**：未配置 `sharp.database.entity-base-package`（源码 `getEntityBasePackage().split(",\\s+")` 对 null 调用）。
- **处理**：配置扫描包（支持 `**` 通配，多包用 `, ` 分隔）。该配置为**必填**。

### 2. 自动配置整体不生效（找不到 TableDAO/GridService Bean）
- **触发**：容器中没有唯一/`@Primary` 的 `DataSource`（`@ConditionalOnSingleCandidate(DataSource.class)`）；或应用未引入 `spring-boot-starter-jdbc`（本模块对 Spring JDBC 是 compileOnly，不传递）。
- **处理**：单数据源 + starter-jdbc + 驱动。多数据源场景本模块不支持自动配置。

### 3. `RuntimeException: java.sql.SQLException`（tableDAO Bean 创建时）
- **触发**：启动读取 `DatabaseProductVersion` 时连接失败（数据源配置错误/库不可达）。
- **处理**：检查 `spring.datasource.*`。

## 写入期

### 4. `BadSqlGrammarException`：Unknown column 'xxx'
- **触发**：实体字段未加 `@Transient`，被 `TableMetaResolver` 当作列拼进 INSERT/UPDATE/SELECT；或实体与表结构不同步（改了字段没改表）。
- **处理**：非持久化字段一律 `@Transient`；对照 `dao.getTableMeta().getColumnNames()` 与真实表结构。

### 5. `BizException: 编号已经存在`
- **触发**：`EntityCodeDAOImpl.insert`（code 已存在）或 `update`（code 被其他 id 占用）；Category 版按 `code + category` 查重。
- **业务含义**：业务级唯一性冲突（非数据库约束），HTTP 层通常直接透出提示。
- **处理**：想要“存在即更新”用 `insertOrUpdate`（自动按 code 回填 id 转 update）；确实要报错则捕获 `BizException`（sharp-common，含 `Result`）。

### 6. `IllegalArgumentException: version field cannot be null` / `version field is old`
- **触发**：实体带 `@Version` 字段：update 时版本为 null / 库中版本大于实体版本（`EntityDAOImpl.insertOrUpdate0`）。
- **业务含义**：乐观锁冲突 = `version field is old`。**没有专用异常类型**，只能按 `IllegalArgumentException` + 消息区分。
- **处理**：冲突时提示用户刷新重试；注意校验是“先读后写”非原子，强一致需求改用 `update("col, version", "id = ? AND version = ?", ...)` 自行 CAS 并检查影响行数。

### 7. `ConstraintViolationException`
- **触发**：DAO 为 Spring Bean（类级 `@Validated`）时：`insert/update/insertOrUpdate` 的 `@Valid` 实体校验失败（如 `@NotBlank name`）；或 `@NotNull id` 等参数校验失败；`patch()` 逐属性校验（`ValidatorHelper.validateProperty`）。
- **处理**：入参问题，400 类响应；不要在 DAO 层捕获吞掉。

### 8. `IllegalArgumentException: id cannot be null`
- **触发**：`update(entity)`/`patch(entity)`/`updateWithoutCascade(entity)` 时实体 id 为 null（`Assert.notNull`）。
- **处理**：新增走 `insert`/`insertOrUpdate`；更新前确保 id 已赋值。

### 9. 数据被意外清空（列变 NULL）
- **触发**：`update(entity)` 是**全列更新**，实体上为 null 的属性照写 NULL；`insertOrUpdate(entity)` 带 id 时同理。
- **处理**：部分更新用 `patch(entity)`（仅非 null 属性）或 `updateById(columns, id, ...)` 指定列。

### 10. 子表/分类表数据被大面积删除
- **触发**：调用了**单参** `insertOrUpdateTable(list)`——它是全表同步语义（`refColumnName` 为 null → 走 `deleteAll()` 或 `delete("id NOT IN (:ids)")`），会删掉不在 list 中的所有行。
- **处理**：范围保存用三参 `insertOrUpdateTable(list, refColumnName, refValue)` 或五参重载，或 `CategoryEntityCodeDAOImpl.insertOrUpdate(category, list)`；给"将被删除的 id"挂 `deletedIdsConsumer` 做日志/校验（**注意必须用五参版**，见第 28 条）。
- **历史说明**：三参版曾忽略 `refColumnName/refValue` 而等效全表删除，**现已修复**（透传五参版）。若排查的是旧版本 jar 上的问题，先确认依赖版本。

### 11. `RuntimeException: SQL_PATH_NOT_IN in的个数不能超过1000`
- **触发**：`TableDAO.deleteNotIn` 值超过 1000（NOT IN 无法分批，源码显式抛错）。
- **处理**：业务侧分批取反或改为“查全量 id → deleteIn 差集”。

### 12. `IllegalArgumentException: 列名数量与参数数量不一致`
- **触发**：`TableDAO.insert(tableName, columnNames, args...)` 列数 ≠ 参数数。

## 查询期

### 13. `IncorrectResultSizeDataAccessException`
- **触发**：期望单行的 API 命中多行：`selectById/selectByCode/selectForObject/selectByCategoryAndCode`、`@Select` 映射到非 Collection 字段时结果多行（`CollectionOps.expectedAsOptional`）。
- **处理**：数据/条件问题——确认业务上应唯一（必要时给表加唯一约束）；确需多行改用 `select(condition, ...)` 列表 API。

### 14. 级联引用 NPE（`EntityDAOManager.getDAO` 返回 null）
- **触发**：被引用实体（`@ManyToOne/@OneToMany/@ManyToMany` 的目标类、`@Select.entityClass`）既不在 `entity-base-package` 扫描范围，也没有 `@Repository` DAO / `getEntityDAO` 注册。
- **处理**：把实体包加入扫描，或为其声明 DAO。

### 15. 分页/排序异常
- `BadSqlGrammarException ... LIMIT`：`type` 配置为 Oracle/SQLServer 但 SQL 里（或 `exists` 实现里）出现 `LIMIT`。`EntityDAO.exists`/`TableDAO.exists` 硬编码 `LIMIT 1`，**Oracle/SQLServer 下不可用**，改 `count(...)>0`。
- 排序不生效：`sidx` 未命中白名单（`GridUtils.sortable` 忽略大小写精确匹配列名）或 `sord` 为空；MySQL 方言要求查询列含 `id`（追加 `temp_.id ASC` 稳定序）。
- 排序报 Unknown column：`sidx` 必须是**结果集列名/别名**（拼在包装查询 `SELECT * FROM (sql) temp_ ORDER BY ...` 上）。

### 16. LIKE 行为不一致：“Grid 里没加 % 也能模糊，DAO 里却不模糊”
- **机制**：`SQLParamCleaner` 仅在 **Grid/分页路径与 `EntityDAO.select(Map)`（单参）** 生效，会把 `col LIKE :p` 自动改写为 `UPPER(col) LIKE CONCAT('%',UPPER(:p),'%')`（contains、大小写不敏感，`%`/`_` 转义）。`select(condition, paramMap/args)` 等其余 DAO 方法**不改写**：LIKE 就是原生语义，值需自带 `%`。
- **处理**：DAO 条件查询自己传 `"%Rick%"`；Grid SQL 里只写 `LIKE :name` 不要自带 `%`（会被转义成字面量）。

### 16b. `IllegalArgumentException: No value registered for key 'xxx'`
- **触发**：`select(condition, paramMap)` 等直接绑定路径中，condition 里的命名参数在 Map 中缺 key（Spring `NamedParameterJdbcTemplate` 抛出）。
- **处理**：补 key（值可为 null，但等值条件将永假）；需要“空参剔除”的动态条件改用 `select(Map)`（单参样例式）或 Grid 路径，或业务侧按参数有无拼接 condition。

### 17. Map 参数查询条件“消失”
- **触发**：`select(Map)`/Grid 查询中参数值为 null 或空白字符串 → 条件被 cleaner 整体剔除（预期行为）。
- **处理**：需要“查 IS NULL”用 `formatSql(sql, params, isSetIsNull=true)` 路径（`EntityDAO.select(example, nullColumnPredicate)` 亦可为 null 列生成 `IS NULL`）；需要“空串精确匹配”则不要用命名参数动态清理路径。

### 18. PostgreSQL json 列读写失败（`cannot cast ... / PGobject`）
- **触发**：json/jsonb 列未声明 `@Column(columnDefinition = "json"/"jsonb")`——`PGobject` 包装与 `::json` cast 只对该声明生效（`EntityDAOImpl.postgresJsonHandler`、`TableMeta.appendColumnVar`）。
- **处理**：补注解；`TableDAO.update` 手写 SET 片段时按接口 javadoc 自行加 `?::json`。

### 19. `DataRetrievalFailureException: Unable to map column ...`
- **触发**：`NestedRowMapper` 列值与属性类型不匹配且非“纯对象”（无法走 JSON 反序列化）。
- **处理**：检查列类型/属性类型；复杂对象属性考虑 `columnDefinition="json"` + 实现 `JsonValue`。

### 20. `RuntimeException: Failed to set property: xxx`
- **触发**：级联回填/Map 写入时属性不可写或嵌套路径创建失败（`EntityDAOImpl.setPropertyValue`）。
- **处理**：实体保证无参构造 + setter（Lombok `@Getter@Setter@NoArgsConstructor`）；嵌套字段类型需可实例化。

## 事务与一致性

### 21. 级联保存/删除中途失败，数据不一致
- **触发**：本模块**所有类都没有 `@Transactional`**；`insertOrUpdate` 级联是多条 SQL。业务方法未加事务时各自自动提交。
- **处理**：写操作的 Service 方法一律 `@Transactional(rollbackFor = Exception.class)`（sharp-test 惯例）。同类内部 this 调用不走代理，事务不生效——拆类或注入自身代理。

### 22. `TableDAO.execute(ConnectionCallback)` 里的操作不回滚 / 返回 null
- **触发**：该 API 从 DataSource 取**新连接**（不参与 Spring 事务），SQLException 被 `printStackTrace` 后返回 null（源码明示）。
- **处理**：事务性操作改用 `getNamedParameterJdbcTemplate()` 或常规 API。

### 23. update 返回 0 但数据没变/没报错
- **触发**：`track-if-has-update=true` 时值未变化会跳过 UPDATE 返回 0；或条件未命中；或（ExtendTableDAOImpl 逻辑删除模式下）行已 `is_deleted=true`（delete/update 条件自动带 `is_deleted = false`）。
- **处理**：按场景核对配置与逻辑删除状态。

### 24. “查不到刚删的数据/查到了已删的数据”
- **触发**：注册 `ExtendTableDAOImpl` 后 delete = 逻辑删除（仅对 `EntityDAOManager` 注册过的表；未注册表仍物理删除；逻辑删除转成的 UPDATE 条件自动附加 `AND is_deleted = false`）；单表 SELECT 自动追加 `is_deleted = false`，但**多表/JOIN/子查询 SQL 不追加**（`SqlSingleTableChecker.isSingleTableQuery` 判定）。
- **处理**：手写 JOIN SQL 时自行给每张表加 `is_deleted = false`。

## 其他

### 25. ~~`selectIdsByCodes` 抛命名参数异常~~（已修复）
- **旧触发**：条件 `code IN (:codes)` 但参数 Map key 是 `"code"`。
- **现状**：`EntityCodeDAOImpl` 已改为 `Map.of("codes", codes)`，`selectIdsByCodes` 可正常使用。若在旧版本 jar 上遇到该异常，升级依赖即可；无需再绕行 `selectByCodes` / `selectCodeIdMap`。

### 26. ~~`BaseServiceImpl.updateWithPropertyNames` 报列不存在~~（已修复）
- **旧触发**：基类误调 `baseDAO.update(propertyNames, ...)`，属性名被当列名。
- **现状**：`BaseServiceImpl` 已改为委托 `baseDAO.updateWithPropertyNames(...)`，属性名→列名转换由 `EntityDAOImpl` 内部的 `propertyNamesToColumns()`（按 `,` 拆分 → `TableMeta.getColumnNameByPropertyName` 映射 → 重新拼接）完成。Service 层方法可直接使用。

### 27. 注册 `ExtendTableDAOImpl` 后，启动期 insert/delete 抛 `NullPointerException`
- **触发**：`ExtendTableDAOImpl.tableNameDAOMap` 字段**无初始值**（声明为 `private Map<String, EntityDAO<?,?>> tableNameDAOMap;`），仅在 `@EventListener(ApplicationReadyEvent.class) onApplicationReady()` 中由 `EntityDAOManager.getAllEntityDAO()` 填充。而 `delete(...)` 与 `addInsertInfo(tableName, paramMap)` 都直接解引用它（`tableNameDAOMap.get(tableName)`）。因此在 `ApplicationReadyEvent` 之前执行的任何写入——`@PostConstruct`、`InitializingBean.afterPropertiesSet`、`@EventListener(ContextRefreshedEvent)`、`ApplicationRunner`/`CommandLineRunner`（二者早于 `ApplicationReadyEvent`）、以及未发布该事件的测试上下文——都会 NPE。
- **区分**：`select` 路径不触碰 `tableNameDAOMap`，故启动期只读不报错，容易误判为"配置没问题"。
- **处理**：把数据初始化写入推迟到 `ApplicationReadyEvent` 之后（`@EventListener(ApplicationReadyEvent.class)` 并用 `@Order` 排在框架监听器之后，或 `ApplicationRunner` 改为监听 ready 事件）；或继承 `ExtendTableDAOImpl` 在构造/字段初始化处自行填充该 map。
- **相关**：第 24 条（逻辑删除与 `is_deleted` 过滤同样依赖该 map 是否已填充——map 未填充时 `delete` 走 NPE，而不是回落物理删除）。

### 28. `insertOrUpdateTable(list, deleteItem, deletedIdsConsumer)` 抛 NPE（**当前仍存在**）
- **触发**：三参重载 `(Collection<T> entityList, boolean deleteItem, Consumer<Collection<ID>> deletedIdsConsumer)` 委托为 `insertOrUpdateTable(entityList, null, null, deleteItem, deletedIdsConsumer)`。五参实现首段是：
  ```java
  if (deleteItem && Objects.nonNull(deletedIdsConsumer)) {
      Collection<ID> deletedIds = resolveDeletedIds(entityList,
          select(..., refColumnName + " = :refValue", Map.of("refValue", refValue)));
      ...
  }
  ```
  此路径下 `refColumnName` 与 `refValue` 均为 null → 条件被拼成字面量 `"null = :refValue"`，且 `Map.of` 拒绝 null 值**直接抛 `NullPointerException`**。即：`deleteItem=true` 且传了非 null 的 consumer 时必崩。
- **未触发的情形**：`deletedIdsConsumer` 传 null（如单参版、三参 `(list, refCol, refVal)` 版内部都传 null）时跳过该分支，正常执行。
- **处理**：需要"将被删除的 id"回调时，**必须用五参版并显式传 `refColumnName`/`refValue`**：
  ```java
  dao.insertOrUpdateTable(list, "category", category.getCode(), true, deletedIds -> log.info("{}", deletedIds));
  ```
- **备注**：这是在三参 `(list, refCol, refVal)` 版修复后复核同族方法时发现的，尚未修复。

### 27. `insertOrUpdate(List<Map>)` 返回空数组无效果
- **触发**：接口方法未实现（源码 `// TODO return new int[0]`）。勿用；循环 `insertOrUpdate(Map)` 或 batch API 替代。

### 28. 动态 DAO（`EntityDAOSupport.getEntityDAO`）上 `patch()` NPE
- **触发**：动态实例经 `registerSingleton` 注册，不走 Spring 注解注入，`validatorHelper/dbConversionService` 字段为 null（`patch()` 依赖前者）。
- **处理**：需要完整能力时声明 `@Repository` DAO 子类；动态实例仅用于常规 select/insertOrUpdate。

### 29. Spring 升级后编译失败于 `ParsedSqlHelper`
- **触发**：该类调用 spring-jdbc **包私有**方法 `ParsedSql.getParameterNames()`（放在 `org.springframework.jdbc.core.namedparam` 包内以突破可见性），Spring 内部 API 变更即断。
- **处理**：升级 spring-boot/spring-jdbc 时优先回归此编译点与动态条件查询（`select(Map)`、Grid 分页）行为；业务代码不要依赖 `ParsedSqlHelper`。详见 `ARCHITECTURE.md` §4。

## 排查工具

- **SQL 日志**：`logging.level.com.rick.db.repository: DEBUG` → 每条 SQL、参数、影响行数（`TableDAOImpl`）；建表 DDL 在 `TableGenerator` 的 INFO 日志。
- **元数据自检**：`dao.getTableMeta()`（表名、`getColumnNames()/getSelectColumn()/getUpdateColumn()`、`getIdMeta()`）比对实体与预期列集。
- **方言自检**：`Context.getDialect().getType()` 确认与 `sharp.database.type`/真实库一致。
