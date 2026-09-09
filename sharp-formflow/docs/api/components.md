# 内置组件完整参考（form/cpn/，21 个实现类）

> 全部取证自源码。组件类均为 🚫 内部实现：由 `CpnManager` 按 `CpnTypeEnum` 分派，业务**不要直接 new 或注入**（手动 new 会跳过 `afterPropertiesSet` 的泛型解析，parseValue 行为损坏）。
> 每个组件都是 `@Component extends AbstractCpn<T>`，T 即「提交/回显值的 Java 类型」。

## 通用行为（AbstractCpn）

- `parseValue(Object)`（DB/实体值 → UI 值 T）：String 输入时按 T 类型转换——T=String 原样；T=Integer 走 `Integer.valueOf`；T=BigDecimal 走 `new BigDecimal`；其他 T 走 JSON 反序列化（非 `[`/`{` 开头的字符串会被包成 `["value"]` 再反序列化）。null → null。
- `getStringValue(T)`（UI 值 → 存储字符串）：String 原样，其他 `JsonUtils.toJson`。
- `httpConverter(Object)`（HTTP 提交值 → T）：默认 String 走 parseValue，非 String 强转 T。
- `valid(T, options)`：值非空且配置了 options 时，值必须在 `options[].name` 集合内，否则抛「没有找到正确的选项」。
- `check(CpnConfigurer)`：options 的 **label 不允许重复**，否则抛「选项不能重复」。
- `validatorSupports()` = REQUIRED（所有组件都支持必填） + `cpnValidators()` 类型 + `internalValidatorSupports()`。

## 组件总表

| 组件类 | CpnTypeEnum | 值类型 T（JSON 形态） | 可配置校验（internalValidatorSupports） | 自带校验（cpnValidators，不可移除、自动执行） | 选项/字典 | 文件依赖 | 典型场景 |
|---|---|---|---|---|---|---|---|
| Text | TEXT | String | LENGTH | - | - | - | 姓名、单行输入 |
| TextArea | TEXTAREA | String | LENGTH | - | - | - | 备注、多行输入 |
| NumberText | NUMBER_TEXT | String（数字字符串；BigDecimal 输入会 stripTrailingZeros 转 plain string） | TEXT_NUMBER_SIZE | NumberRegex | - | - | 数字但以文本存储/展示 |
| IntegerNumber | INTEGER_NUMBER | Integer（JSON 数字；空串→null） | SIZE | - | - | - | 数量、年龄 |
| Currency | CURRENCY | BigDecimal（JSON 数字/字符串） | SIZE | NumberRegex | - | - | 金额 |
| Date | DATE | String `yyyy-MM-dd`（parseValue 兼容 LocalDate/java.sql.Date/java.util.Date 输入） | - | DateRegex | - | - | 日期选择（内置模板挂 bootstrap-datepicker） |
| Time | TIME | String `HH:mm` | - | TimeRegex | - | - | 时间 |
| Email | EMAIL | String | - | EmailRegex + Length(max=32) | - | - | 邮箱 |
| Mobile | MOBILE | String | - | MobileRegex | - | - | 手机号 |
| Hidden | HIDDEN | String | - | - | - | - | 隐藏传值 |
| Label | LABEL | String | - | - | - | - | 纯展示文本 |
| Switch | SWITCH | String `"1"`/`"0"`（parseValue 兼容 Boolean→"1"/"0"、Enum→toString；httpConverter 用 `com.rick.common.util.StringUtils.toBoolean(value)` 判定） | - | - | - | - | 是/否开关 |
| CheckBox | CHECKBOX | List\<String\>（JSON `["a","b"]`；单个字符串自动包装；页面提交多值为 List） | - | - | options / datasource 字典 | - | 多选框组 |
| Radio | RADIO | String（Enum 输入→toString） | - | - | options / datasource | - | 单选框组 |
| Select | SELECT | String（Enum→toString、Number→String.valueOf） | - | - | options / datasource | - | 下拉单选 |
| MultipleSelect | MULTIPLE_SELECT | List\<String\>（valid/httpConverter/parseValue 全委托 CheckBox） | - | - | options / datasource | - | 下拉多选 |
| GroupSelect | GROUP_SELECT | String（parseValue 委托 Select） | - | - | options / datasource | - | 分组下拉单选 |
| SearchSelect | SEARCH_SELECT | String（parseValue 委托 Select） | - | - | options / datasource | - | 可搜索下拉单选 |
| SingleImage | SINGLE_IMAGE | Map\<String,Object\>（JSON 对象：文件元数据，含 id/url/fullName/name/extension/size/groupName/path 等，来自 fileupload /documents/upload 响应） | - | - | - | sharp-fileupload（前端 HTTP 集成） | 单图/单文件 |
| Attachment | FILE | List\<Map\<String,Object\>\>（JSON 数组，元素同上） | - | - | - | sharp-fileupload（前端 HTTP 集成） | 多附件 |
| Table | TABLE | List\<List\>（JSON 二维数组 `[["r1c1","r1c2"],["r2c1","r2c2"]]`，单元格一律字符串；httpConverter 过滤整行全空的行） | - | -（valid() 改为执行 cpnValidators，默认空） | - | - | 可编辑子表（前端 editable-table.js） |

⚠️ `SINGLE_CHECKBOX`（枚举里的「单选」）**没有实现类**：`CpnManager.getCpnByType` 返回 null。经 `POST /forms/configurers`（CpnConfigurerService.saveOrUpdate 会调 `cpn.check`）**定义时即 NPE**；经 `POST /forms/configs`（FormCpnService 链路不调 check）**定义能存进去，但渲染/提交时必 NPE**（getFormBO 中 `cpn.parseValue` 处）。**禁止使用**。

## 重点组件说明

### CheckBox / MultipleSelect（选项多值）

- **值校验**：提交值集合必须是 options.name 集合的子集（`SetUtils.difference`）。
- **parseValue 兼容丰富输入**（CREATE_TABLE 宽表回显场景）：
  - `Collection<Enum>` → 各枚举 name 列表；`Collection<DictValue>` → 各 code 列表；`Collection<String>` 原样；其他元素类型抛 `"xxx无法决断，请指定字段 map"`。
  - `Boolean` → `["true","1"]` / `["false","0"]`（两种表示都给，便于选项名匹配）。
  - 单个 Enum → `[name]`；单个 DictValue → `[code]`。
- **存储**：JSON 数组字符串，如 `["java","sql"]`。

### Table（可编辑表格 = 子表单）

- 值结构是 **List\<List\>（二维数组），不是 List\<Map\>**：每行一个 List，单元格按列顺序排布，全部是字符串。**没有列名 key**——列语义由 `configurer.additionalInfo.columns` 的顺序约定。
- 列定义（内置模板消费方式，tpl/form.html L96-99、L143-147）：`additionalInfo.columns` 为数组，每项含 `label`（表头文本）和可选 `validatorProperties`（如 `{"Required.required": true}` 控制表头红星）；前端 `$('.'+name).editableTable({columns: columns.length})` 初始化，`editableTable('getValue')` 返回二维数组。列对象的完整 schema `[需要确认]`（模板只消费 label/validatorProperties/长度）。
- 提交时整行全空的行被过滤。

### Attachment（FILE）/ SingleImage（SINGLE_IMAGE）

- **与 sharp-fileupload 的集成方式**：纯前端。内置模板中 `<input type=file>` change 时 `$.ajaxFileUpload` POST 到 **`/documents/upload?name=...`**（sharp-fileupload `DocumentController.fileUpload`，multipart），响应 `Result<List<Document>>`；JS 把返回的文件对象数组 JSON.stringify 后写入同名**隐藏文本域**，随表单一起提交。
- **存的是完整文件元数据 JSON（含 id 和 url）**，不是仅 Document ID。Document/FileMeta 字段（源码核实）：id、name、extension、contentType、size、groupName、path、url、fullName（getter 计算 name+extension）、createBy/createTime/updateBy/updateTime/deleted。
- Java 侧（Attachment/SingleImage 类）**零 fileupload 类型引用**，只做 JSON↔Map/List\<Map\> 转换。删除附件仅是前端把该对象从 JSON 数组里过滤掉（deleteAttachment）。
- ⚠️ 内置模板引用的 `/plugins/ajaxfileupload.js` 路径与实际静态资源 `/ajaxfileupload.js` 不符（404），使用附件组件时需自行修正模板引用或拷贝资源。

### 选项类组件的选项来源（逐个核实结论）

| 来源 | 机制 | 证据 |
|---|---|---|
| 静态配置 | `CpnConfigurer.options = [{name,label}]`；CpnOption 只填 label 时 name=label | CpnConfigurer.CpnOption |
| 字典（sharp-meta） | `CpnConfigurer.datasource` = 字典 type；**查询时** `CpnConfigurerDAO.datasourceOptionsHandler` 调 `DictService.getDictByType(type)`，用 Dict(name,label) **覆盖** options（不写回库）。⚠️ 仅 Map 参数查询路径生效（selectByIds/select(example)），selectById 绕过 | CpnConfigurerDAO.select 覆写 |
| 远程接口 | **不存在**。SearchSelect 的“搜索”仅是前端交互语义，Java 侧与 Select 等价，无远程数据源代码 | SearchSelect.java |

## 组件与校验的适配矩阵（validatorSupports 汇总）

| 组件 | REQUIRED | LENGTH | SIZE | TEXT_NUMBER_SIZE | 自带格式校验 |
|---|---|---|---|---|---|
| Text / TextArea | ✓ | ✓ | - | - | - |
| NumberText | ✓ | - | - | ✓ | NumberRegex |
| IntegerNumber | ✓ | - | ✓ | - | - |
| Currency | ✓ | - | ✓ | - | NumberRegex |
| Date | ✓ | - | - | - | DateRegex |
| Time | ✓ | - | - | - | TimeRegex |
| Email | ✓ | - | - | - | EmailRegex + Length(32) |
| Mobile | ✓ | - | - | - | MobileRegex |
| 其余（Hidden/Label/Switch/CheckBox/Radio/Select/MultipleSelect/GroupSelect/SearchSelect/SingleImage/Attachment/Table） | ✓ | - | - | - | - |

给组件配置了其不支持的校验器时，`CpnInstanceProcessor.valid` 里 `cpn.hasValidator(validator)` 不通过 → **静默跳过，不报错**（定义期兼容性检查 `checkIfAvailable` 是私有方法且未被调用）。
