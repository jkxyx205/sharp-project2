package com.rick.test.module.db.fix.entity;

import com.rick.db.repository.Table;
import com.rick.db.repository.model.BaseEntity;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

/**
 * P0 修复回归测试专用实体（见 P0FixTest）。
 * 表 t_p0_fix_record 由测试 @BeforeEach 建表并物理清场，不承载业务数据。
 *
 * @author Rick.Xu
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Table(value = "t_p0_fix_record", comment = "P0 修复回归测试记录")
public class FixRecord extends BaseEntity<Long> {

    String name;
}
