package com.rick.test.module.db.user.select;

import com.rick.db.repository.ManyToMany;
import com.rick.db.repository.OneToMany;
import com.rick.db.repository.Table;
import com.rick.db.repository.model.EntityId;
import com.rick.test.module.db.user.entity.IdCard;
import com.rick.test.module.db.user.entity.Pet;
import com.rick.test.module.db.user.entity.Role;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * @author Rick.Xu
 * @date 2025/11/10 16:24
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Table
public class UserSelect extends EntityId<Long> {

    /**
     * 一对一主键关联
     * 在 service 中处理
     */
    @OneToMany(oneToOne = true, joinColumnId = "id", mappedBy = "id")
    @NotNull
    IdCard idCard;

    /**
     * 一对多外键关联
     */
    @OneToMany(mappedBy = "user", joinColumnId = "user_id")
    List<Pet> petList;

    @ManyToMany(tableName = "t_user_role", joinColumnId = "user_id", inverseJoinColumnId = "role_id"/*, cascadeSave = true, mappedBy = "userList"*/)
    List<Role> roleList;

}
