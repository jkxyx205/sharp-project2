# sharp-database 模块使用指南（面向 AI 编程助手）

> group: `com.rick.db`　|　JDK 17 / Spring Boot 3.5.7　|　自动配置类：`com.rick.db.config.SharpDatabaseAutoConfiguration`

## 模块定位

sharp-database 是一套**自研的注解驱动轻量 ORM + 分页 + 建表生成器**，底层基于 Spring `NamedParameterJdbcTemplate` 直接执行由实体注解元数据（`TableMeta`）拼装出的 SQL。

**它不是 JPA/Hibernate，也不是 MyBatis。禁止套用它们的心智模型：**

- 没有 PersistenceContext / 一级二级缓存 / 脏检查 / flush。实体是普通 POJO（Lombok），保存 = 显式调用 `insert/update/insertOrUpdate`。
- 没有懒加载代理。`@ManyToOne/@OneToMany/@ManyToMany/@Select` 是**查询后一次性批量级联加载**（eager，非 JPA 语义）。
- 没有 XML/注解式 SQL Mapper。复杂查询 = 业务方直接写 SQL 字符串（命名参数 `:name`）交给 `TableDAO`/`GridService`，框架用 `SQLParamCleaner` 动态清理空参数条件。
- 注解形似 JPA（`@Table/@Id/@Column/@Transient/@ManyToOne`...）但**包名是 `com.rick.db.repository`，语义有差异**（详见 `docs/api/annotations.md`），不要凭 JPA 经验推断行为。

**适用**：单表/主从表 CRUD、按 code 查询、分类（category）字典表、动态条件分页报表（Grid）、由实体生成建表 DDL（开发期）。

**不适用/需注意**：需要真正懒加载、二级缓存、复杂 JPQL/Criteria、多数据源路由（自动配置基于 `@ConditionalOnSingleCandidate(DataSource.class)`，仅单数据源生效）、Oracle/SQLServer 的建表生成器（源码中为 TODO，回退到 MySQL5 生成器）。

## 源码阅读规则

```
默认不要扫描整个模块源码。
使用模块时：
1. 优先阅读 CLAUDE.md
2. 再阅读 API.md
3. 如果 API.md 已经可以解决问题，不要继续阅读源码
4. 只有在文档无法解决问题、排查 Bug 或确认特殊行为时，才阅读相关源码
```

## 文档索引

| 文档 | 内容 |
|---|---|
| `API.md` | 全部 API 分类参考 + 配置项 + Common Mistakes（首选查阅） |
| `ARCHITECTURE.md` | 设计链路、自动配置条件、`ParsedSqlHelper` 说明、扩展点、已知限制 |
| `docs/api/annotations.md` | 实体注解完整参考 |
| `docs/api/dao.md` | `EntityDAO`/`EntityCodeDAO`/`TableDAO` 方法完整参考 |
| `docs/api/pagination.md` | `PageModel`/`Grid`/`GridService`/`GridUtils`/`AbstractTableGridService` |
| `docs/api/configuration.md` | `sharp.database.*` 配置项与自动配置 Bean 一览 |
| `docs/examples/crud.md` | 实体→DAO→Service→Controller 最小 CRUD |
| `docs/examples/relation.md` | 一对多/多对多/一对一/@Select/@Embedded 关联示例 |
| `docs/examples/pagination.md` | 标准分页查询示例 |
| `docs/examples/category.md` | 分类（category）字典表示例 |
| `docs/examples/code-generator.md` | 建表生成器示例 |
| `docs/troubleshooting.md` | 异常→触发条件→处理方式 |

## 使用原则

1. **DAO 标准写法**：`@Repository public class XxxDAO extends EntityDAOImpl<Xxx, Long> {}`；实体带 code 用 `EntityCodeDAOImpl`；分类实体按「实体是否带 code」×「分类是否为枚举」在 `Category*DAOImpl` 四件套中选（最常见 `CategoryEnumEntityCodeDAOImpl`；仅 id 无 code 的枚举分类表用 `CategoryEnumEntityDAOImpl`，见 API.md §13）。真实范例：sharp-test `UserDAO`/`IdCardDAO`/`CodeDescriptionDAO`，下游 `DictDAO`/`DocumentDAO`/`FormDAO`。不需要专属 DAO 类时，注入 `EntityDAOSupport` 调 `getEntityDAO(Xxx.class)`。分类列名不是默认的 `category` 时，四个父类都可在子类构造里 `super("列名")`。
2. **Service 层**：继承 `BaseServiceImpl<XxxDAO, Xxx, Long>`（或 `BaseCodeServiceImpl`）白得整套 EntityDAO 委托方法；**事务加在 Service 层**（`@Transactional(rollbackFor = Exception.class)`），本模块任何类都没有 `@Transactional`。
3. **分页**：用 `GridService`/`GridUtils`/`AbstractTableGridService`。**total（Grid.records）由框架自动生成 count SQL 计算**（`SELECT COUNT(*) FROM (去掉 order by 的原 SQL) temp`），不要自己写 count SQL，除非需要优化时才传 `countSQL` 参数。
4. **动态条件**：`SQLParamCleaner` 的空参条件剔除与 LIKE 自动改写**只发生在两条路径**——分页/Grid SQL（`GridService/GridUtils/GridHttpServletRequestUtils`）和 `EntityDAO.select(Map)`（不带 condition 的全列动态查询）。Grid SQL 里模糊查询写 `col LIKE :param` 即可（自动改写为大小写不敏感 contains 并转义 `%`/`_`，不要自己拼 `%`）；其余 DAO 方法（`select(condition, paramMap)` 等）是直接 `NamedParameterJdbcTemplate` 绑定：condition 里的每个命名参数都必须在 Map 中有值（缺 key 抛异常），null 会绑定为 NULL（等值条件永假），动态条件需业务自己拼 condition 或用 `select(Map)`/Grid 路径。
5. **部分更新**用 `patch(entity)` 或 `updateById(columns, id, ...)`；`update(entity)` 是**全列更新，null 字段会把库中值清成 NULL**。
6. 新增实体后确认包被 `sharp.database.entity-base-package` 覆盖（否则级联查询找不到该实体的 DAO）。

## 禁止行为（依据源码）

- **不要 `new EntityDAOImpl<>(...)`/`new TableDAOImpl(...)`**：DAO 必须是 Spring Bean（`@Repository` 子类或 `EntityDAOSupport` 注册），否则 `@Resource` 注入（tableDAO/conversionService/validatorHelper）与 `@Validated` 参数校验不生效。
- **不要直接调用 `EntityDAOManager.register(...)`**：静态注册表是框架级联查询的内部机制。
- **不要使用 `org.springframework.jdbc.core.namedparam.ParsedSqlHelper`**：它是为访问 Spring 包私有方法 `ParsedSql.getParameterNames()` 而放在 Spring 同名包下的内部工具，与 spring-jdbc 版本强耦合（见 ARCHITECTURE.md）。
- **不要硬编码方言相关 SQL**（如 `LIMIT`、`IFNULL`、`::json`）到业务查询中，跨库需求交给 `AbstractDialect`/`SharpDatabaseProperties.type`。
- **数据库列为 json/jsonb 时，实体属性必须显式声明 `@Column(columnDefinition = "json")` 或 `@Column(columnDefinition = "jsonb")`**（与实际列类型一致）：PostgreSQL 下框架只对显式声明的列做 `PGobject` 包装与 `::json/::jsonb` cast（`EntityDAOImpl.postgresJsonHandler`、`TableMeta.appendColumnVar`），未声明则写入失败；读取时 `PGobject` 由 `TableDAOImpl` 解包。注意声明后 `nullable/comment` 失效（DDL 直接使用 columnDefinition 原文）。详见 `docs/api/annotations.md` 与 `docs/troubleshooting.md` 第 18 条。
- **不要信任前端 `sidx` 排序参数**：`sidx/sord` 是字符串拼接进 ORDER BY 的（非绑定参数），必须通过 `GridUtils.list(sql, params, countSQL, sortableColumns...)` 或 `AbstractTableGridService` 提供列白名单。
- **不要用 `${...}` 模板变量承接用户输入**：`SQLParamCleaner.replaceVars` 是原样字符串替换，存在 SQL 注入风险。
- **不要调用已废弃 API**：`com.rick.db.util.OperatorUtils`（整类 `@Deprecated`，用 sharp-common `CollectionOps` 替代）、`@Select.nullWhenParamsIsNull`（`@Deprecated`，参数为 null 时框架已默认直接返回 null）、`SQLParamCleaner.formatSql(sql, params, formatMap[, isSetIsNull])` 带外置 formatMap 的两个重载（`@Deprecated`，用返回 `FormatParam` record 的版本）。
- **注意 `insertOrUpdateTable(list)` 单参版是「全表同步」语义**：会删除不在 list 中的所有行。子表范围保存请用三参版 `insertOrUpdateTable(list, refColumnName, refValue)`（已修复，范围参数生效）或 Category DAO 的 `insertOrUpdate(category, list)`。
  > 历史提示：三参版曾忽略 `refColumnName/refValue` 而等效全表删除，**现已修复**（`EntityDAOImpl` 透传为五参版）。看到旧的"勿用三参版"建议不要照做。

## 最小上手骨架

四步完整可运行示例见 `docs/examples/crud.md`（蓝本：sharp-test `module/db/user`）。

**1. 定义实体**（`com.rick.db.repository` 注解，非 jakarta.persistence）：

```java
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Table(value = "t_user", comment = "用户")
public class User extends BaseEntity<Long> {   // id 默认雪花算法自动生成
    @NotBlank
    String name;

    @Transient          // 忘加会被当成列，insert 时报“列不存在”
    Integer score;
}
```

**2. 定义 DAO**：

```java
@Repository
public class UserDAO extends EntityDAOImpl<User, Long> {
}
```

**3. 写 Service**（事务在这一层）：

```java
@Service
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Validated
public class UserService extends BaseServiceImpl<UserDAO, User, Long> {

    public UserService(UserDAO baseDAO) {
        super(baseDAO);
    }

    @Transactional(rollbackFor = Exception.class)
    public User save(User user) {
        return baseDAO.insertOrUpdate(user);   // id 为 null → insert，否则 update
    }

    public Optional<User> findById(Long id) {
        return baseDAO.selectById(id);          // 不存在返回 Optional.empty()
    }
}
```

**4. 分页查询**（前端传 `page/size/sidx/sord` + 业务参数；total 自动计算）：

```java
@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final GridService gridService;

    public Grid<Map<String, Object>> page(Map<String, Object> params) { // params 来自请求
        QueryModel qm = QueryModel.of(params);
        return gridService.query(
                "SELECT id, name FROM t_user WHERE name LIKE :name",
                qm.getPageModel(), qm.getParams());
    }
}
```

**必备配置**（application.yml）：

```yaml
spring:
  datasource: ...        # 应用必须自带 spring-boot-starter-jdbc 与驱动（本模块 compileOnly）
sharp:
  database:
    type: PostgreSQL                       # 默认 MySQL5
    entity-base-package: com.acme.**.entity # 缺失会导致启动 NPE（EntityDAOSupport.init）
```
