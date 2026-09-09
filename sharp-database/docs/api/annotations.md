# 实体注解完整参考（com.rick.db.repository / com.rick.db.repository.support）

> 全部信息摘自注解源码与 `TableMetaResolver`/`EntityDAOImpl` 的实际读取逻辑。
> **再次强调：这些注解不是 jakarta.persistence，不要按 JPA 语义理解。**
> 列名/属性名默认转换规则：属性名 camelCase → 列名 snake_case（`StringUtils.camelToSnake`）；类名 → 表名同理。

## @Table ⭐

```java
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE}) @Retention(RUNTIME)
public @interface Table {
    String value() default "";
    String comment() default "";
    String referenceColumnId() default "";
}
```

| 属性 | 类型 | 默认 | 语义 |
|---|---|---|---|
| `value` | String | `""` | 表名。空 → `camelToSnake(类简名)`（`User` → `user`）。也支持 `@Table` 不带 value（sharp-test `UserSelect`） |
| `comment` | String | `""` | 表注释（建表生成器使用；`EntityCodeIdFillService` 报错信息也用它） |
| `referenceColumnId` | String | `""` | 其他实体 `@OneToMany` 未指定 `joinColumnId` 时使用的**子表外键列名**，空 → `camelToSnake(类简名) + "_id"`（`User` → `user_id`） |

`@Table` 同时是 `EntityDAOSupport` 包扫描的识别标记（`AnnotationTypeFilter(Table.class)`）。

## @Id ⭐

```java
@Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE}) @Retention(RUNTIME)
public @interface Id {
    GenerationType strategy() default GenerationType.SEQUENCE;
    enum GenerationType { ASSIGN, SEQUENCE, IDENTITY }
}
```

- **只有第一个被解析到的 `@Id` 字段生效**（`TableMetaResolver` 取 `idMeta == null` 时的第一个）。
- 三种策略（`EntityDAOImpl.insertOrUpdate0` 实证）：

| 策略 | id 来源 | 建表 DDL | 说明 |
|---|---|---|---|
| `SEQUENCE`（默认） | 框架：`IdGenerator.getSequenceId()` **雪花算法 Long**（非数据库序列！） | 普通 BIGINT 主键 | insert 前生成并回填实体 |
| `IDENTITY` | 数据库自增，`insertAndReturnKey` 回填 | MySQL `AUTO_INCREMENT`；PG `GENERATED ALWAYS AS IDENTITY`；SQLite `AUTOINCREMENT` | 批量 `insertWithoutCascade(Collection)` 走 `batchInsert` 回填 |
| `ASSIGN` | 业务方 insert 前自行 `setId` | 普通主键（SQLite 生成 TEXT(32)） | sharp-test `IdCard` 用法（一对一主键共享） |

- 特例：**任何策略下，insert 时实体 id 已有值 → 按该值插入**（不再生成）。
- `EntityId<ID>` 基类已带 `@Id`（默认 SEQUENCE）；子类可重新声明字段覆盖策略（sharp-test `IdCard` 重声明 `@Id(strategy = ASSIGN)` 并覆写 `toString`）。
- 主键列名默认 `id`；⚠️ 多个 DAO 方法硬编码 `id` 列名（见 API.md Common Mistakes #10）。

## @Column ⭐

```java
@Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE}) @Retention(RUNTIME) @Inherited
public @interface Column {
    String value() default "";
    boolean updatable() default true;
    boolean nullable() default true;
    String comment() default "";
    String columnDefinition() default "";
}
```

| 属性 | 语义 |
|---|---|
| `value` | 列名，空 → 属性名 snake_case；`@ManyToOne` 字段空 → 属性名 snake_case + `_id` |
| `updatable` | `false` → 列不进 UPDATE 语句集（`updateColumn`）；insert 仍包含。`BaseEntityInfo.createBy/createTime` 即 `false` |
| `nullable` | 仅建表生成器使用（`NOT NULL`/`NULL`）；运行期不做非空校验（校验靠 Bean Validation） |
| `comment` | 建表生成器使用 |
| `columnDefinition` | 非空时**直接作为 DDL 类型**（此时 `nullable/comment` 失效——注解 javadoc 原文）。PostgreSQL 下 `"json"`/`"jsonb"` 还触发运行期特殊处理：写入包 `PGobject`、SQL 追加 `::json/::jsonb`、`TableMeta.appendColumnVar` 生成占位符带 cast |

`@Column` 是元注解：`@ManyToOne` 通过 `@AliasFor` 复用其 `value/updatable/comment`。查询/更新时通过 `AnnotatedElementUtils.getMergedAnnotation` 解析，故组合注解属性等效。

**未标注解的普通字段也会被映射为列**（默认列名 snake_case）；不想映射必须 `@Transient`。

## @Transient ⭐

```java
@Target({ElementType.ANNOTATION_TYPE, ElementType.FIELD}) @Retention(RUNTIME)
public @interface Transient {}
```

字段不进任何 SQL（select 列、insert、update 均排除）。注意它也是元注解：`@Select/@Embedded/@OneToMany/@ManyToMany` 都被 `@Transient` 标注 → 这些字段天然不映射列；`@ManyToOne` 例外（被 `@Column` 标注，是列）。

## @Version ⚠️

```java
@Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE}) @Retention(RUNTIME)
public @interface Version { String value() default ""; }
```

- 每实体取第一个标注字段为 `versionField`；`value` 属性当前实现**未读取**（列名仍按普通规则）。
- 行为（`EntityDAOImpl.insertOrUpdate0`）：insert → 写 `1`；update → 实体版本 null 抛 `IllegalArgumentException("version field cannot be null")`；库中版本 > 实体版本抛 `IllegalArgumentException("version field is old")`；否则写 `库中版本 + 1`。
- **非原子**（先 SELECT 比对再 UPDATE，WHERE 不含版本）；无专用异常类型。详见 API.md §11。
- 建表生成器：版本列强制 `NOT NULL`（MySQL 加 COMMENT '版本号'）。

## @Embedded ⭐

```java
@Target({ElementType.METHOD, ElementType.FIELD}) @Retention(RUNTIME) @Transient
public @interface Embedded {
    String columnPrefix() default "";
    String comment() default "";
}
```

- **把嵌套对象平铺为列**（非 JPA Embeddable）：`TableMetaResolver.loopAllFields` 递归展开字段类型的所有字段；属性路径变为 `外层字段.内层字段`（SELECT 别名 `"baseEntityInfo.createBy"` 形式，`NestedRowMapper` 自动增长嵌套路径回填）。
- `columnPrefix`：拼在内层列名前的前缀（`@Embedded(columnPrefix="material_type_")` + 内层 `code` → 列 `material_type_code`）。`BaseEntity/BaseCodeEntity` 的 `baseEntityInfo` 无前缀 → 列即 `create_by` 等。
- `comment` 当前实现未读取。
- 可与 `@Select` 组合（sharp-test `ComplexModel.materialType`）：既平铺为列存储，又用 @Select 查询回填 label 等 `@Transient` 内层字段。
- 内层类无需 `@Table`；内层字段照常支持 `@Column/@Transient`。

## @Select ⚠️

```java
@Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE}) @Retention(RUNTIME) @Transient
public @interface Select {
    String value();
    Class<?> entityClass() default Void.class;
    String params() default "";
    @Deprecated String[] nullWhenParamsIsNull() default {};
    boolean cascadeSelect() default true;
}
```

| 属性 | 语义 |
|---|---|
| `value` | SQL。`entityClass=Void.class`（默认）→ **完整 SELECT 语句**；指定 `entityClass` → 拼在该实体 DAO 的 `SELECT 列 FROM 表` 之后（即只写 WHERE/ORDER 尾巴） |
| `entityClass` | 目标实体类；其 DAO 必须已注册（`EntityDAOManager.getDAO(...)`，即在 `entity-base-package` 内或有 DAO Bean） |
| `params` | `"SQL参数名@实体属性路径"` 逗号分隔，如 `"name@materialType.code"`；属性路径安全导航（中间 null → 参数 null）。**任一参数为 null → 不查库，字段直接置 null**（无需配置，javadoc 原文） |
| `nullWhenParamsIsNull` | 🗑 `@Deprecated`，行为已默认化 |
| `cascadeSelect` | 默认 true：查询结果的元素若是 `EntityId` 实体，继续对结果级联加载 |

- 字段类型为 `Collection` → 取结果列表；否则取唯一行（`expectedAsOptional`：**0 行 → null，多行 → `IncorrectResultSizeDataAccessException`**）。
- **对列表逐行执行**（N+1）；结果类型可以是任意类（走 `NestedRowMapper`），常见如 sharp-meta `DictValue`、`IdCodeValue`。
- 真实示例（sharp-test `ComplexModel`）：

```java
@Embedded(columnPrefix="material_type_")
@Select(value = "select name code, label from sys_dict WHERE type = 'MATERIAL_TYPE' AND name = :name",
        params = "name@materialType.code")
DictValue materialType;
```

## @ManyToOne ⭐

```java
@Target({ElementType.FIELD}) @Retention(RUNTIME) @Column
public @interface ManyToOne {
    @AliasFor(annotation = Column.class) String value() default "";
    @AliasFor(annotation = Column.class) boolean updatable() default true;
    @AliasFor(annotation = Column.class) String comment() default "";
    boolean cascadeSelect() default true;
    boolean cascadeSave() default false;
    String referencedColumnName() default "";   // 源码标注 TODO，当前实现未读取
}
```

- 字段类型 = 引用实体类；**字段本身是列**（外键，默认列名 `属性名_id`，如 `user` → `user_id`）。
- 写入：`parsingColumnValue` 提取引用实体的 id 存列；引用实体为 null → 存 null。
- 读取：级联查询时批量 IN 加载完整对象；`selectWithoutCascade` 时经 `IdToEntityConverterFactory` 得到**只含 id 的实体桩**。
- `cascadeSelect=true`（默认）：主查询后自动补全对象。`cascadeSave=true`：保存本实体前先 `insertOrUpdate` 引用实体（sharp-test `Pet.user`）。
- 引用实体的 DAO 必须已注册（级联经 `EntityDAOManager`）。

## @OneToMany ⭐

```java
@Target({ElementType.FIELD}) @Retention(RUNTIME) @Transient
public @interface OneToMany {
    boolean cascadeDelete() default true;
    boolean cascadeSelect() default true;
    boolean cascadeSave() default true;
    boolean cascadeSaveItemDelete() default true;
    boolean cascadeSaveItemDeleteCheck() default false;
    String mappedBy() default "";
    String joinColumnId() default "";
    boolean oneToOne() default false;
}
```

| 属性 | 语义 |
|---|---|
| `mappedBy` | **子实体的属性名**（非列名）：保存时框架把父实体（子字段类型==父类型时）或父 id set 到子实体该属性。例：`mappedBy="user"`（Pet.user）；一对一主键共享用 `mappedBy="id"` |
| `joinColumnId` | 子表外键**列名**（查询/删除条件用）；空 → 父实体 `@Table.referenceColumnId`（父类名_id） |
| `oneToOne` | true → 字段是单个对象（取第一条，无匹配为 null）；`mappedBy` 指向子实体 id 时实现主键共享一对一（sharp-test `User.idCard`，insert 需 Service 手工处理，见 API.md §7） |
| `cascadeSelect` | 查询父实体后一次 `joinColumnId IN (父ids)` 批量加载子表并分组回填 |
| `cascadeSave` | 保存父实体时同步子表：list 为空且 `cascadeSaveItemDelete=true` → 删除该父的全部子行；否则先删除不在 list 中的子行（id NOT IN），再逐条给子实体 set mappedBy 并 insertOrUpdate |
| `cascadeSaveItemDelete` | 上述“删除不在列表中的子行”开关（只在 cascadeSave=true 或 cascadeDelete=true 时生效） |
| `cascadeSaveItemDeleteCheck` | true → 删除前回调 DAO 的 `itemDeletedCheckCallback(referenceDAO, deletedIds)`（业务校验/联动，sharp-test `UserDAO` 有覆写样例） |
| `cascadeDelete` | 删除父实体时删除子表行（`joinColumnId IN`），同样受 `cascadeSaveItemDelete(Check)` 影响 |

## @ManyToMany ⭐

```java
@Target({ElementType.FIELD}) @Retention(RUNTIME) @Transient
public @interface ManyToMany {
    boolean cascadeDelete() default true;
    boolean cascadeSelect() default true;
    boolean cascadeSave() default false;
    String mappedBy() default "";                    // 当前实现未读取
    String tableName();                              // 必填：中间表
    String joinColumnId();                           // 必填：指向本实体的列
    String inverseJoinColumnId();                    // 必填：指向对端实体的列
    String joinReferencedColumnName() default "";    // 源码 TODO，未读取
    String inverseJoinReferencedColumnName() default ""; // 源码 TODO，未读取
}
```

- 字段类型 `List<对端实体>`；查询：中间表 `(joinColumnId, inverseJoinColumnId) WHERE joinColumnId IN (父ids)` → 批量查对端 → 分组回填。
- 保存（任何 cascadeSave 值都会重建中间表行）：先 `DELETE 中间表 WHERE joinColumnId = 本实体id`，再为 list 中每个对端实体 INSERT 一行；`cascadeSave=true` 时先 `insertOrUpdate` 对端实体本身。
- `cascadeDelete=true`：删本实体时删中间表行；**不删对端实体**。
- 中间表建表由生成器自动完成（两列 BIGINT + `is_deleted` + UNIQUE 约束）。
- 真实示例（sharp-test `User.roleList` / `Role.userList` 双向，各自 joinColumnId 互换）。

## @ToStringValue ⚠️

```java
@Target({ElementType.FIELD}) @Retention(RUNTIME)
public @interface ToStringValue {}
```

写库前对该字段值调用 `toString()`（`parsingColumnValue` 第一优先级，先于枚举/json 处理）。适用：枚举按 `toString()` 而非 `getCode()` 存储、对象按字符串存储。当前工作区无使用实例。

## @CodeFillIgnore / @CodeFillUncheck ⚠️（com.rick.db.repository.support）

```java
@Target({ElementType.TYPE, ElementType.FIELD}) @Retention(RUNTIME)
public @interface CodeFillIgnore {}   // 注释：忽略 fill code —— 当前代码库无引用点，语义[需要确认]
public @interface CodeFillUncheck {}  // 注释：fill code 如果不存在也不抛出异常
```

`@CodeFillUncheck` 标在**实体类**上时，`EntityCodeIdFillService.fill(...)` 按 code 找不到记录返回 null 而非抛 `BizException`。

## 相关非注解标记

- `RowCategory<T>`（support.category，接口）：实体实现 `getCategory()/setCategory(T)` 后可用 Category DAO（见 API.md §13）。
- `InsertUpdateCallback`（support，函数式接口）：不是注解，是保存回调扩展点。
- sharp-meta 的 `@DictType/@DictValue` 不属于本模块（示例中出现，属字典模块）。
