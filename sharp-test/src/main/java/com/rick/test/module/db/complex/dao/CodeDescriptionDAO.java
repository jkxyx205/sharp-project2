package com.rick.test.module.db.complex.dao;

import com.rick.common.http.exception.BizException;
import com.rick.db.repository.EntityCodeDAO;
import com.rick.db.repository.EntityCodeDAOImpl;
import com.rick.db.util.OperatorUtils;
import com.rick.test.module.db.complex.entity.CodeDescription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.NonNull;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Rick.Xu
 * @date 2025/11/14 11:59
 */
@Repository
public class CodeDescriptionDAO extends EntityCodeDAOImpl<CodeDescription, Long> {

    @Override
    public CodeDescription insert(CodeDescription entity) {
        if (exists("code = ? AND category = ?", new Object[]{entity.getCode(), entity.getCategory().getCode()})) {
            throw new BizException("编号已经存在");
        }
        return insertOrUpdate0(entity, true);
    }

    @Override
    public CodeDescription update(CodeDescription entity) {
        fillEntityIdByCode(entity);
        if (exists("id <> ? AND code = ? AND category = ?", new Object[]{entity.getId(), entity.getCode(), entity.getCategory().getCode()})) {
            throw new BizException("编号已经存在");
        }
        return insertOrUpdate0(entity, false);
    }

    @Override
    public Collection<CodeDescription> insertOrUpdate(Collection<CodeDescription> entityList, @NonNull String refColumnName, @NonNull Object refValue) {
        fillEntityIdsByCodes(this, entityList);
        return super.insertOrUpdate(entityList, refColumnName, refValue);
    }

    public Optional<CodeDescription> selectByCategoryAndCode(@NotNull CodeDescription.CategoryEnum category, @NotBlank String code) {
        return OperatorUtils.expectedAsOptional(select("code = ? AND category = ?", code, category.getCode()));
    }

    private void fillEntityIdByCode(CodeDescription t) {
        if (Objects.isNull(t.getId()) && StringUtils.isNotBlank(t.getCode())) {
            Optional<CodeDescription> option = selectByCategoryAndCode(t.getCategory(), t.getCode());
            if (option.isPresent()) {
                t.setId(option.get().getId());
            }
        }
    }

    private void fillEntityIdsByCodes(EntityCodeDAO<CodeDescription, Long> entityCodeDAO, Collection<CodeDescription> entities) {
        if (CollectionUtils.isNotEmpty(entities)) {
            Set<String> emptyIdCategorySet = entities.stream().filter(t -> Objects.isNull(t.getId())).map(e -> e.getCategory().getCode()).collect(Collectors.toSet());
            if (CollectionUtils.isNotEmpty(emptyIdCategorySet)) {

                List<CodeDescription> codeDescriptionList = entityCodeDAO.selectWithoutCascadeSelect(CodeDescription.class, "code, id, category", "category IN (:category)", Map.of("category", emptyIdCategorySet));
                if (CollectionUtils.isNotEmpty(codeDescriptionList)) {
                    MultiKeyMap<Object, Long> multiKeyMap = new MultiKeyMap();

                    for (CodeDescription codeDescription : codeDescriptionList) {
                        multiKeyMap.put(codeDescription.getCategory(), codeDescription.getCode(), codeDescription.getId());
                    }

                    for (CodeDescription t : entities) {
                        // fillIds
                        if (Objects.isNull(t.getId()) && multiKeyMap.containsKey(t.getCategory(), t.getCode())) {
                            t.setId(multiKeyMap.get(t.getCategory(), t.getCode()));
                        }
                    }
                }
            }
        }
    }
}