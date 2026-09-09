# sharp-database 架构说明

## 1. 在依赖链中的位置

```
sharp-common ← sharp-database ← { sharp-meta, sharp-fileupload } ← sharp-formflow
                     ↑
                 sharp-test（样例/集成验证）
```

- **上游 sharp-common 提供的能力**（本模块直接依赖）：
  - `SFunction`（可解析属性名的可序列化函数引用，用于 `selectById(id, T::getName)` 类 API）
  - `IdGenerator.getSequenceId()`（Twitter Snowflake 实现 `com.rick.common.util.sequence.Sequence`，`@Id` 默认 SEQUENCE 策略的 id 来源）
  - `EnumUtils.getCode/valueOfCode`（枚举 ↔ code 存取）、`JsonUtils`（json 列序列化）、`ObjectUtils.mayPureObject`（判定“纯对象”→ json 存储/`NestedRowMapper`）
  - `StringUtils.camelToSnake/stringToCamel`（属性名 ↔ 列名命名约定）
  - `ValidatorHelper`（Bean Validation 封装，`patch()` 逐属性校验）
  - `BizException`（业务异常，code 重复/code 回填失败）、`HttpServletRequestUtils`（Grid 请求取参）、`CollectionOps.expectedAsOptional`（单行断言）
  - JSON/枚举/日期转换器（`JsonStringTo*Converter`、`CodeToEnumConverterFactory` 等，注册进 `dbConversionService`）
- **下游依赖方**：sharp-meta（`DictDAO extends EntityDAOImpl`，`MetaServiceAutoConfiguration` 以 `@ConditionalOnSingleCandidate(TableDAO.class)` 挂接本模块）、sharp-fileupload（`DocumentDAO`）、sharp-formflow（`FormDAO/FormCpnDAO/FormCpnValueDAO/CpnConfigurerDAO`，其自动配置以 `@ConditionalOnSingleCandidate(GridService.class)` 挂接）。
- **构建依赖形态**：根 `build.gradle` 对所有子模块 `compileOnly` 引入 spring-boot-starter-web/validation/jdbc 等；本模块 `api project(':sharp-common')`、`compileOnly 'org.postgresql:postgresql'`。**因此运行期 Spring JDBC、驱动、Validation、Servlet API 都要由最终应用提供**。

## 2. 整体设计：这是一套什么样的持久层

**注解驱动元数据 + 运行期 SQL 拼装 + Spring JdbcTemplate 执行**的轻量 ORM，没有代理、没有会话、没有 XML：

```
实体注解(@Table/@Id/@Column/@Embedded/@ManyToOne/...)
   │  启动或首次使用时一次性解析
   ▼
TableMetaResolver.resolve(Class) ──► TableMeta（列名↔属性名映射、selectColumn/updateColumn/columnNames、
   │                                  IdMeta、versionField、Reference 表；SQL 片段带缓存）
   ▼
EntityDAOImpl（每个实体一个 DAO 实例，注册进 EntityDAOManager 静态表）
   │  按方法语义拼 SQL：SELECT 列 AS "属性路径" FROM 表 WHERE 条件
   ▼
SQLParamCleaner（Map 参数动态条件：空参剔除 / IN 展开 / LIKE 方言化 / ${} 模板）
   │  命名参数解析借助 org.springframework...ParsedSqlHelper（见 §6）
   ▼
TableDAO / TableDAOImpl ──► NamedParameterJdbcTemplate / SimpleJdbcInsert / batchUpdate
   │                          （可被业务替换为 ExtendTableDAOImpl：逻辑删除+审计填充）
   ▼
结果映射：ColumnMapRowMapper（Map）/ SingleColumnRowMapper（标量）/ NestedRowMapper（复杂对象，
   │      列别名=属性路径，支持嵌套对象自增长、json 列经 dbConversionService 反序列化、PGobject 取值）
   ▼
级联装载：selectReference（@ManyToOne/@OneToMany/@ManyToMany 批量 IN 补查、@Select 逐行查询，
          ThreadLocal 实体缓存防环）
```

方言（`AbstractDialect`）只介入两处：Grid 分页（count 包装 + pageSql + order by）与 LIKE 改写；实体 CRUD 生成的 SQL 是方言无关的标准语法（PostgreSQL json 是唯一按 `DatabaseType` 分支的特例，在 `EntityDAOImpl.postgresJsonHandler` / `TableMeta.appendColumnVar`）。

### 关键运行时结构

- **`EntityDAOManager`**：`Map<Class, EntityDAO>` 静态注册表 + 两个 ThreadLocal（`threadLocalEntity` 本次顶层查询已加载实体、`localStack` select 嵌套深度）。级联查询/保存通过它找引用实体的 DAO；顶层 select 结束时清理 ThreadLocal（`watchSelect`）。这就是“无会话”的替代物——**只在一次 select 调用栈内生效**。
- **DAO 实例的两条来源**：① 业务 `@Repository class XxxDAO extends EntityDAOImpl<...>`（完整 Spring 注入 + `@Validated` 代理）；② `EntityDAOSupport` 构造的动态实例（`@PostConstruct` 扫描 `entity-base-package` 下 `@Table` 类，或 `getEntityDAO(Class)` 懒创建；`registerSingleton` 注册，Bean 名 = 实体名驼峰 + `DAO`）。两者都会 `EntityDAOManager.register`，**后注册者覆盖注册表**；动态实例不经过注解注入生命周期（`validatorHelper`/`dbConversionService` 字段为 null，`patch()` 等方法不可用）。
- **`TableMeta` 缓存**：每实体解析一次（DAO 构造时），SQL 片段字符串懒加载缓存。无失效机制——实体注解是静态的，合理。

## 3. 自动配置生效条件全貌

`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` → `com.rick.db.config.SharpDatabaseAutoConfiguration`。

- **生效前提**：`@ConditionalOnSingleCandidate(DataSource.class)`（存在唯一/主 DataSource），`@AutoConfigureAfter(DataSourceAutoConfiguration.class)`。多数据源且无 `@Primary` → 整个模块自动配置不生效。
- **Bean 清单**：

| Bean | 条件 | 可被业务覆盖 |
|---|---|---|
| `tableDAO`（TableDAOImpl；含读取 `databaseProductVersion`、可选 `DatabaseMetaData.initTableMapping`） | `@ConditionalOnMissingBean(TableDAO.class)` | ✅ 覆盖入口（sharp-test 用 `ExtendTableDAOImpl` 覆盖） |
| `getDialect`（AbstractDialect，按 `sharp.database.type` if-else 选型；写入静态 `Context`） | 无条件 | ❌（无 OnMissingBean；自定义 Bean 不会同步 `Context` 与 `SQLParamCleaner`） |
| `gridService`（并调用 `SQLParamCleaner.setDialect`） | 无条件 | ❌ |
| `getEntityCodeIdFillService` | 无条件 | ❌ |
| `entityDAOSupport` | 无条件；`@PostConstruct` 依赖 `entity-base-package`（null → 启动 NPE） | ❌ |
| `validatorHelper` | `@ConditionalOnMissingBean` + `@ConditionalOnBean(Validator.class)` | ✅ |
| `dbConversionService`（ConversionService，汇总上下文所有 `ConverterFactory` Bean + 内置 JSON/枚举/日期/IdToEntity 转换器） | 无条件 | ❌ |
| `tableGenerator`（按方言选型；Oracle/SQLServer TODO → 回退 MySQL5TableGenerator） | 无条件（需 `JdbcTemplate` Bean） | ❌ |
| `UtilGridServiceConfiguration`（把 GridService 注入静态 `GridUtils`） | 无条件 | — |

- **应用侧还需**：spring-boot-starter-jdbc（DataSource/JdbcTemplate/NamedParameterJdbcTemplate 自动配置）、JDBC 驱动、（用到校验时）starter-validation、（用到 `GridHttpServletRequestUtils` 时）Servlet API。

## 4. `org.springframework.jdbc.core.namedparam.ParsedSqlHelper` 的存在理由

**它访问了什么**：spring-jdbc 6.2.x 中 `ParsedSql` 类本身是 public，但其构造器与 `getParameterNames()` 方法是**包私有**（已用 javap 对 spring-jdbc 6.2.12 验证）。`ParsedSqlHelper.get(sql)` 调 `NamedParameterUtils.parseSqlStatement(sql)`（public）拿到 `ParsedSql`，再调包私有的 `getParameterNames()`，得到 SQL 中全部命名参数名列表。

**为什么必须放在这个包**：Java 的包私有可见性按“同包”判定（classpath 下同名包即可，无需同一 jar）。`SQLParamCleaner.formatSql` 需要枚举 SQL 里的命名参数以逐个决定“剔除条件/展开 IN/改写 LIKE”，Spring 没有公开 API 提供该能力，故把工具类放进 `org.springframework.jdbc.core.namedparam` 包突破可见性（源码类注释原文：“ParsedSql只有在包org.springframework.jdbc.core.namedparam才能获取getParameterNames”）。

**对业务方的影响**：无直接使用价值——业务代码**不应调用它**（🚫）。它的产出只体现在 `SQLParamCleaner` 的行为里。

**风险（升级 Spring 时必须关注）**：
1. **编译期耦合**：若 spring-jdbc 升级后 `ParsedSql`/`getParameterNames` 改名、删除或转 public API 调整，本模块编译失败（fail-fast，尚可控）。
2. **运行期语义耦合**：`parseSqlStatement` 的解析规则（注释处理、`:` 转义）变化会静默改变 `SQLParamCleaner` 的空参剔除/IN 展开行为。
3. **split-package**：与 spring-jdbc 同包分属两个 jar。当前 spring-jdbc 未 sealed、项目未启用 JPMS，故可用；若未来引入模块路径或 Spring 封包，此技巧失效。
4. Spring 官方立场是包私有 API 不承诺兼容——每次升级 spring-boot/spring-jdbc 都应回归测试动态条件查询（`select(Map)`、Grid 分页）。

## 5. 分页链路 与 代码生成链路

**分页**（详细 API 见 `docs/api/pagination.md`）：

```
请求参数 page/size/sidx/sord + 业务参数（Map 或 HttpServletRequest）
  → QueryModel.of(map) 抽取 PageModel（缺省 page=1,size=15；size=-1 → 全量模式）
  → GridUtils.setOrderParams：sidx 必须命中 sortableColumns 白名单，经 dialect.getOrderBy 生成
    "temp_.col asc, temp_.id ASC"（MySQL5 追加 id 保证稳定序）
  → GridService.query：
      SQLParamCleaner.formatSql（空参条件剔除 / IN 展开 / LIKE 改写）
      → countSQL（默认 dialect.formatSqlCount = SELECT COUNT(*) FROM (去 order by 的 SQL) temp）→ records
      → records=0 → Grid.emptyInstance
      → PageModel 校正（size<1→15，>1000→1000；page 越界钳制）→ totalPages
      → dialect.pageSql 包装分页（MySQL/SQLite: LIMIT off,size；PG: LIMIT..OFFSET；
        Oracle: ROWNUM 双层；SQLServer: OFFSET..FETCH，无排序时补 ORDER BY (SELECT NULL)）
      → 执行 + 行映射（Map/Class/JdbcTemplateCallback）
  → Grid{page,pageSize,records,totalPages,rows}
```

**代码生成**（`docs/examples/code-generator.md`）：

```
@SpringBootTest 注入 TableGenerator Bean（自动配置按 sharp.database.type 选型）
  → createTable(Class)：TableMetaResolver.resolve → DDL 拼装（id 列按策略：AUTO_INCREMENT /
    GENERATED ALWAYS AS IDENTITY / 普通列；@Version 列 NOT NULL；columnDefinition 优先；
    类型映射 determineSqlType；MySQL 加 COMMENT/ENGINE，PG 追加 comment on 语句）
  → log.info 打印 DDL → jdbcTemplate.execute
  → @ManyToMany 中间表 DDL（两列 + is_deleted + UNIQUE(join,inverse)）
  → ThreadLocal 集合去重（同线程同表只建一次）
```
定位是开发/测试期工具（`src/test/generated_tests` 目录当前为空），没有迁移/增量 DDL（alter）能力，生产建表应以打印出的 DDL 为底稿走正规变更流程。

## 6. 扩展点汇总

| 扩展点 | 机制 |
|---|---|
| `TableDAO` 替换 | `@ConditionalOnMissingBean(TableDAO.class)`；现成扩展 `ExtendTableDAOImpl`（逻辑删除 + 审计填充 + 单表 SELECT 自动加 `is_deleted=false`，依赖 `SqlSingleTableChecker` 判定单表；`getUserId()` 默认 1L 需覆写） |
| 保存回调 | `InsertUpdateCallback` Bean（`@Autowired(required=false)` 注入进 EntityDAOSupport/动态 DAO；现成实现 `ExtendInsertUpdateCallback`） |
| DAO 钩子 | `itemDeletedCheckCallback` / `handlerReferenceListBefore` / `protected select(Class,String,Map)` |
| 条件合并 | 抽象类 `ConditionEntityCodeDAOImpl`（delete/update 注入固定条件，可做租户/分类隔离） |
| 分类表 | `RowCategory` + `Category*DAOImpl` 四件套 |
| Grid 报表 | `AbstractTableGridService` / `DefaultTableGridService` |
| 类型转换 | 注册 `ConverterFactory` Bean 自动并入 `dbConversionService` |
| 新方言 | **无 SPI**：需改模块源码（`DatabaseType` 枚举 + `getDialect` if-else + `AbstractDialect` 子类 + `TableGenerator` 选型），见 API.md §16 |

## 7. 设计约束与已知限制（源码取证）

1. **单数据源**：`@ConditionalOnSingleCandidate(DataSource.class)`；`Context`/`SQLParamCleaner`/`GridUtils` 均为静态单例状态，不支持多库多方言并存。
2. **硬编码 `id` 列**：`selectByIds/deleteByIds/updateByIds/selectByIdWithoutCascade/selectById(id,SFunction)` 等的条件字面量是 `id`，主键列改名（`@Id` + `@Column("xxx")`）后这些方法失效；`exists` 系列与 `TableDAO.exists` 硬编码 `LIMIT 1`，Oracle/SQLServer 方言下报错。
3. **乐观锁非原子**：`@Version` 校验是先 SELECT 后 UPDATE，WHERE 不带版本条件（API.md §11）。
4. **`@Select` 是 N+1**：逐行执行；`@ManyToOne/@OneToMany/@ManyToMany` 是批量 IN（每个引用字段一次）。无 JOIN 抓取。
5. ~~三参 `insertOrUpdateTable(list, refCol, refVal)` 忽略范围参数~~ → **已修复**：现透传为五参版 `insertOrUpdateTable(entityList, refColumnName, refValue, true, null)`，范围删除生效。注意单参版 `insertOrUpdateTable(list)` 仍是全表同步语义。
6. ~~`EntityCodeDAOImpl.selectIdsByCodes` 参数名 bug~~ → **已修复**：参数 Map key 已改为 `"codes"`，与条件 `:codes` 匹配，可正常使用。
7. ~~`BaseServiceImpl.updateWithPropertyNames` 误调 `baseDAO.update`~~ → **已修复**：现委托 `baseDAO.updateWithPropertyNames(propertyNames, condition, example)`，属性名→列名的转换由 `EntityDAOImpl.updateWithPropertyNames` 内部的私有方法 `propertyNamesToColumns()`（按 `,` 拆分 → `TableMeta.getColumnNameByPropertyName` 映射 → 重新拼接）统一完成。
8. **`insertOrUpdate(List<Map>)` 未实现**（返回空数组，源码 TODO）。
9. **无 schema 迁移**：生成器只会 CREATE TABLE（重复执行报表已存在），无 ALTER/版本化迁移；`DbScriptUtils.importSQL` 只是脚本执行器。
10. **动态注册的 DAO 实例无 Spring 注入**（`registerSingleton` 不走注解处理），依赖注入字段的方法（`patch` 等）受限；正式业务应声明 `@Repository` DAO 子类。
11. **`track-if-has-update=true` 的比较**忽略审计列，且对复杂值走 `toString` 比较，json/对象列可能误判“未变化”。
12. **Oracle/SQLServer 支持不完整**：方言存在（分页/函数），但建表生成器缺失（回退 MySQL DDL）、且限制 2 的 `LIMIT 1` 问题在这两种库上必现。
13. **`DatabaseMetaData` 静态元数据表无消费方**（仅 `init-database-meta-data=true` 时填充），用途[需要确认]。
14. **`@CodeFillIgnore` 无引用点**（`@CodeFillUncheck` 有），语义[需要确认]。
