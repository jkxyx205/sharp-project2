package com.rick.db.repository.support.category;

import com.rick.db.repository.model.EntityIdCode;

/**
 * 如果有通过枚举值静态category 会区分不同的分组，比如 CodeDescription
 * 编辑器报错，但是可以通过编译。本质是 Lombok 的 @SuperBuilder + 泛型继承 + 多重边界接口(RowCategory) 组合使用导致的 builder() 冲突
 * @author Rick.Xu
 * @date 2025/11/14 11:59
 */
public class CategoryEnumEntityDAOImpl<T extends EntityIdCode<ID> & RowCategory<E>, ID, E extends Enum<E>> extends CategoryEntityDAOImpl<T, ID, E> {
}