# 示例：定义一个表单

> ⚠️ 本目录所有示例依据**模块内部真实调用链**（FormController → FormCpnService → DAO；AjaxInstanceController → FormService）改写为业务方视角。仓库内暂无外部业务调用样例。

## 方式 A：HTTP 接口定义（对应 FormController.formCpnMapping 真实链路）

```http
POST /forms/configs
Content-Type: application/json

{
  "form": {
    "name": "入职登记",
    "code": "entry-form",
    "storageStrategy": "INNER_TABLE",
    "tplName": "tpl/form"
  },
  "configs": [
    {
      "name": "userName",
      "label": "姓名",
      "cpnType": "TEXT",
      "placeholder": "请输入姓名",
      "validators": [
        {"validatorType": "REQUIRED", "required": true},
        {"validatorType": "LENGTH", "min": 2, "max": 20}
      ]
    },
    {
      "name": "entryDate",
      "label": "入职日期",
      "cpnType": "DATE"
    },
    {
      "name": "dept",
      "label": "部门",
      "cpnType": "SELECT",
      "datasource": "dept"
    },
    {
      "name": "skills",
      "label": "技能",
      "cpnType": "CHECKBOX",
      "options": [
        {"name": "java", "label": "Java"},
        {"name": "sql", "label": "SQL"}
      ]
    },
    {
      "name": "detail",
      "label": "工作经历",
      "cpnType": "TABLE",
      "additionalInfo": {
        "columns": [
          {"label": "公司", "validatorProperties": {"Required.required": true}},
          {"label": "职位"}
        ]
      }
    }
  ]
}
```

响应：`{"success":true,"code":200,"message":"OK","data":"<formId字符串>"}`。

要点（均有源码依据）：
- `configs` 数组顺序 = 渲染顺序（orderNum 按 0..n 生成）。
- `cpnType` 必须是 CpnTypeEnum 枚举名字符串（TEXT/DATE/SELECT/CHECKBOX/TABLE…），写错 → 反序列化 400；`SINGLE_CHECKBOX` 是合法枚举但无实现类：本接口（/forms/configs）能存成功，之后渲染/提交必 500 NPE，勿用（详见 docs/api/components.md）。
- `dept` 字段用 `datasource`（sharp-meta sys_dict 的 type）替代 options，读取时 DictService 动态填充。
- validators 只有 REQUIRED/LENGTH/SIZE/TEXT_NUMBER_SIZE 会真正执行（其余被 hasValidator 跳过，见 docs/api/validation.md）。
- `tplName: "tpl/form"` 必须显式设置——不设则页面接口默认视图 `tpl/form/form` 不存在。
- `name` 落库后**不可更新**（@Column(updatable=false)）。

## 方式 B：Java 代码定义（注入 FormCpnService）

```java
@Service
@RequiredArgsConstructor
public class EntryFormInitializer {

    private final FormCpnService formCpnService;   // ⭐ 定义态入口（自动配置的 Bean）

    public Long defineEntryForm() {
        Form form = Form.builder()
                .name("入职登记")
                .code("entry-form")
                .storageStrategy(Form.StorageStrategyEnum.INNER_TABLE)
                .tplName("tpl/form")
                .build();

        List<CpnConfigurer> configs = List.of(
                CpnConfigurer.builder()
                        .name("userName").label("姓名").cpnType(CpnTypeEnum.TEXT)
                        .validatorList(List.of(new Required(), new Length(2, 20)))
                        .build(),
                CpnConfigurer.builder()
                        .name("entryDate").label("入职日期").cpnType(CpnTypeEnum.DATE)
                        .build(),
                CpnConfigurer.builder()
                        .name("skills").label("技能").cpnType(CpnTypeEnum.CHECKBOX)
                        .options(List.of(new CpnConfigurer.CpnOption("java", "Java"),
                                         new CpnConfigurer.CpnOption("sql", "SQL")))
                        .build()
        );

        formCpnService.saveOrUpdateByConfigurer(form, configs);  // 事务内：存 form + configs + 关联
        return form.getId();
    }
}
```

要点：
- `validatorList` 与 `validators` 二选一：set validatorList 后 `getValidators()` 自动转 JSON 落库（CpnConfigurer 源码）。
- `CpnOption(label)` 单参构造 name=label。
- ❗ 定义完成后若该 formId 之前被读取过（已进 FormUtils 缓存），**必须** `FormUtils.update(formId, null)` 或重启，否则运行态仍用旧定义。

## 方式 C：组件库复用（CpnConfigurerController + FormController 1.4 接口）

```http
POST /forms/configurers          → data: ["<configId1>","<configId2>",...]
POST /forms/{formId}/configs     body: [<configId1>,<configId2>]
```
同一批字段定义可挂到多个表单（CpnConfigurer 与 Form 是多对多）。⚠️ id 数组顺序 = 展示顺序；传入无效 id 会 NPE。

## 存储策略怎么选

| 需求 | 选择 |
|---|---|
| 快速上线、结构多变、只按实例整取 | INNER_TABLE（零建表） |
| 需要 SQL 条件查询/报表/与业务表关联 | CREATE_TABLE：自建表 + 实体 + `XxxDAO extends EntityDAOImpl` Bean，`form.repositoryName = "xxxDAO"`（Bean 名），列属性名与 CpnConfigurer.name 一致 |
| 只渲染+校验，数据自存（如 Mongo） | NONE + FormAdvice（afterInstanceHandle 落库；insertOrUpdate 返回 true 接管写入） |
