# sharp-meta

数据字典（Dict）+ 系统参数（Property）两大能力的基础模块。

## 模块定位

**解决什么**：
- 字典：把「编码 → 名称」的映射（如 `MATERIAL_TYPE: HIBE → 混合物`）集中管理。数据来源三选一可混用：`sys_dict` 表、yml 配置（`dict.items`）、程序提供（实现 `DictDOSupplier`）。全量加载进内存缓存，提供查询、Bean Validation 校验（`@DictType`）、label 回填（`DictUtils.fillDictLabel`）与值→展示文本转换（`DictConverter` 等）。
- 系统参数：`sys_property` 表 + yml（`props.items`）的键值配置，启动时全量加载进内存，`PropertyService.getProperty/setProperty` 读写。

**适用**：需要下拉选项数据源、字典合法性校验、编码转中文展示、运行时可变的全局键值参数的场景。sharp-formflow 的组件数据源（`CpnConfigurer.datasource`）即基于 `DictService.getDictByType`。

**不适用**：大数据量/高频变更的参考数据（本模块是启动时全量内存缓存，不是按需查库）；带层级/多语言的复杂主数据（模型只有 type/name/label/sort/remark）。

## 使用原则

- 字典查询统一走 `DictService`（或静态 `DictUtils`，注意其初始化前提）；参数读写统一走 `PropertyService`。
- 不要绕过服务自己写查询+缓存逻辑。例外：sharp-database 的 `@Select` 注解直接查 `sys_dict` 回填 label 是仓库既有用法（见 sharp-test `ComplexModel.materialType`），可以使用。
- 实体中字典字段用 `DictValue` + `@DictType(type="...")` 声明，不要硬编码字典值字符串散落业务逻辑。
- 直接改了 `sys_dict` 表后必须调 `DictService.rebuild()`；直接改了 `sys_property` 表**没有任何刷新手段**，只能重启应用（代码事实，见 API.md）。

## 源码阅读规则

```
默认不要扫描整个模块源码。
使用模块时：
1. 优先阅读 CLAUDE.md
2. 再阅读 API.md
3. 如果 API.md 已经可以解决问题，不要继续阅读源码
4. 只有在文档无法解决问题、排查 Bug 或确认特殊行为时，才阅读相关源码
```

## API 文档索引

- [API.md](API.md)：全部公开 API 的签名、参数、返回值、异常、示例、推荐等级；数据模型与表结构；配置项；扩展点；Common Mistakes。
- [ARCHITECTURE.md](ARCHITECTURE.md)：依赖链、缓存与一致性策略、自动配置生效条件、扩展点机制、已知限制。

## 禁止行为

- 禁止调用/依赖 `DictServiceImpl`、`PropertyServiceImpl`、`MetaServiceAutoConfiguration`、`config/validator` 包下所有校验器类（框架通过 `@DictType` 注解调度，业务代码不直接使用）。
- 禁止读写 `DictUtils.dictMap`、`PropertyUtils.map` 这两个静态缓存字段本身（只允许通过公开方法访问）。
- 禁止使用 `DictValidator`、`Dict2Validator`（未注册进 `@DictType` 的 `validatedBy` 列表，仓库内无任何调用，遗留代码）。
- 禁止用 `@Value("${...}")` 读取 `sys_property` 中的配置——`KeyValueProperties` 不是 `PropertySource`，`@Value` 读不到数据库值（见 API.md「系统参数」）。

## 引入清单

```gradle
// group=com.rick.meta, version=libs.versions.sharp（当前 0.0.1-SNAPSHOT）
implementation "com.rick.meta:sharp-meta:0.0.1-SNAPSHOT"
// sharp-meta 对 sharp-database 是 implementation 依赖，不传递。
// 若业务代码需要直接使用 TableDAO 等 sharp-database 类型，需自行引入：
implementation project(":sharp-database")
```

生效前提：容器中存在唯一 `TableDAO` bean（即已配置 `DataSource`，sharp-database 自动配置生效）。

需要的表（模块内**没有**建表脚本，需业务方自建；两表缺失时模块降级运行、仅打 warn 日志）：

| 表 | 用途 | 建法 |
|---|---|---|
| `sys_dict` | 字典数据 | 可用 sharp-database 的 `TableGenerator.createTable(Dict.class)`，或按 API.md 字段表手写 DDL |
| `sys_property` | 系统参数 | 手写 DDL（`PropertyDO` 不是 `@Table` 实体，TableGenerator 不适用），至少含 `name`、`value` 两列 |

最小配置（可不建任何表、不写任何配置——字典缓存为空、参数缓存为空，查询返回空/null）：

```yaml
dict:
  items:
    - type: UNIT              # 三选一：map / sql / list（同时配置时优先级 map > sql > list）
      map:
        EA: 个
        KG: 千克
props:
  items:
    my.switch: "true"
```
