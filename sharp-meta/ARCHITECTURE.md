# sharp-meta 架构

## 1. 依赖链位置

```
sharp-common ← sharp-database ← sharp-meta ← sharp-formflow
                                     ↑
                                 sharp-test（示例/测试）
```

- **向下**：`implementation project(":sharp-database")`（非 api，不传递）。持久化全部委托 sharp-database 的 `TableDAO`（原生 SQL/命名参数查询、`selectForKeyValue`），不引入 JPA/MyBatis。`Dict` 实体继承 sharp-database 的 `BaseEntity<Long>`，`DictDAO` 继承其 `EntityDAOImpl`。
- **向上**：sharp-formflow 依赖本模块，把字典作为表单组件数据源——`CpnConfigurer.datasource` 存字典 type，`CpnConfigurerDAO` 查询后调 `dictService.getDictByType(datasource)` 生成下拉 options；`CheckBox` 组件识别 `DictValue` 类型字段并取其 `code`。
- 对 sharp-common 的依赖经由 sharp-database 传递（`JsonUtils`、`Time2StringUtils`、`ObjectUtils`、`EntityWithCodePropertyDeserializer` 等）。

## 2. 两条能力线

### 2.1 字典线（dict 包）

```
数据来源(3 种，可混用)                     缓存                      消费端
┌─ sys_dict 表 (SELECT_SQL)  ─┐
├─ yml dict.items (map/sql/list) ─┼→ DictUtils.dictMap ←─ DictService (查询/rebuild) ⭐ 主入口
└─ DictDOSupplier bean (可选) ─┘   (静态 HashMap)   ├─ DictUtils.fillDictLabel (label 回填)
                                                    ├─ @DictType 校验器 (Bean Validation)
    DictDAO (EntityDAOImpl<Dict,Long>)              ├─ DictConverter / ArrayDictConverter (值→展示文本)
    = sys_dict 管理端 CRUD，写后需手动 rebuild        └─ sharp-formflow CpnConfigurerDAO (组件数据源)
```

- `DictServiceImpl` 实现 `InitializingBean`：`afterPropertiesSet() → rebuild()` 启动即全量构建缓存；`@PostConstruct init()` 把 `TableDAO` 静态注入 `DictUtils.tableDAO`（供 fillDictLabel 的 sql 回填路径使用）。
- **静态耦合点**：`DictUtils.dictMap` 是 public static 字段，由 `DictServiceImpl` 写入、被所有查询端读取。这是「非 Spring 环境 NPE」问题的根源（dictMap 初值 null）。

### 2.2 系统参数线（props 包）

```
yml props.items (KeyValueProperties) ─┐
                                      ├→ PropertyUtils.map ←─ PropertyService.get/setProperty
sys_property 表 (SELECT 全表，后放、覆盖同名 key) ─┘   (静态 HashMap)      PropertyUtils.getProperty (静态)

写入路径：setProperty = UPDATE sys_property（0 行则 INSERT）+ 同步更新 map
```

- 无 DAO 类：`PropertyServiceImpl` 内联 3 条 SQL，经 `TableDAO`/`JdbcTemplate` 执行。`PropertyDO` 仅存在于注释代码，现行无引用。
- `KeyValueProperties` **不是** Spring `PropertySource`，不进 Environment，`@Value` 读不到；只是缓存种子。

## 3. 自动配置生效条件全貌

`MetaServiceAutoConfiguration`（注册于 `AutoConfiguration.imports`）：

```
DataSourceAutoConfiguration (Boot)
  → SharpDatabaseAutoConfiguration  @ConditionalOnSingleCandidate(DataSource.class)
      提供 TableDAO bean (@ConditionalOnMissingBean，业务可覆盖)
  → MetaServiceAutoConfiguration    @ConditionalOnSingleCandidate(TableDAO.class)
                                    @AutoConfigureAfter(SharpDatabaseAutoConfiguration)
      内部类 @EnableConfigurationProperties({DictProperties, KeyValueProperties})
      beans: dictDAO, getDictService(DictService), getPropertyService(PropertyService),
             dictConverter, arrayDictConverter, boolConverter,
             sqlDateConverter, sqlTimestampConverter, localDateTimeConverter
```

- `DictDOSupplier` 以 `@Autowired(required = false)` 注入 `getDictService`——业务提供则用，不提供则为 null（rebuild 时跳过）。
- **所有 bean 均无 `@ConditionalOnMissingBean`**：业务不能覆盖 DictService/PropertyService/Converter；唯一可替换的上游是 `TableDAO`。
- 条件不满足（无 DataSource/TableDAO）时整条自动配置静默跳过：`DictUtils.dictMap = null`（使用即 NPE）、`PropertyUtils.map` 为空（getProperty 恒 null）、`@DictType` 校验器无法实例化（校验时报错）。
- 模块内的 `@Component` 注解（`Dict2Validator`、`DictValidator`、各 Converter）依赖应用组件扫描 `com.rick.meta` 才生效；仓库内应用（如 sharp-test 只扫 `com.rick.test`）不扫描该包，Converter 实际以自动配置的 `@Bean` 为准，两个 Validator 则完全不生效（也不在 `validatedBy` 列表中）。

## 4. 缓存与一致性策略

| | 字典（DictUtils.dictMap） | 系统参数（PropertyUtils.map） |
|---|---|---|
| 结构 | `Map<type, List<Dict>>`，public static，初值 **null** | `Map<name, value>`，static 包私有，初值空 HashMap |
| 加载时机 | 启动 `afterPropertiesSet → rebuild()` | 启动 `afterPropertiesSet`（先 yml 后 DB，DB 覆盖） |
| 刷新手段 | `rebuild()` / `rebuild(type)`，仅手动 | **无**；`setProperty` 单点更新；直改表需重启 |
| 并发安全 | 无同步。全量 rebuild 先换引用再填充，读线程可见中间态；单类型 rebuild 直接 put | 无同步 HashMap，读写并发理论上不安全（低频场景可接受） |
| 缺失降级 | `sys_dict` 查询异常 → warn 日志 + 空列表继续（表可不建） | `sys_property` 查询异常 → warn 日志继续（表可不建） |
| 数据优先级 | 同 type：yml items > (sys_dict + DictDOSupplier)（rebuild 中 yml 最后 put） | 同 name：sys_property > yml props.items |
| 一致性缺口 | DAO 写入不触发 rebuild（必须手动调用）；`is_deleted` 是否被过滤**取决于装配的 `TableDAO`**：默认 `TableDAOImpl` 不过滤（逻辑删除行仍进缓存），`ExtendTableDAOImpl` 会为单表查询自动追加 `AND is_deleted = false`（详见 API.md 2.1） | 多实例部署时 setProperty 只刷新本实例缓存 |

## 5. 扩展点

1. **自定义字典来源**：实现 `DictDOSupplier` 并注册为唯一 bean（范例：sharp-test `DictDOSupplierImpl` 把枚举和 `CodeDescription` 表注册为字典）。
2. **自定义值转换器**：实现 `ValueConverter<C,T>` 并声明 bean。无自动分派机制，调用方注入具体实现自行调用。
3. **自定义校验数据源**：`@DictType(sql = "... ? ...")`，校验兜底走任意 SQL（须返回单行、含 `label` 列），无需改代码。
4. **label 回填双通道**：`@DictType(type/sql)` + `DictUtils.fillDictLabel`（本模块），或 sharp-database `@Select` 直查 `sys_dict`（如 `ComplexModel.materialType`，查询时自动回填，无需手动调用）。
5. **不可扩展**：DictService/PropertyService 实现、校验器调度逻辑（无 ConditionalOnMissingBean / 无 SPI 注册表）。

## 6. 设计约束与已知限制

1. **全量内存缓存模型**：适合中小规模、低频变更字典；不支持按需加载、分页、过期。
2. **静态字段桥接 Spring bean**（DictUtils/PropertyUtils）：换来静态可调，代价是初始化时序依赖（bean 创建前使用 → NPE/null）、测试必须起容器、多 ApplicationContext 场景互相覆盖。
3. **`@DictType` 一注解三职责**（约束注解 + fillDictLabel 标记 + 校验兜底 SQL 载体），且 `DefaultDictValidator` 对不支持类型静默放行——类型误用无编译期/运行期报警。
4. **校验器硬依赖 Hibernate Validator**：`AbstractDictValidator` 强转 `ConstraintValidatorContextImpl`；且校验器无无参构造，只能经 Spring 管理的 Validator 触发。
5. **遗留/死代码**：`DictValidator`、`Dict2Validator`（不在 validatedBy、无调用点）；`PropertyDO`（仅注释引用）。均未标 `@Deprecated`。
6. **`DictProperties.getItemByType` 用 `.get()`**：type 未配置抛 `NoSuchElementException`，且 `item.getType()` 为 null 的 Item 会在 rebuild 时 NPE（`dict.items[n].type` 实际必填）。
7. **`setProperty` upsert 非事务**：UPDATE→INSERT 两步，`sys_property.name` 若无唯一约束，并发首写可能产生重复行。
8. **`getDictsByCodes` 空入参返回 null** 而非空 Map，与其余方法的「空集合降级」风格不一致。
9. **多实例部署**：字典 rebuild、参数 setProperty 均只作用于本实例内存，无跨实例广播。
