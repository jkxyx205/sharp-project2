# 示例：分类（Category）字典表

> 蓝本：sharp-test `module/db/complex/**`（`CodeDescription`/`CodeDescriptionDAO`/`CodeDescriptionService`，真实代码）。
> 解决的问题：**同一张 code 表按分类维度隔离**——如 `sys_code_description` 中 `MATERIAL`、`SALES_ORG` 等每个分类各自拥有一套唯一 code；或按动态值（如工厂 `plant_code`）分组（`CategoryEntityDAOImpl` javadoc 原文场景）。

## 1. 实体：实现 RowCategory

```java
// sharp-test CodeDescription（节选，真实代码）
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Table(value = "sys_code_description", comment = "编号-描述")
public class CodeDescription extends BaseCodeDescriptionEntity<Long>   // id + code + description + 审计字段
        implements RowCategory<CodeDescription.CategoryEnum> {          // com.rick.db.repository.support.category.RowCategory

    @NotNull
    CategoryEnum category;      // 静态分类：实体自己的枚举字段 → 列 category

    Integer sort;

    @AllArgsConstructor
    @Getter
    public enum CategoryEnum {
        MATERIAL("物料组"),
        SALES_ORG("销售组织");
        // ...

        @JsonValue
        public String getCode() { return this.name(); }
        public String getLabel() { return label; }
        private final String label;
        public static CategoryEnum valueOfCode(String code) { return valueOf(code); }
    }
}
```

RowCategory 契约：

```java
public interface RowCategory<T> {
    T getCategory();
    void setCategory(T t);
}
```

- 静态分类：直接一个枚举字段（Lombok @Getter/@Setter 即满足接口）。存储值 = `枚举.toString()`（`CategoryEntityDAOImpl.getValue`），上例即 `MATERIAL`。
- 动态分类：把 get/setCategory 代理到业务字段（javadoc 原文示例）：

```java
@Override public String getCategory()        { return plantCode; }
@Override public void setCategory(String pc) { setPlantCode(pc); }
```

## 2. DAO：选对父类

| 实体基类 | DAO 父类 |
|---|---|
| `EntityIdCode`/`BaseCodeEntity`/`BaseCodeDescriptionEntity` + 枚举分类 | `CategoryEnumEntityCodeDAOImpl<T, ID, E extends Enum<E>>` |
| 同上 + 动态（非枚举）分类 | `CategoryEntityCodeDAOImpl<T, ID, E>` |
| `EntityId`/`BaseEntity`（**无 code**）+ 枚举分类 | `CategoryEnumEntityDAOImpl<T, ID, E extends Enum<E>>` |
| `EntityId`/`BaseEntity`（**无 code**）+ 动态（非枚举）分类 | `CategoryEntityDAOImpl<T, ID, E>` |

```java
// sharp-test CodeDescriptionDAO（真实代码）
@Repository
public class CodeDescriptionDAO extends CategoryEnumEntityCodeDAOImpl<CodeDescription, Long, CodeDescription.CategoryEnum> {
}
```

分类列名默认 `category`；不同时构造传入（javadoc 原文示例）：

```java
@Repository
public class StorageLocationDAO extends CategoryEntityCodeDAOImpl<StorageLocation, Long, String> {
    public StorageLocationDAO() {
        super("plant_code");
    }
}
```

**四个父类现在都支持自定义分类列名**（都有 `()` 与 `(String categoryColumnName)` 两个构造器）。两个 `*Enum*` 变体的 `(String)` 构造器是近期补上的——此前它们只有隐式无参构造器，枚举分类的表若列名不是 `category`（如叫 `type`），写 `super("type")` 会编译不过，只能改表列名或退回非 Enum 版。现在可以：

```java
@Repository
public class SomeEnumCategoryDAO extends CategoryEnumEntityCodeDAOImpl<SomeEntity, Long, SomeEntity.TypeEnum> {
    public SomeEnumCategoryDAO() {
        super("type");          // 分类列是 type 而非默认的 category
    }
}
```

> IDE 可能对 `builder()` 标红：Lombok `@SuperBuilder` + 多重边界泛型（`EntityIdCode<ID> & RowCategory<E>`）组合的已知噪音，**可以通过编译**（源码 javadoc 原文）。

## 3. 白得的能力

```java
// 分类内 code 唯一（insert/update 自动按 "code = ? AND category = ?" 查重，重复抛 BizException("编号已经存在")）
codeDescriptionDAO.insert(cd);

// 按分类 + code 查询
Optional<CodeDescription> one =
        codeDescriptionDAO.selectByCategoryAndCode(CategoryEnum.MATERIAL, "M01");
Optional<Long> id = codeDescriptionDAO.selectByCategoryAndCode(CategoryEnum.MATERIAL, "M01", CodeDescription::getId);
// （第三重载：selectByCategoryAndCode(category, code, columnName, clazz) 取任意单列）

// 按分类查全部
List<CodeDescription> list = codeDescriptionDAO.selectAll(CategoryEnum.MATERIAL);

// 分类范围内整表同步（保存页面提交的某分类全量列表）：
// 给每行 setCategory → 删除该分类下不在列表中的行 → 逐条 insertOrUpdate
codeDescriptionDAO.insertOrUpdate(CategoryEnum.MATERIAL, list);
// 需要在删除前拿到将被删除的 id：
codeDescriptionDAO.insertOrUpdate(CategoryEnum.MATERIAL, list, true,
        deletedIds -> log.info("将删除 {}", deletedIds));

// insertOrUpdate(entity)（EntityCodeDAO 语义增强版）：id 为 null 时按 category+code 回填 id 再决定 insert/update
codeDescriptionDAO.insertOrUpdate(cd);
```

## 4. Service 参考（sharp-test CodeDescriptionService 真实代码）

```java
@Service
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Validated
public class CodeDescriptionService {

    CodeDescriptionDAO codeDescriptionDAO;

    public List<CodeDescription> findAll(CodeDescription.CategoryEnum category) {
        return codeDescriptionDAO.select("category = :category", Map.of("category", category.getCode()));
    }

    public Optional<CodeDescription> findOne(CodeDescription.CategoryEnum category, String code) {
        return OperatorUtils.expectedAsOptional(   // 新代码建议用 sharp-common CollectionOps.expectedAsOptional（OperatorUtils 已 @Deprecated）
                codeDescriptionDAO.select("category = :category AND code = :code",
                        Map.of("category", category.getCode(), "code", code)));
    }
}
```

> ℹ️ sharp-test 该文件的 `saveAll` 调用了三参 `insertOrUpdateTable(list, "category", ...)` 并留有 TODO。该 TODO 是修复前的产物：三参版曾忽略范围参数按全表删除，**现已修复为正确的范围删除**（`EntityDAOImpl` 透传五参版）。新代码可继续用三参版，或用语义更清晰的 `insertOrUpdate(category, list)`（内部同样走五参版）。
> 仍需注意的陷阱是**单参版 `insertOrUpdateTable(list)` 是全表同步语义**（`refColumnName` 为 null 时走 `deleteAll()` / `delete("id NOT IN (:ids)")`），见 API.md Common Mistakes #4。

## 5. 建表要点

生成器会为 `category` 枚举列生成 MySQL `ENUM('MATERIAL','SALES_ORG',...)` / PostgreSQL `VARCHAR(32) + CHECK (category IN (...))`（存 `toString()` 值）。code 唯一约束是**应用层校验**（insert/update 查重），DDL 不建 (category, code) 唯一索引——高并发下需自行加库级唯一约束兜底。
