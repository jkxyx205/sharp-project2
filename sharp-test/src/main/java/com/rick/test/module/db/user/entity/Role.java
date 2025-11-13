package com.rick.test.module.db.user.entity;

import com.rick.db.repository.ManyToMany;
import com.rick.db.repository.Table;
import com.rick.db.repository.model.BaseEntity;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * @author Rick.Xu
 * @date 2025/11/10 20:09
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Table(value = "t_role", comment = "角色")
public class Role extends BaseEntity<Long> {

    String name;

    @ManyToMany(tableName = "t_user_role", joinColumnId = "role_id", inverseJoinColumnId = "user_id")
    List<User> userList;
}