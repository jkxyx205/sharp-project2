# 示例：关联（一对多 / 多对多 / 一对一 / @ManyToOne / @Select / @Embedded）

> 蓝本：sharp-test `module/db/user/entity/{User,Pet,Role,IdCard}.java`、`module/db/complex/entity/ComplexModel.java`（真实代码，注释为文档补充）。
> **核心语义：没有懒加载、没有 JOIN。**主查询完成后按引用字段批量 IN 补查（`@Select` 为逐行查询）。

## 一对多（外键在子表）

```java
// 父：User（表 t_user）
@OneToMany(mappedBy = "user",           // 子实体 Pet 的属性名（不是列名！）
           joinColumnId = "user_id",    // 子表外键列；省略时默认 = 父类名snake + "_id" = user_id
           cascadeSaveItemDeleteCheck = true)  // 级联保存删除子行前回调 DAO 钩子
List<Pet> petList;

// 子：Pet（表 t_pet）
@ManyToOne(cascadeSave = true)          // 列 user_id（属性名snake + _id）；保存 Pet 时先保存 user
User user;
```

- **查询**：`userDAO.selectById(id)` → 查 t_user 后自动执行 `SELECT ... FROM t_pet WHERE user_id IN (:ids)` 回填 `petList`；Pet 的 `user` 字段也会被级联回填（注意在 Controller 序列化前断开回环，sharp-test `UserController` 手动 `pet.setUser(null)`）。
- **保存**：`userDAO.insertOrUpdate(user)`（cascadeSave 默认 true）→
  1. `cascadeSaveItemDelete=true`（默认）：删除 `user_id = 当前id` 且 id 不在 petList 中的子行（删除前因 `cascadeSaveItemDeleteCheck=true` 触发 `UserDAO.itemDeletedCheckCallback`）；
  2. 对每个 pet：`pet.setUser(user)`（mappedBy 属性类型=父类型时 set 实体，否则 set 父 id）后 `insertOrUpdate`。
- **删除**：`userDAO.deleteById(id)` → cascadeDelete 默认 true → 先删子行再删父行。

对应的 DAO 钩子（sharp-test `UserDAO`）：

```java
@Repository
public class UserDAO extends EntityDAOImpl<User, Long> {
    @Override
    protected void itemDeletedCheckCallback(EntityDAO referenceDAO, Collection<Long> deletedIds) {
        // 子行即将被删除时的业务校验/联动（deletedIds 保证非空）
    }
}
```

## 多对多（中间表）

```java
// User 侧
@ManyToMany(tableName = "t_user_role", joinColumnId = "user_id", inverseJoinColumnId = "role_id")
List<Role> roleList;

// Role 侧（双向：join/inverse 互换）
@ManyToMany(tableName = "t_user_role", joinColumnId = "role_id", inverseJoinColumnId = "user_id")
List<User> userList;
```

- **查询**：查中间表 `(user_id, role_id) WHERE user_id IN (:ids)` → 批量查 t_role → 分组回填。
- **保存 User**：`DELETE FROM t_user_role WHERE user_id = ?` → （`cascadeSave=true` 时先保存 Role 实体本身，默认 false 只连关系）→ 逐条 `INSERT INTO t_user_role(user_id, role_id)`。Role 需已存在（cascadeSave=false 时只取其 id）。
- **删除 User**：cascadeDelete 默认 true → 删除中间表相关行（**不删 Role**）。
- 中间表可由 `TableGenerator` 自动创建（两列 BIGINT + is_deleted + UNIQUE(user_id, role_id)）。
- 前端只传 id 列表时，配合 sharp-common 的 `EntityWithLongIdPropertyDeserializer`（sharp-test 用法）把 `["1","2"]` 反序列化为只含 id 的实体列表。

## 一对一（主键共享，特殊）

```java
// User
@OneToMany(oneToOne = true, joinColumnId = "id", mappedBy = "id")
@NotNull
IdCard idCard;

// IdCard：主键即 User 的主键（业务赋值）
@Table(value = "t_id_card", comment = "身份证")
public class IdCard extends BaseCodeEntity<Long> {
    @Id(strategy = Id.GenerationType.ASSIGN)
    private Long id;
}
```

**必须在 Service 手工处理子实体的 insert**（sharp-test `UserService` 真实代码与原文注释：“一对一主键映射，需要在 service 中处理 insert 方法，sharp-database 会处理为更新”）。机制：级联保存按 `mappedBy="id"` 自动把 `user.id` 写进 `idCard.id`，随后对子实体执行的是 `insertOrUpdate`——此时 id 已非 null 被当成 **update**（新行更新不到任何记录），所以新增场景要由 Service 补一次 `insert`：

```java
// sharp-test UserService（真实代码）
@Transactional(rollbackFor = Exception.class)
public User insertOrUpdate(User user) {
    boolean insert = Objects.isNull(user.getId());
    baseDAO.insertOrUpdate(user);        // 级联把 user.id 回填到 idCard.id，但对 idCard 执行的是 update
    if (insert) {
        idCardDAO.insert(user.getIdCard());   // 手工补 insert（id 已与 user 相同）
    }
    return user;
}
```

查询方向正常：`selectById(userId)` 会以 `id IN (父ids)` 查 t_id_card 回填单个 `idCard`（无匹配为 null）。

## @Select：字段值来自自定义 SQL

```java
// sharp-test ComplexModel（真实代码）
@Embedded(columnPrefix = "material_type_")   // code 平铺存列 material_type_code
@Select(value = "select name code, label from sys_dict WHERE type = 'MATERIAL_TYPE' AND name = :name",
        params = "name@materialType.code")   // SQL 参数 name ← 实体属性路径 materialType.code
DictValue materialType;
```

- `params` 任一取值为 null → **不查库，字段置 null**。
- 字段是 Collection → 取结果列表；否则取唯一行（多行抛 `IncorrectResultSizeDataAccessException`）。
- `entityClass = Xxx.class` 时 `value` 只写尾巴：`@Select(value = "WHERE status = 1", entityClass = Role.class)` → 实际执行 `SELECT <Role列> FROM t_role WHERE status = 1`。
- ⚠️ 对 N 行结果执行 N 次（N+1），大列表慎用。

## @Embedded：值对象平铺为列 / json 列

```java
// 平铺（BaseEntity 内部即此用法）：
@Embedded(columnPrefix = "unit_")
DictValue unit;                 // 列 unit_code（DictValue.label/type 是 @Transient 不建列）

// json 列（复杂对象整体存储）：
@Column(columnDefinition = "json")   // PostgreSQL 必须显式声明 json/jsonb；MySQL8 生成 JSON，MySQL5 生成 TEXT
EmbeddedValue embeddedValue;

@Column(value = "category_list", columnDefinition = "json", nullable = false)
List<CodeDescription.CategoryEnum> categoryList;   // 集合/枚举集合 → json，写入自动序列化

@Column(columnDefinition = "text", value = "map", nullable = false)
Map<String, Object> map;
```

- 纯对象（非数字/字符串/枚举/时间）默认按 json 存取（`ObjectUtils.mayPureObject`），自有类建议实现 `JsonStringToObjectConverterFactory.JsonValue`（sharp-test `EmbeddedValue` 注释原文）。
- 枚举字段默认存 `getCode()` 值（`EnumUtils.getCode`）；要按 `toString()` 存加 `@ToStringValue`。

## 性能：跳过级联

```java
List<User> users = userDAO.selectWithoutCascade("age > ?", 18);  // 不加载 petList/roleList/idCard
Optional<User> u = userDAO.selectByIdWithoutCascade(id);
// ManyToOne 字段此时是“只含 id 的实体桩”（IdToEntityConverterFactory 转换）
```

级联触发点只有 `select*`（不带 WithoutCascade）与 `cascadeSelect(list)`；`selectById(id, column, clazz)` 等单列投影也不级联。
