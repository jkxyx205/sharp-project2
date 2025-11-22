package com.rick.db.repository.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.validator.constraints.Length;

/**
 * 外部可见的唯一编号, 同时带有 description 属性
 * @author Rick
 * @createdAt 2023-03-08 22:01:00
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class BaseCodeDescriptionEntity<ID> extends BaseCodeEntity<ID> {

    @NotBlank(message = "描述不能为空")
    @Length(max = 512, message = "描述不能超过512个字符")
    private String description;

}
