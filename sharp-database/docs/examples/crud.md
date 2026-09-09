# 示例：从零新增一张表并做增删改查

> 蓝本：sharp-test `src/main/java/com/rick/test/module/db/user/**`（真实可运行代码，做了最小化裁剪）。
> 前提：应用已引入 `spring-boot-starter-jdbc` + 驱动，并配置 `spring.datasource.*` 与 `sharp.database.*`（见 `docs/api/configuration.md`）。

## 1. 实体（entity/User.java）

```java
package com.example.demo.entity;

import com.rick.db.repository.Column;
import com.rick.db.repository.Table;
import com.rick.db.repository.Transient;
import com.rick.db.repository.model.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder                       // 基类是 @SuperBuilder，子类必须一致
@Table(value = "t_user", comment = "用户")   // com.rick.db.repository.Table，不是 JPA！
public class User extends BaseEntity<Long> { // 自带：id(雪花)、create_by/create_time/update_by/update_time/is_deleted

    @NotBlank                        // insert/update(@Valid) 时校验
    String name;                     // 未标注解 → 列 name（camelCase 自动转 snake_case）

    @Column(comment = "年龄", nullable = false)
    Integer age;                     // 列 age，建表 DDL NOT NULL

    @Transient                       // 非列字段必须标，否则 SQL 报“列不存在”
    Integer score;
}
```

带外部编号的表改用 `BaseCodeEntity<Long>`（+`code` 列）或 `BaseCodeDescriptionEntity<Long>`（+`code`、`description`）。

## 2. DAO（dao/UserDAO.java）

```java
package com.example.demo.dao;

import com.rick.db.repository.EntityDAOImpl;
import com.example.demo.entity.User;
import org.springframework.stereotype.Repository;

@Repository
public class UserDAO extends EntityDAOImpl<User, Long> {
}
```

- code 实体：`extends EntityCodeDAOImpl<IdCard, Long>`（获得 selectByCode 系列 + code 唯一性检查）。
- 确保实体包被 `sharp.database.entity-base-package` 覆盖。

## 3. Service（service/UserService.java，事务加在这一层）

```java
package com.example.demo.service;

import com.rick.db.plugin.BaseServiceImpl;
import com.example.demo.dao.UserDAO;
import com.example.demo.entity.User;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;

@Service
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Validated
public class UserService extends BaseServiceImpl<UserDAO, User, Long> {

    public UserService(UserDAO baseDAO) {
        super(baseDAO);
    }

    @Transactional(rollbackFor = Exception.class)   // 框架无事务，必须自己加
    public User saveOrUpdate(User user) {
        return baseDAO.insertOrUpdate(user);        // id==null → insert（雪花 id 回填）；否则全列 update
    }

    public Optional<User> findById(Long id) {
        return baseDAO.selectById(id);              // empty = 不存在；不会返回 null
    }

    public List<User> findAll() {
        return baseDAO.selectAll();
    }

    public Integer removeById(Long id) {
        return baseDAO.deleteById(id);              // 影响行数
    }
}
```

> ⚠️ 继承 `BaseServiceImpl` 后，子类**不要声明与父类同签名但返回类型不兼容的方法**（如 `Integer deleteById(Long)` 对父类 `int deleteById(ID)` 会编译失败）。要么直接用继承来的方法，要么换名（sharp-test `UserService` 因此叫 `deleteBytId`）。

不想继承基类也可以直接注入 `UserDAO`（sharp-test `UserService2` 就是这种写法）。

## 4. Controller（controller/UserController.java）

```java
@RestController
@RequestMapping("users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public User save(@Valid @RequestBody User user) {
        return userService.saveOrUpdate(user);
    }

    @GetMapping("{id}")
    public User get(@PathVariable Long id) {
        return userService.selectById(id).orElse(null);
    }

    @DeleteMapping("{id}")
    public Integer delete(@PathVariable Long id) {
        return userService.removeById(id);
    }
}
```

## 5. 建表（开发期，二选一）

- **生成器**：测试里 `tableGenerator.createTable(User.class)`（见 `code-generator.md`），DDL 同时打印到 INFO 日志。
- **手写 DDL**：按注解规则建表（列名 = 属性 snake_case；`BaseEntity` 需含 `id BIGINT PK, create_by BIGINT NOT NULL?, create_time DATETIME NOT NULL, update_by BIGINT, update_time DATETIME NOT NULL, is_deleted BIT/BOOLEAN NOT NULL`——精确类型以生成器输出为准）。

> 审计字段（create_by 等）自动填充与逻辑删除需要注册 `ExtendTableDAOImpl`（见 `docs/api/configuration.md` 与 sharp-test `TestConfig`）；否则这些字段按实体上的值写入（通常为 null，`create_time/update_time/is_deleted` 为 NOT NULL 列时 insert 会失败）。

## 6. 常用查询写法速查

```java
// 条件 + 命名参数（直接绑定：condition 里出现的每个 :参数 都必须在 Map 中给值）
userDAO.select("name LIKE :name AND age > :age",
        Map.of("name", "%Rick%", "age", 18));        // 此路径不走 SQLParamCleaner：LIKE 需自带 %

// 样例查询（非 null 属性 = 等值条件，null 属性条件自动剔除 ← 走 cleaner）
userDAO.select(User.builder().name("Rick").build());

// 全动态（不写条件，按 Map 全列匹配，空值条件自动剔除 ← 走 cleaner）
userDAO.select(Map.of("name", "Rick"));

// 单列投影
Optional<String> name = userDAO.selectById(1L, User::getName);
Map<Long, String> names = userDAO.selectByIds(Set.of(1L, 2L), User::getName);

// 指定列更新（部分更新，不动其他列）
userDAO.updateById("name, age", 1L, Map.of("name", "Rick", "age", 20)); // key=属性名
userDAO.patch(user);                                                      // 或：仅更新 user 上非 null 属性

// 计数 / 存在
long total = userDAO.count("age > ?", 18);
boolean has = userDAO.exists("name = ?", "Rick");
```
