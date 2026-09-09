# 示例：标准分页查询

> API 细节与方言行为见 `docs/api/pagination.md`。当前工作区下游模块未包含 Grid 的业务调用点，以下示例依据模块公开 API（`GridService/GridUtils/GridHttpServletRequestUtils/AbstractTableGridService`）与 sharp-test 配置编写，签名均已对照源码核实。

## 前端请求约定

```
GET /users?page=1&size=15&sidx=create_time&sord=desc&name=Rick&status=1
```

- `page/size/sidx/sord` 是框架保留分页参数；其余（`name/status`...）是业务过滤参数，SQL 里用 `:name`/`:status` 引用。
- 参数值为 null/空串 → 对应条件**自动从 SQL 剔除**（无需业务写 if）。
- `size=-1` → 不分页返回全部。

## 写法一：Controller 直接用 GridHttpServletRequestUtils（最短）

```java
@RestController
@RequestMapping("users")
public class UserQueryController {

    @GetMapping
    public Grid<Map<String, Object>> list(HttpServletRequest request) {
        return GridHttpServletRequestUtils.list(
                "SELECT id, name, age, create_time FROM t_user " +
                "WHERE name LIKE :name AND age > :age",
                request);
        // total（Grid.records）由框架自动 count，不用自己写 count SQL
    }
}
```

## 写法二：Service 用 GridService（可映射为实体/DTO，推荐）

```java
@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final GridService gridService;

    public Grid<User> page(Map<String, Object> params) {   // params 可来自 @RequestParam Map 或请求体
        QueryModel qm = QueryModel.of(params);             // 拆出 PageModel(page/size/sidx/sord)
        return gridService.query(
                "SELECT id, name, age, create_time FROM t_user " +
                "WHERE name LIKE :name AND status = :status",
                qm.getPageModel(),
                qm.getParams(),
                User.class);                               // 行映射到类；不传则为 Map<String,Object> 行
    }
}
```

## 写法三：GridUtils + 排序白名单（对外接口必须这样限制 sidx）

```java
public Grid<Map<String, Object>> page(Map<String, Object> params) {
    return GridUtils.list(
            "SELECT id, name, create_time FROM t_user WHERE name LIKE :name",
            params,
            null,                                  // countSQL：null = 自动生成
            "id", "name", "create_time");          // ✅ 允许排序的列白名单（查询列必须含 id，MySQL 方言会追加 id 稳定序）
}
```

## 写法四：固定报表 = AbstractTableGridService / DefaultTableGridService

```java
@Service
public class UserReportService extends AbstractTableGridService {

    @Override
    public String getListSQL() {
        return "SELECT id, name, age FROM t_user WHERE name LIKE :name";
    }

    @Override
    public String getSummarySQL() {           // 可选：合计接口
        return "SELECT sum(age) FROM t_user WHERE name LIKE :name";
    }

    // getCountSQL() 不覆写 → 自动 SELECT COUNT(*) FROM (listSQL 去 order by) temp
}

// Controller
@GetMapping("report")
public Grid<Map<String, Object>> report(HttpServletRequest request) {
    return userReportService.list(request);
}

@GetMapping("report/summary")
public List<BigDecimal> summary(HttpServletRequest request) {
    return userReportService.summary(request);   // 数字单行 → 按列顺序返回
}
```

简单场景也可不建类：`new DefaultTableGridService(listSQL, countSQL, summarySQL).list(params)`。

## 返回结构（Grid JSON）

```json
{
  "page": 1,
  "pageSize": 15,
  "records": 42,        // total，框架自动 count
  "totalPages": 3,
  "rows": [ { "id": 1, "name": "Rick", ... } ],
  "additionalInfo": null // query 路径不设置
}
```

## 自定义 count / 多条件 / 模糊 / IN

```java
// 复杂 SQL 默认 count 包装慢时，传优化过的 countSQL（占位参数与主 SQL 相同）
gridService.query(listSQL, pageModel, params,
        "SELECT count(*) FROM t_user WHERE name LIKE :name");

// 多条件动态查询：全部写成 :参数 即可，空参自动剔除
"SELECT id, name FROM t_user WHERE name LIKE :name AND status = :status AND create_time >= :begin"

// 模糊：写 LIKE :name，框架改写为 UPPER(col) LIKE '%值%'（大小写不敏感 contains，% _ 自动转义）
// 不要自己拼 '%'

// IN：集合直接放 params
params.put("ids", List.of(1L, 2L, 3L));
// SQL: WHERE id IN (:ids)  → 自动展开为 IN (:ids0, :ids1, :ids2)；超过 1000 自动拆 OR 组
```

## 与实体级联的组合

Grid 路径（`GridService/GridUtils`）**不做实体级联加载**（即使 `clazz` 传实体类，`@OneToMany` 等字段不会回填）。需要“分页 + 级联”时：

```java
Grid<Map<String, Object>> grid = GridUtils.list(sql, params);       // 1. 分页拿 id 页
List<Long> ids = grid.getRows().stream().map(r -> (Long) r.get("id")).toList();
List<User> users = ids.isEmpty() ? List.of() : userDAO.selectByIds(ids); // 2. 级联加载（selectByIds 参数 @NotEmpty）
// 注意：selectByIds 不保序，需要按页内顺序时用 ids 重排
```
