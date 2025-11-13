package com.rick.test.module.db.user.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.rick.common.http.json.deserializer.EntityWithLongIdPropertyDeserializer;
import com.rick.common.http.web.param.ParamName;
import com.rick.db.repository.Column;
import com.rick.db.repository.ManyToMany;
import com.rick.db.repository.OneToMany;
import com.rick.db.repository.Table;
import com.rick.db.repository.model.BaseEntity;
import jakarta.validation.constraints.NotBlank;
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
@Table(value = "t_user", comment = "用户")
public class User extends BaseEntity<Long> {

    @NotBlank
    String name;

    @Column(columnDefinition = "json") // postgres 需要显示指定 json
    List<String> nameList;

    /**
     * 一对一主键关联
     * 在 service 中处理
     */
    @OneToMany(oneToOne = true, joinColumnId = "id", mappedBy = "id")
    IdCard idCard;

    /**
     * 一对多外键关联
     */
    @OneToMany(mappedBy = "user", joinColumnId = "user_id")
    /**
     * /users?id=1&name=Rick&petIds=1&petIds=2
     * /users?id=1&name=Rick&pet_ids=3&pet_ids=4
     * /users?id=1&name=Rick&petList=5&petList=6
     */
    @ParamName({"petIds", "pet_ids", "petList"}) // 参数值
    /**
     * {
     *     "id": 1,
     *     "name": "Rick",
     *     // "petIds": [1, 2]
     *     // "pet_ids": [3, 4]
     *     // "petList": [5, 6]
     *     "petList": [
     *         {
     *             "id": 7,
     *             "name": "jerry"
     *         }
     *     ]
     * }
     */
    @JsonAlias({"petIds", "pet_ids", "petList"}) // 只读（反序列化）
    @JsonDeserialize(using = EntityWithLongIdPropertyDeserializer.class)
    List<Pet> petList;

    @ManyToMany(tableName = "t_user_role", joinColumnId = "user_id", inverseJoinColumnId = "role_id")
    List<Role> roleList;
}
