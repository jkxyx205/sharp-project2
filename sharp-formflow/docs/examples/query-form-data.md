# 示例：读取用户提交的表单数据

> ⚠️ 示例依据模块内部真实调用链（AjaxInstanceController.get → FormService.getFormBOByIdAndInstanceId → FormCpnValueDAO/EntityDAO）改写。仓库内暂无外部业务调用样例。

## 1. 单实例读取（模块提供的唯一读取 API）

### Java

```java
private final FormService formService;

FormBO bo = formService.getFormBO(formId, instanceId);   // ⭐ 推荐

// 方式一：按字段名取值（UI 强类型）
Map<String, Object> data = bo.getData();
String userName = (String) data.get("userName");                  // TEXT → String
Integer qty     = (Integer) data.get("qty");                      // INTEGER_NUMBER → Integer
BigDecimal pay  = (BigDecimal) data.get("salary");                // CURRENCY → BigDecimal
List<String> sk = (List<String>) data.get("skills");              // CHECKBOX → List<String>
List<List> rows = (List<List>) bo.getData().get("detail");        // TABLE → 二维数组，单元格 String
List<Map<String, Object>> files =
        (List<Map<String, Object>>) data.get("resume");           // FILE → 文件元数据列表(id/url/fullName…)

// 方式二：带定义信息遍历（渲染/导出场景）
for (FormBO.Property p : bo.getPropertyList()) {
    String label = p.getConfigurer().getLabel();
    Object value = p.getValue();
}
```

值类型 = 各组件 `Cpn.parseValue` 的产物（完整对照表见 docs/api/components.md）。**不是数据库原始字符串**——INNER_TABLE 里存的 String/JSON 已被反序列化。

### HTTP

```
GET /forms/ajax/{formId}/{instanceId}   → FormBO JSON（value 同上是强类型 JSON）
```

## 2. 列表 / 条件查询 —— 模块未提供，按存储策略自行实现

### INNER_TABLE（EAV，sys_form_cpn_value）

数据形态：一个实例 = N 行 `(instance_id, form_id, config_id, form_cpn_id, value)`，value 全是字符串/JSON。

- 取某表单全部实例 id：`SELECT DISTINCT instance_id FROM sys_form_cpn_value WHERE form_id = ?`，再逐个 `getFormBO(formId, instanceId)`（小数据量可行；每次调用有 FormCache 深克隆开销，勿在大循环里高频调用）。
- 按字段值过滤（行转列，示例 PostgreSQL）：

```sql
SELECT v.instance_id,
       max(CASE WHEN c.name = 'userName' THEN v.value END) AS user_name,
       max(CASE WHEN c.name = 'entryDate' THEN v.value END) AS entry_date
FROM sys_form_cpn_value v
JOIN sys_form_configurer c ON c.id = v.config_id
WHERE v.form_id = :formId
GROUP BY v.instance_id
HAVING max(CASE WHEN c.name = 'entryDate' THEN v.value END) >= '2026-01-01'
```

⚠️ 注意：value 是文本，数字/日期比较按字符串语义（DATE 的 yyyy-MM-dd 与 NUMBER 的定长场景可用，金额/变长数字比较不可靠）；CHECKBOX/TABLE 存的是 JSON 数组文本。**复杂查询需求应在定义表单时选 CREATE_TABLE**。
（SQL 为本模块表结构上的通用写法示意，表列名依据实体注解反推，执行前请对照实际 DDL。）

### CREATE_TABLE（业务宽表）

数据就在业务自己的表里（列 = CpnConfigurer.name 的 camelToSnake），直接用业务 EntityDAO / SQL 查询：

```java
// form.repositoryName = "entryDAO" 时
private final EntryDAO entryDAO;                 // 业务自己的 EntityDAOImpl 子类
List<Entry> list = entryDAO.select("entry_date >= ?", LocalDate.of(2026, 1, 1));
```

回显仍可用 `getFormBO(formId, instanceId)`（FormService 会 getBean(repositoryName) 读业务表并经 CpnValueConverter/parseValue 转 UI 类型）。

## 3. 值类型桥接：CpnValueConverter

CREATE_TABLE 下 DB 列类型与组件期望不符时（典型：列是 `LocalDateTime`，组件是 TEXT/DATE 期望字符串），配置转换器：

```java
// 模块内置：Bean 名 dateTimeToStringConverter（LocalDateTime → "yyyy-MM-dd HH:mm:ss"）
CpnConfigurer.builder()
        .name("createTime").label("创建时间").cpnType(CpnTypeEnum.TEXT)
        .cpnValueConverterName("dateTimeToStringConverter")
        .build();

// 自定义：注册 Bean，名字即 cpnValueConverterName
@Component("deptIdToNameConverter")
public class DeptIdToNameConverter implements CpnValueConverter<Long, String> {
    @Override public String convert(Long deptId) { /* 查部门名 */ return name; }
}
```

执行位置：`getFormBOByIdAndInstanceId` 中，取到原始值后、`Cpn.parseValue` 前（FormService L172-178）。⚠️ 仅作用于**读取回显**方向；提交方向没有 converter 钩子（提交侧用 FormAdvice.beforeInstanceHandle 改写 values）。
