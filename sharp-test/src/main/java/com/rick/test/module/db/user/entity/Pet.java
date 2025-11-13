package com.rick.test.module.db.user.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.rick.common.http.json.deserializer.EntityWithLongIdPropertyDeserializer;
import com.rick.common.http.web.param.ParamName;
import com.rick.db.repository.ManyToOne;
import com.rick.db.repository.Table;
import com.rick.db.repository.model.BaseEntity;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.util.Objects;

/**
 * @author Rick.Xu
 * @date 2025/11/13 10:24
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Table(value = "t_pet", comment = "宠物")
public class Pet extends BaseEntity<Long> {

    String name;

   /**
    id=1&name=Tom&userId=4
    id=1&name=Tom&use_id=4
    id=1&name=Tom&user=4
    */
    @ParamName({"userId", "user_id", "user"}) // 参数值
    /**
    {
        "id": 1,
            "name": "Tom",
            // "user": {
            //     "id": 2
            // }
            // "userId": "3"
//            "user_id": 4
            "user": 5
    }
    **/
    @JsonAlias({"userId", "user_id", "user"}) // 只读（反序列化）
    @JsonDeserialize(using = EntityWithLongIdPropertyDeserializer.class)
//    @JsonProperty(value = "userId") //    读 + 写（序列化 + 反序列化）, 通过 getUserId() 来完成
//    @JsonSerialize(using = EntityWithLongIdPropertySerializer.class)
    @ManyToOne
    User user;

    @JsonProperty("userId")
    public Long getUserId() {
        return Objects.nonNull(user) ? user.getId() : null;
    }
}