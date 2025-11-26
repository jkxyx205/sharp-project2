package com.rick.db.repository.support;

import com.rick.common.http.exception.BizException;
import com.rick.common.util.EnumUtils;
import com.rick.db.repository.EntityCodeDAO;
import com.rick.db.repository.EntityDAOManager;
import com.rick.db.repository.Table;
import com.rick.db.repository.model.EntityIdCode;
import com.rick.db.repository.support.category.CategoryEntityCodeDAOImpl;
import com.rick.db.repository.support.category.RowCategory;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Rick
 * @createdAt 2023-03-08 22:17:00
 */
public class EntityCodeIdFillService {

    public <T extends EntityIdCode> void fill(T t) {
        if (t == null || t.getId() != null || StringUtils.isBlank(t.getCode())) {
            return;
        }

        fill(t, t.getCode());
    }

    public <T extends EntityIdCode> void fill(T t, String code) {
        if (t == null || t.getId() != null || StringUtils.isBlank(code)) {
            return;
        }

        if (t instanceof RowCategory rowCategory) {
            t.setId(fill((Class)rowCategory.getClass(), null, rowCategory.getCategory(), code));
        } else {
            t.setId(fill(t.getClass(), null, code));
        }

    }

    public <T extends EntityIdCode, ID> ID fill(Class<T> clazz, ID id, String code) {
        if (id != null || StringUtils.isBlank(code)) {
            return id;
        }
        Optional<ID> optional = ((EntityCodeDAO) EntityDAOManager.getDAO(clazz)).selectIdByCode(code);

        if (Objects.nonNull(clazz.getAnnotation(CodeFillUncheck.class))) {
            return optional.orElse(null);
        }

        return optional.orElseThrow(() -> new BizException("%s code %s 不存在", new Object[]{clazz.getAnnotation(Table.class).comment(), code}));
    }

    public <T extends EntityIdCode & RowCategory<E>, E> void fill(T t, E category) {
        fill(t, category, t.getCode());
    }

    public <T extends EntityIdCode & RowCategory<E>, E> void fill(T t, E category, String code) {
        if (t == null || t.getId() != null || StringUtils.isBlank(code)) {
            return;
        }

        t.setId(fill((Class<T>) t.getClass(), t.getId(), category, code));
    }

    public <T extends EntityIdCode<ID> & RowCategory, ID, E> ID fill(Class<T> clazz, ID id, E category, String code) {
        if (id != null || StringUtils.isBlank(code)) {
            return id;
        }

        Optional<T> optional = ((CategoryEntityCodeDAOImpl) EntityDAOManager.getDAO(clazz)).selectByCategoryAndCode(category, code);
        if (Objects.nonNull(clazz.getAnnotation(CodeFillUncheck.class))) {
            return optional.map(T::getId).orElse(null);
        }

        return optional.orElseThrow(() -> new BizException("%s%s code %s 不存在", new Object[]{clazz.getAnnotation(Table.class).comment(), category.getClass().isEnum() ? EnumUtils.getLabel((Enum) category) : category, code})).getId();
    }

}
