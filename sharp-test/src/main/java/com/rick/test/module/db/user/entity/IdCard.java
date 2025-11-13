package com.rick.test.module.db.user.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.rick.db.repository.Id;
import com.rick.db.repository.Table;
import com.rick.db.repository.model.BaseCodeEntity;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

/**
 * @author Rick.Xu
 * @date 2025/11/12 21:24
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Table(value = "t_id_card", comment = "身份证")
public class IdCard extends BaseCodeEntity<Long> {

    @Id(strategy = Id.GenerationType.ASSIGN)
    @JsonSerialize(
            using = ToStringSerializer.class
    )
    private Long id;

}
