package com.rick.db.repository.support.category;

import com.rick.db.repository.EntityDAOImpl;
import com.rick.db.repository.model.EntityId;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 如果有通过 category 会区分不同的分组(可以是枚举值静态category，比如 CodeDescription；也可以是动态值category，比如库位StorageLocation的plant_code)
 * 枚举值静态category:
 *      实体添加属性 Enum category，categoryColumnName 默认是 "category"
 *
 * 动态值category
 *     @Override
 *     public String getCategory() {
 *         return plantCode;
 *     }
 *
 *     @Override
 *     public void setCategory(String plantCode) {
 *         setPlantCode(plantCode);
 *     }
 *
 * 如果 分组不是使用的默认 categoryColumnName "category"
 * DAO需要在构造中给出
 *     @Repository
 *      public class StorageLocationDAO extends CategoryEntityCodeDAOImpl<StorageLocation, Long, String> {
 *          public StorageLocationDAO() {
 *              super("plant_code");
 *          }
 *      }
 * 编辑器报错，但是可以通过编译。本质是 Lombok 的 @SuperBuilder + 泛型继承 + 多重边界接口(RowCategory) 组合使用导致的 builder() 冲突
 * @author Rick.Xu
 * @date 2025/11/14 11:59
 */
public class CategoryEntityDAOImpl<T extends EntityId<ID> & RowCategory<E>, ID, E> extends EntityDAOImpl<T, ID> {

    private String categoryColumnName;

    public CategoryEntityDAOImpl() {
        this("category");
    }

    public CategoryEntityDAOImpl(String categoryColumnName) {
        this.categoryColumnName = categoryColumnName;
    }

    public void insertOrUpdate(@NotNull E category, Collection<T> list) {
        insertOrUpdate(category, list, true, null);
    }

    public void insertOrUpdate(@NotNull E category, Collection<T> list, boolean deleteItem, Consumer<Collection<ID>> deletedIdsConsumer) {
        if (CollectionUtils.isNotEmpty(list)) {
            for (T t : list) {
                t.setCategory(category);
            }
        }

        insertOrUpdate(list, categoryColumnName, getValue(category), deleteItem, deletedIdsConsumer);
    }

    public List<T> selectAll(@NotNull E category) {
        return select(categoryColumnName + " = ?", getValue(category));
    }

    private Object getValue(E category) {
        if (Objects.isNull(category)) {
            return "";
        }

        if (Enum.class.isAssignableFrom(category.getClass())) {
            return category.toString();
        }

        return category;
    }

}