package com.rick.db.repository.model;

import com.rick.db.repository.Embedded;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * @author Rick
 * @createdAt 2021-09-23 23:10:00
 */
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
public class BaseEntity<ID> extends EntityId<ID> implements BaseEntityInfoGetter {

    @Valid
    @Embedded
    BaseEntityInfo baseEntityInfo;

}
