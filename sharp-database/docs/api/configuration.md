# 配置项与自动配置完整参考

> 源码：`com.rick.db.config.SharpDatabaseProperties`、`SharpDatabaseAutoConfiguration`、`Context`；
> 注册文件：`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。

## @ConfigurationProperties：前缀 `sharp.database`

```java
@ConfigurationProperties(prefix = "sharp.database")
@Data
public class SharpDatabaseProperties {
    private DatabaseType type = DatabaseType.MySQL5;
    private String databaseProductVersion;
    private boolean initDatabaseMetaData = false;
    private String entityBasePackage;
    private boolean trackIfHasUpdate = false;
}
```

### 逐项说明

| key | 类型 | 默认值 | 必填 | 用途 | 什么时候修改 | 修改后副作用 |
|---|---|---|---|---|---|---|
| `sharp.database.type` | `com.rick.db.repository.model.DatabaseType` 枚举：`MySQL5 / MySQL8 / PostgreSQL / Oracle10g / Oracle11c / SQLServer2012 / SQLite` | `MySQL5` | 否（但**必须与实际库一致**） | 选择方言 Bean（分页 SQL、count 包装、LIKE 改写、ORDER BY、summaryFun）与建表生成器；PostgreSQL 还触发实体层 json/PGobject 特殊处理 | 切换数据库时 | 分页/LIKE/建表 DDL 语法全部随之变化；配置与实际库不符 → 分页语法错误、json 列读写失败。**框架不从 DataSource 元数据推断类型** |
| `sharp.database.entity-base-package` | String（多包用 `,` + 空白分隔，源码 `split(",\\s+")`；支持 Ant 通配 `**`，如 `com.rick.test.**.entity, com.rick.test.**.select`） | 无（null） | **是** | `EntityDAOSupport.init()`（`@PostConstruct`）扫描这些包下所有 `@Table` 类，为每个实体预注册 DAO（`EntityDAOManager` + 单例 Bean `<实体名驼峰>DAO`），供级联查询/保存使用 | 新增实体包时确认覆盖 | **未配置：启动即 `NullPointerException`**（对 null 调 split）。实体不在扫描包且无 `@Repository` DAO：被其他实体级联引用时 `EntityDAOManager.getDAO` 返回 null → NPE |
| `sharp.database.init-database-meta-data` | boolean | `false` | 否 | true 时 `tableDAO` Bean 创建过程中调用 `DatabaseMetaData.initTableMapping(jdbcTemplate)`：读取全库 TABLE 的列清单与主键，存入静态 `DatabaseMetaData.tableColumnMap / tablePrimaryKeyMap` | 需要全库表结构元数据时 | 启动变慢（逐表两次元数据查询）；当前代码库**无任何消费方**，用途[需要确认] |
| `sharp.database.track-if-has-update` | boolean | `false` | 否 | true 时 `TableDAOImpl.update(...)` 先 SELECT 目标列现值，与参数比对（忽略 `id/create_by/create_time/update_by/update_time`），完全一致则**跳过 UPDATE 返回 0** | 想避免无变化更新（触发器/审计噪音）时 | 每次 update 前多一次 SELECT；比较对复杂类型退化为 `toString` 对比，json/对象列可能误判；返回 0 不再代表“行不存在” |
| `sharp.database.database-product-version` | String | 无 | 否（**不要在 yml 配置**） | 输出型属性：`tableDAO` Bean 创建时从 `connection.getMetaData().getDatabaseProductVersion()` 回写 | 只读 | 连接取元数据失败 → 启动抛 `RuntimeException` |

示例（sharp-test `application.yml` 真实配置）：

```yaml
sharp:
  database:
    type: PostgreSQL
    entity-base-package: com.rick.test.**.entity, com.rick.test.**.select
    track-if-has-update: false
```

## 自动配置：SharpDatabaseAutoConfiguration

类级条件：

```java
@Configuration
@ConditionalOnSingleCandidate(DataSource.class)      // 需要唯一（或 @Primary）DataSource，多数据源不生效
@AutoConfigureAfter({DataSourceAutoConfiguration.class})
@EnableConfigurationProperties({SharpDatabaseProperties.class})
```

### Bean 一览（含条件与可覆盖性）

| Bean 方法 | 产物 | 条件 | 业务可覆盖？ | 说明 |
|---|---|---|---|---|
| `GridServiceConfiguration#tableDAO` | `TableDAOImpl`（`TableDAO` Bean） | `@ConditionalOnMissingBean(TableDAO.class)` | ✅ **官方扩展入口** | 创建时回写 `databaseProductVersion`；`init-database-meta-data=true` 时初始化 `DatabaseMetaData`。业务定义自己的 `TableDAO` Bean（如 `new ExtendTableDAOImpl(npjt)`）即覆盖（范例 sharp-test `TestConfig`） |
| `GridServiceConfiguration#getDialect` | `AbstractDialect`（按 `type` if-else new 具体方言）；同时 `Context.setDialect(...)` | 无 | ❌ 无 OnMissingBean | 自定义方言 Bean 不会同步静态 `Context` 与 `SQLParamCleaner`，不能简单覆盖 |
| `GridServiceConfiguration#gridService` | `GridService`；同时 `SQLParamCleaner.setDialect(dialect)` | 无 | ❌ | 分页执行器 |
| `GridServiceConfiguration#getEntityCodeIdFillService` | `EntityCodeIdFillService` | 无 | ❌ | code→id 回填服务 |
| `EntityDAOConfiguration#entityDAOSupport` | `EntityDAOSupport` | 无 | ❌ | 参数 `@Autowired(required=false) List<EntityDAO>`（把业务 DAO Bean 先实例化，未直接使用）；`@PostConstruct` 执行包扫描 |
| `EntityDAOConfiguration#validatorHelper` | `ValidatorHelper`（sharp-common） | `@ConditionalOnMissingBean` + `@ConditionalOnBean(Validator.class)` | ✅ | `patch()` 逐属性校验依赖它；应用无 `Validator` Bean 时不创建 |
| `EntityDAOConfiguration#dbConversionService` | `ConversionService`（`DefaultFormattingConversionService`，Bean 名 `dbConversionService`） | 无 | ❌ | 注册：上下文全部 `ConverterFactory` Bean + `StringToLocalDateConverterFactory`、`CodeToEnumConverterFactory`、`JsonStringToListMapConverter`、`JsonStringToObjectConverterFactory`、`JsonStringToMapConverterFactory`、`JsonStringToCollectionConverter`、`JsonStringToSetMapConverter`、`IdToEntityConverterFactory`、`LocalDateTimeToInstantConverter`。行映射与属性写入的类型转换均走它 |
| `UtilGridServiceConfiguration#setGridService` | —（`@Autowired` setter） | 无 | — | 把 GridService 注入静态 `GridUtils.GRID_SERVICE` |
| `TableGeneratorConfiguration#tableGenerator` | `TableGenerator` | 无（需 `JdbcTemplate` Bean） | ❌ | 按 `dialect.getType()` 选型：PostgreSQL→`PostgresSQLTableGenerator`，MySQL5→`MySQL5TableGenerator`，MySQL8→`MySQL8TableGenerator`，SQLite→`SQLiteTableGenerator`；**Oracle10g/11c/SQLServer2012 分支为 TODO → 落到方法末尾回退 `MySQL5TableGenerator`** |

被注释掉未启用的：`GridServiceCacheConfiguration`（`sharp.database.select-cache` 查询缓存，源码整段注释）。

### 应用侧运行期必备依赖（本模块 compileOnly，不传递）

- `spring-boot-starter-jdbc`：DataSource、`JdbcTemplate`、`NamedParameterJdbcTemplate`（Spring Boot JDBC 自动配置提供）
- JDBC 驱动；**PostgreSQL 必须显式引入 `org.postgresql:postgresql`**（`EntityDAOImpl.postgresJsonHandler` 直接使用 `PGobject`；该路径仅在 `type=PostgreSQL` 时执行）
- `spring-boot-starter-validation`（`Validator` Bean；缺失则 `validatorHelper` 不创建、`patch()` 不可用）
- Servlet API（仅 `GridHttpServletRequestUtils`/`AbstractTableGridService.list(request)` 需要）

## Context（静态上下文）

```java
@UtilityClass
public class Context {
    static void setDialect(AbstractDialect dialect);   // 包私有：仅自动配置可写
    public static AbstractDialect getDialect();         // 业务只读
}
```

- 单一静态字段，启动时写一次，之后只读——实践上线程安全；**业务代码只允许 `getDialect()`**（如 `Context.getDialect().getType() == DatabaseType.PostgreSQL` 做 json 分支）。
- 隐含约束：一个 JVM 只支持一个方言（单数据源设计）。

## 日志开关（排查 SQL）

模块在 DEBUG 级别输出最终 SQL 与参数（`TableDAOImpl` 各方法 `log.debug("SQL => [{}], args => ...")`）：

```yaml
logging:
  level:
    com.rick.db.repository: DEBUG   # sharp-test application.yml 实测写法
```

建表生成器以 `log.info` 输出 DDL（`TableGenerator.createTable`）。
