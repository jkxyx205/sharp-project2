package com.rick.test.module.db.user.entity;

/**
 * @author Rick.Xu
 * @date 2026/3/15 15:48
 * PostgreSQL 定义自增 id 有两种方式：
 *
 * 方式一：SERIAL（传统方式）
 * sqlCREATE TABLE t_user (
 *     id SERIAL PRIMARY KEY,
 *     name VARCHAR(100)
 * );
 * 方式二：GENERATED ALWAYS AS IDENTITY（推荐，PostgreSQL 10+）
 * sqlCREATE TABLE t_user (
 *     id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 *     name VARCHAR(100)
 * );
 */
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.rick.db.repository.Id;
import com.rick.db.repository.Table;
import com.rick.db.repository.model.EntityId;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Table(value = "t_user_generated", comment = "id 自动增长")
public class UserGenerated extends EntityId<Long> {

    @Id(strategy = Id.GenerationType.IDENTITY)
    @JsonSerialize(using = ToStringSerializer.class)
    Long id;

    String name;
}
