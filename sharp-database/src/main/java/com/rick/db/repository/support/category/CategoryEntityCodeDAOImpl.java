package com.rick.db.repository.support.category;

import com.rick.common.http.exception.BizException;
import com.rick.db.repository.EntityCodeDAOImpl;
import com.rick.db.repository.model.EntityIdCode;
import com.rick.db.util.OperatorUtils;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
public class CategoryEntityCodeDAOImpl<T extends EntityIdCode<ID> & RowCategory<E>, ID, E> extends EntityCodeDAOImpl<T, ID> {

    private String categoryColumnName;

    public CategoryEntityCodeDAOImpl() {
        this("category");
    }

    public CategoryEntityCodeDAOImpl(String categoryColumnName) {
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

    @Override
    public T insert(T entity) {
        if (exists("code = ? AND "+ categoryColumnName +" = ?", new Object[]{entity.getCode(), getValue(entity.getCategory())})) {
            throw new BizException("编号已经存在");
        }
        return insertOrUpdate0(entity, true);
    }

    @Override
    public T update(T entity) {
        if (exists("id <> ? AND code = ? AND "+ categoryColumnName +" = ?", new Object[]{entity.getId(), entity.getCode(), getValue(entity.getCategory())})) {
            throw new BizException("编号已经存在");
        }
        return insertOrUpdate0(entity, false);
    }

    public Optional<T> selectByCategoryAndCode(@NotNull E category, @NotBlank String code) {
        return OperatorUtils.expectedAsOptional(select("code = ? AND "+categoryColumnName+" = ?", code, getValue(category)));
    }

    public List<T> selectAll(@NotNull E category) {
        return select(categoryColumnName + " = ?", getValue(category));
    }

    @Override
    protected void fillEntityIdByCode(T t) {
        if (Objects.isNull(t.getId()) && StringUtils.isNotBlank(t.getCode())) {
            Optional<T> option = selectByCategoryAndCode(t.getCategory(), t.getCode());
            if (option.isPresent()) {
                t.setId(option.get().getId());
            }
        }
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

//    private void fillEntityIdsByCodes(EntityCodeDAO<T, ID> entityCodeDAO, Collection<T> entities) {
//        if (CollectionUtils.isNotEmpty(entities)) {
//            Set<String> emptyIdCategorySet = entities.stream().filter(t -> Objects.isNull(t.getId())).map(e -> e.getCategory().toString()).collect(Collectors.toSet());
//            if (CollectionUtils.isNotEmpty(emptyIdCategorySet)) {
//
//                List<T> list = entityCodeDAO.selectWithoutCascadeSelect(entityCodeDAO.getTableMeta().getEntityClass(), "code, id, category", "category IN (:category)", Map.of("category", emptyIdCategorySet));
//                if (CollectionUtils.isNotEmpty(list)) {
//                    MultiKeyMap<Object, ID> multiKeyMap = new MultiKeyMap();
//
//                    for (T t : list) {
//                        multiKeyMap.put(t.getCategory(), t.getCode(), t.getId());
//                    }
//
//                    for (T t : entities) {
//                        // fillIds
//                        if (Objects.isNull(t.getId()) && multiKeyMap.containsKey(t.getCategory(), t.getCode())) {
//                            t.setId(multiKeyMap.get(t.getCategory(), t.getCode()));
//                        }
//                    }
//                }
//            }
//        }
//    }
}