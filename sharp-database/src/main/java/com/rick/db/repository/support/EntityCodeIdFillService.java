package com.rick.db.repository.support;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.rick.common.http.exception.BizException;
import com.rick.common.util.EnumUtils;
import com.rick.db.repository.EntityCodeDAO;
import com.rick.db.repository.EntityCodeDAOImpl;
import com.rick.db.repository.EntityDAOManager;
import com.rick.db.repository.Table;
import com.rick.db.repository.model.EntityIdCode;
import com.rick.db.repository.support.category.CategoryEntityCodeDAOImpl;
import com.rick.db.repository.support.category.RowCategory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Rick
 * @createdAt 2023-03-08 22:17:00
 */
public class EntityCodeIdFillService {

    public <T extends EntityIdCode> void fill(T t) {
        if (t == null || t.getId() != null || StringUtils.isBlank(t.getCode())) {
            return;
        }

        t.setId(fill(t.getClass(), t.getId(), t.getCode()));
    }

    public <T extends EntityIdCode<ID>, ID> void fill(Collection<T> list) {
        if (CollectionUtils.isEmpty(list)) {
            return;
        }

        Class<T> clazz = (Class<T>) list.iterator().next().getClass();
        Map<String, ID> codeIdMap = getCodeIdMap(clazz, list.stream().map(EntityIdCode::getCode).collect(Collectors.toSet()));
        list.forEach(t -> t.setId(codeIdMap.get(t.getCode())));
    }

    public <T extends EntityIdCode<ID>, ID> List<T> fill(Class<T> clazz, Collection<String> codes) {
        Map<String, ID> codeIdMap = getCodeIdMap(clazz, codes);
        List<T> list = Lists.newArrayListWithExpectedSize(codes.size());

        for (String code : codes) {
            try {
                T t = clazz.newInstance();
                t.setId(codeIdMap.get(code));
                list.add(t);
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        return list;
    }

    public <T extends EntityIdCode> T fill(Class<T> clazz, String code) {
        try {
            T t = clazz.newInstance();
            return fill(clazz, t , code);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public <T extends EntityIdCode> void fill(T t, String code) {
        if (t == null || StringUtils.isBlank(code)) {
            return;
        }

        fill((Class<T>) t.getClass(), t, code);
    }

    public <T extends EntityIdCode> T fill(Class<T> clazz, T t, String code) {
        if (StringUtils.isBlank(code)) {
            return t;
        }

        if (t == null) {
            try {
                t = clazz.newInstance();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        if (t.getId() != null) {
            return t;
        }

        t.setCode(code);
        t.setId(fill(clazz, t.getId(), t.getCode()));
        return t;
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

    public <T extends EntityIdCode, ID> Map<String, ID> getCodeIdMap(Class<T> clazz, Collection<String> codes) {
        if (CollectionUtils.isEmpty(codes)) {
            return Collections.emptyMap();
        }

        Map<String, ID> codeIdMap = ((EntityCodeDAOImpl) EntityDAOManager.getDAO(clazz)).selectForKeyValue("code, id", "code IN (:codes)", Map.of("codes", codes));

        SetUtils.SetView<String> difference = SetUtils.difference(Sets.newHashSet(codes), codeIdMap.keySet());
        if (CollectionUtils.isNotEmpty(difference)) {
            throw new BizException("%s codes %s 不存在", new Object[]{clazz.getAnnotation(Table.class).comment(), difference});
        }

        return codeIdMap;
    }

    public <T extends EntityIdCode & RowCategory<E>, E> void fill(T t, E category) {
        if (t == null || t.getId() != null || StringUtils.isBlank(t.getCode())) {
            return;
        }

        t.setId(fill((Class<T>) t.getClass(), t.getId(), category, t.getCode()));
    }

    public <T extends EntityIdCode<ID> & RowCategory, ID, E> ID fill(Class<T> clazz, ID id, E category, String code) {
        if (id != null || StringUtils.isBlank(code)) {
            return id;
        }

        Optional<T> optional = ((CategoryEntityCodeDAOImpl) EntityDAOManager.getDAO(clazz)).selectByCategoryAndCode(category, code);

        return optional.orElseThrow(() -> new BizException("%s%s code %s 不存在", new Object[]{clazz.getAnnotation(Table.class).comment(), category.getClass().isEnum() ? EnumUtils.getLabel((Enum) category) : category, code})).getId();
    }

}
