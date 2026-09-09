# 分页与 Grid 完整参考

> 源码：`com.rick.db.plugin.page.*`、`com.rick.db.plugin.table.*`、`com.rick.db.repository.support.dialect.AbstractDialect`。

## 分工总览

| 类 | 等级 | 角色 |
|---|---|---|
| `PageModel` | ⭐ | 分页参数载体（page/size/sidx/sord），前后端契约 |
| `Grid<T>` | ⭐ | 分页结果载体（返回给前端的 JSON 结构） |
| `GridService` | ⭐ | 分页执行器 Bean：清理参数 → count → 分页包装 → 查询 → Grid |
| `GridUtils` | ⭐ | GridService 的静态门面（自动配置注入实例），从 params Map 直接分页 |
| `GridHttpServletRequestUtils` | ⚠️ | Web 层便捷入口：从 `HttpServletRequest` 取参再走 GridUtils |
| `QueryModel` | ⚠️ | 把请求 Map 拆成 PageModel + params 的辅助 |
| `AbstractTableGridService` / `DefaultTableGridService` | ⭐ | “一个报表 = 一段 SQL”的封装：子类只给 SQL |
| `PaginationHelper` | ⚠️ | 纯 UI 辅助：计算页码显示窗口，与查询无关 |

**前端 → 后端参数契约**（`PageModel` 常量）：

| 参数 | 常量 | 类型 | 默认 | 说明 |
|---|---|---|---|---|
| `page` | `PageModel.PARAM_PAGE` | int | 1 | 当前页，从 1 开始；越界自动钳制（>totalPages → totalPages；<1 → 1） |
| `size` | `PageModel.PARAM_SIZE` | int | 15 | 每页条数；`-1` = 不分页查全部；`<1`（非 -1）→ 15；`>1000` → 1000 |
| `sidx` | `PageModel.PARAM_SIDX` | String | null | 排序列（**列名**，字符串拼接进 ORDER BY，须白名单控制） |
| `sord` | `PageModel.PARAM_SORD` | String | null | 排序方向；`asc`（忽略大小写）为升序，其余按降序生成；sidx/sord 任一为空 → 不排序 |

业务过滤参数与分页参数放在同一个 Map/请求中即可（如 `name`、`code`），SQL 用 `:name` 引用；未使用的 key 自动忽略。

## Grid<T> 返回结构

```java
@Value @Builder
public class Grid<T> implements Serializable {
    private int page;                       // 当前页（校正后）
    @JsonProperty("pageSize") private int pageSize;
    private int records;                    // 总条数（total）—— 框架自动 count
    @JsonProperty("totalPages") private int totalPages;
    private List<T> rows;                   // 数据
    private Map<String, Object> additionalInfo; // query 路径不设置 → null；仅 emptyInstance 为空 Map
    public static <T> Grid<T> emptyInstance(int pageSize); // page=1, records=0, totalPages=0, rows=[]
}
```

- `records == 0` 时直接返回 `emptyInstance`，不再执行数据查询。
- `size = -1`（全量模式）：不执行 count，`records = rows.size()`，`totalPages = records>0 ? 1 : 0`。

## GridService（Bean）

```java
public GridService(TableDAO tableDAO, AbstractDialect dialect);   // 由自动配置创建，业务注入使用

public Grid<Map<String, Object>> query(String sql, PageModel model, Map<String, Object> params);
public Grid<Map<String, Object>> query(String sql, PageModel model, Map<String, Object> params, String countSQL);
public <T> Grid<T> query(String sql, PageModel model, Map<String, Object> params, Class<T> clazz);
public <T> Grid<T> query(String sql, PageModel model, Map<String, Object> params, Class<T> clazz, String countSQL);
public <T> Grid<T> query(String sql, PageModel model, Map<String, Object> params, JdbcTemplateCallback<T> callback);
public <T> Grid<T> query(String sql, PageModel model, Map<String, Object> params, JdbcTemplateCallback<T> callback, String countSQL);
```

- `sql`：**不含分页/排序包装**的业务 SQL（`SELECT ... FROM ... WHERE col = :param ...`）。分页由方言包装为 `SELECT * FROM (你的SQL) temp_ ORDER BY ... LIMIT ...`。
- `params`：命名参数 Map；经 `SQLParamCleaner.formatSql` 处理（null/空串条件剔除、IN 展开、LIKE 改写、枚举转 code）。**params 可以是含 page/size 的原始请求 Map**，多余 key 无害。
- `countSQL`：可选优化。缺省自动生成：`SELECT COUNT(*) FROM (<sql 去掉 order by 子句>) temp`（`AbstractDialect.formatSqlCount`；Oracle10g 方言对含 LISTAGG 的 SQL 不去 order by）。**total 不需要业务自己算**。
- `clazz`：行映射目标类（`NestedRowMapper`）；缺省返回 `Map<String,Object>` 行。注意：此路径**不做实体级联加载**（要级联用 `EntityDAOSupport.select` 或 DAO）。
- `callback`：完全自定义行映射（拿 `NamedParameterJdbcTemplate` + 最终 SQL + 清理后的参数）。

## GridUtils（静态门面）

```java
public static Grid<Map<String, Object>> list(String sql, Map<String, Object> params);
// 排序白名单 = params 里的 sidx 自身（等于不校验）
public static Grid<Map<String, Object>> list(String sql, Map<String, Object> params, String countSQL);
public static Grid<Map<String, Object>> list(String sql, Map<String, Object> params, String countSQL, String... sortableColumns);
// ✅ 推荐：显式给出允许排序的列白名单（列名，忽略大小写匹配 sidx）
public static List<BigDecimal> numericObject(String sql, Map<String, Object> params);
// 合计/平均值：单行、每列都是数字的 SQL → List<BigDecimal>（按列顺序）
public static void setOrderParams(PageModel pageModel, String[] sortableColumns);
public static String getOrderBy(String tablePrefix, String column, Boolean asc, String[] sortableColumns);
```

`params` 需包含 `page/size/sidx/sord`（缺省走 PageModel 默认值）+ 业务参数。内部：`QueryModel.of(params)` → 白名单校验/生成 order → `GridService.query(sql, pageModel, params, countSQL)`。

## GridHttpServletRequestUtils（Web 层 ⚠️）

```java
public static Grid<Map<String, Object>> list(String sql, HttpServletRequest request);
public static Grid<Map<String, Object>> list(String sql, HttpServletRequest request, String countSQL);
public static Grid<Map<String, Object>> list(String sql, HttpServletRequest request, Map<String, Object> extendParams, String countSQL);
public static List<BigDecimal> numericObject(String sql, HttpServletRequest request[, Map<String, Object> extendParams]);
```

`HttpServletRequestUtils.getParameterMap(request, extendParams)` 把请求参数（数组值保留为数组）与 extendParams 合并（extendParams 覆盖同名）。Controller 里一行完成分页：

```java
@GetMapping("users")
public Grid<Map<String, Object>> list(HttpServletRequest request) {
    return GridHttpServletRequestUtils.list(
            "SELECT id, name, create_time FROM t_user WHERE name LIKE :name",
            request, null, null);   // ⚠️ 此入口白名单=sidx 自身；需要限制排序列时改走 GridUtils.list 四参版
}
```

## AbstractTableGridService / DefaultTableGridService（报表封装 ⭐）

```java
public abstract class AbstractTableGridService {
    public abstract String getListSQL();          // 必须：查询 SQL
    public String getCountSQL()  { return null; } // 可选：自定义 count（null → 自动生成）
    public String getSummarySQL(){ return null; } // 可选：合计 SQL（summary 时 null 抛异常 "getSummarySQL need overwrite"）

    public Grid<Map<String, Object>> list(Map<String, Object> params);
    public Grid<Map<String, Object>> list(HttpServletRequest request);
    public Grid<Map<String, Object>> list(HttpServletRequest request, Map<String, Object> extendParams);
    public List<BigDecimal> summary(HttpServletRequest request[, Map<String, Object> extendParams]);
    public List<BigDecimal> summary(Map<String, Object> params);
}

public class DefaultTableGridService extends AbstractTableGridService {
    public DefaultTableGridService(String listSQL);
    public DefaultTableGridService(String listSQL, String countSQL);
    public DefaultTableGridService(String listSQL, String countSQL, String summarySQL);
}
```

适合“SQL 固定、参数来自请求”的列表页：把 Service 注册为 Bean（或方法内 new `DefaultTableGridService`），Controller 直接 `list(request)`。`summary` 的 SQL 要求单行、数字列（配合方言 `summaryFun(column)` 生成 `CONVERT/CAST(SUM(col) AS ...)`）。

## QueryModel / PageModel

```java
@Data public class PageModel {
    private Integer page = 1; private Integer size = 15; private String sidx; private String sord;
    public PageModel(); public PageModel(int page, int size); public PageModel(int page, int size, String sidx, String sord);
    public Map<String, Object> toMap();
    public boolean isPageQueryModel();  // size != -1
    public boolean isAllQueryModel();   // size == -1
}

@Data public class QueryModel {
    private PageModel pageModel; private Map<String, Object> params;
    public static QueryModel of(Map<String, Object> requestMap);  // 抽取 page/size/sidx/sord；params=requestMap 原样
    public static QueryModel of(PageModel pageModel);             // params = pageModel.toMap()
}
```

## PaginationHelper（UI 辅助 ⚠️）

```java
public static Map<String, Long> limitPages(long total, long displayPage, long activePage);
// 返回 {"startPage": x, "endPage": y} —— 页码条只显示 displayPage 个页码时的窗口，与 SQL 无关
```

## 分页 SQL 生成（各方言，取证 dialect 源码）

| 方言 | 分页包装 | LIKE 改写 | summaryFun |
|---|---|---|---|
| MySQL5/MySQL8 | `SELECT * FROM (sql) temp_ [ORDER BY sidx sord] LIMIT (page-1)*size, size` | `UPPER(col) LIKE CONCAT('%',UPPER(:p),'%') escape '\\'` | `CONVERT(sum(col), DECIMAL(20,3))` |
| PostgreSQL | `... LIMIT size OFFSET (page-1)*size` | 同上，`escape '\'` | `CAST(SUM(col) AS NUMERIC(20,3))` |
| Oracle10g/11c | ROWNUM 双层包装 `SELECT * FROM (SELECT A.*, ROWNUM RN FROM (...) A WHERE ROWNUM <= end) WHERE RN > start` | `'%'||UPPER(:p)||'%'` | `CAST(SUM(col) AS NUMBER(20,3))` |
| SQLServer2012 | `... [ORDER BY sidx sord | ORDER BY (SELECT NULL)] OFFSET (page-1)*size ROWS FETCH NEXT size ROWS ONLY` | `'%' + UPPER(@p) + '%'`（⚠️ 生成 `@name`，与命名参数 `:name` 体系不一致，SQLServer LIKE 场景[需要确认]） | `CAST(SUM(col) AS DECIMAL(20,3))` |
| SQLite | `... LIMIT (page-1)*size, size` | 同 MySQL，`escape '\'` | `CAST(SUM(col) AS NUMERIC)` |

排序生成 `getOrderBy`：MySQL5 追加稳定序 `temp_.col asc, temp_.id ASC`（因此**查询列必须包含 id**，GridUtils javadoc 原文）；其余方言仅 `temp_.col asc|desc`。

## 安全注意（源码依据）

1. `sidx/sord` **字符串拼接**进 ORDER BY（`AbstractDialect.pageSql/wrapSordString`），不是绑定参数。唯一防线是 `GridUtils.sortable()` 白名单；两参/三参 `list` 的白名单取自请求自身，等于不设防。**对外接口必须用四参 `list(sql, params, countSQL, sortableColumns...)` 显式白名单**。
2. 业务参数一律走 `:name`/`?` 绑定；`${name}`（`SQLParamCleaner.replaceVars`）是原样替换，禁止承接用户输入。
3. LIKE 值中 `%`/`_` 由框架转义（`likeEscape`），业务不要预拼 `%`。
