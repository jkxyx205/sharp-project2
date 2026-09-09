# 示例：代码生成器（由实体生成建表 DDL）

> 蓝本：sharp-test `src/test/java/com/rick/test/TableGeneratorTest.java`（真实代码）。
> 注意：`TableGenerator` 生成的是**建表 SQL（CREATE TABLE）并直接执行**，不是生成 Java 代码；没有 ALTER/迁移能力。定位是开发/测试期工具。

## 运行方式（无 main 方法、无 CLI，通过 Spring 测试跑）

```java
// sharp-test TableGeneratorTest（真实代码）
@SpringBootTest
public class TableGeneratorTest {

    @Autowired
    private TableGenerator tableGenerator;   // 自动配置按 sharp.database.type 选型注入

    @Test
    public void testGeneratorUserTable() {
        tableGenerator.createTable(User.class);
        tableGenerator.createTable(IdCard.class);
        tableGenerator.createTable(Pet.class);
        tableGenerator.createTable(Role.class);   // ManyToMany 的中间表 t_user_role 随之自动创建
    }
}
```

- 需要可用的 DataSource（DDL 会被 `jdbcTemplate.execute` 真实执行）。
- DDL 同时以 `log.info` 打印（`TableGenerator` 源码），可只取打印结果人工执行。
- 同一线程内对同一实体重复调用会被跳过（ThreadLocal 去重）；表已存在时数据库报“table already exists”（`BadSqlGrammarException`/`SQLException` 透传），生成器**不做存在性检查**。
- `sharp-database/src/test/generated_tests` 目录当前为空（历史生成产物目录）。

## 方言选型（自动配置 `tableGenerator` Bean）

| `sharp.database.type` | 生成器 |
|---|---|
| MySQL5 | `MySQL5TableGenerator`（ENUM 列、COMMENT、ENGINE=InnoDB CHARSET=utf8mb4） |
| MySQL8 | `MySQL8TableGenerator`（Map/List/json 值 → JSON 而非 TEXT） |
| PostgreSQL | `PostgresSQLTableGenerator`（IDENTITY 用 `GENERATED ALWAYS AS IDENTITY`，枚举用 `CHECK (col IN (...))`，注释用 `comment on` 语句） |
| SQLite | `SQLiteTableGenerator`（TEXT 系类型、AUTOINCREMENT） |
| Oracle10g/Oracle11c/SQLServer2012 | 源码 TODO → **回退 MySQL5TableGenerator**，不要在 Oracle/SQLServer 上执行其产物 |

## 生成规则速查（MySQL5 版 `determineSqlType`）

| Java 类型 | DDL 类型 |
|---|---|
| `Long` | BIGINT |
| `Integer` | INT |
| `Short` | SMALLINT |
| `String`/CharSequence | **VARCHAR(32)**（长度不够必须 `@Column(columnDefinition="VARCHAR(255)")`） |
| `Character` | CHAR(1) |
| 枚举 | `ENUM('code1','code2',...)`；枚举 `getCode()` 返回数值 → INT |
| `Boolean` | BIT DEFAULT b'0' |
| `BigDecimal` | DECIMAL(10,2) |
| `LocalDateTime` | DATETIME |
| `Instant` | TIMESTAMP |
| `LocalDate` / `LocalTime` | DATE / TIME |
| `Map`/`List`/`JsonValue` | TEXT（MySQL8/PG: JSON） |
| 实体类型（`EntityId` 子类，如 `@ManyToOne`） | BIGINT |
| 其他纯对象 | JSON |
| 兜底 | VARCHAR(32) |

其他规则：

- **主键**：`@Id` SEQUENCE/ASSIGN → `BIGINT NOT NULL COMMENT '主键' PRIMARY KEY`；IDENTITY → `AUTO_INCREMENT ... PRIMARY KEY`（PG：`GENERATED ALWAYS AS IDENTITY`）。找不到 id 字段抛 `IllegalArgumentException("cannot find id field, forgot to extends BaseEntity??")`。
- **`@Version` 列**：类型按字段 + `NOT NULL COMMENT '版本号'`。
- **`@Column(columnDefinition=...)`**：原样作为类型（此时忽略 nullable/comment）；否则 `nullable=false → NOT NULL`，`true → NULL`；`comment` 生成列注释。
- **列顺序**：`id/code/description` 排前（`TableMeta.getSortedColumns`），其余按字段声明顺序。
- **`@ManyToMany` 中间表**：`(joinColumnId BIGINT NOT NULL, inverseJoinColumnId BIGINT NOT NULL, is_deleted BIT DEFAULT b'0' NOT NULL, UNIQUE(join, inverse))`。
- `@Transient`/`@OneToMany`/`@ManyToMany`/`@Select` 字段不生成列；`@Embedded` 平铺生成（含 `columnPrefix`）。

## 示例产物（BaseEntity 子类，MySQL5 方言，示意）

对 `docs/examples/crud.md` 的 `User`（继承 BaseEntity）大致生成：

```sql
CREATE TABLE t_user(
  id BIGINT NOT NULL COMMENT '主键' PRIMARY KEY,
  name VARCHAR(32) NULL,
  age INT NOT NULL COMMENT '年龄',
  create_by BIGINT NULL COMMENT '创建人',
  create_time DATETIME NOT NULL COMMENT '创建时间',
  update_by BIGINT NULL COMMENT '更新人',
  update_time DATETIME NOT NULL COMMENT '更新时间',
  is_deleted BIT DEFAULT b'0' NOT NULL COMMENT '是否逻辑删除'
) COMMENT '用户' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
```

（精确产物以运行时 `log.info` 输出为准；`name` 的 `@NotBlank` 是运行期校验，不影响 DDL 的 NULL 性——需要 NOT NULL 请写 `@Column(nullable=false)`。）

## 配套：执行自备 SQL 脚本

`DbScriptUtils.importSQL(connection, sqlFile|sqlContent|reader)`（⚠️ 工具类）可按 `;` 切分执行初始化脚本（跳过 `--` 注释行），配合 `TableDAO.execute(ConnectionCallback)` 或 `DbUtils` 使用；模块本身**没有**启动时自动跑脚本的机制（无 schema 自动初始化）。
