package com.rick.test.module.db.complex.service;

import com.rick.test.module.db.complex.dao.CodeDescriptionDAO;
import com.rick.test.module.db.complex.entity.CodeDescription;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Rick.Xu
 * @date 2024/2/20 15:51
 */
@Service
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Validated
public class CodeDescriptionService {

    CodeDescriptionDAO codeDescriptionDAO;

    public void saveAll(@NotNull CodeDescription.CategoryEnum category, Collection<CodeDescription> list) {
        if (CollectionUtils.isNotEmpty(list)) {
            for (CodeDescription codeDescription : list) {
                codeDescription.setCategory(category);
            }
        }
       // TODO
        codeDescriptionDAO.insertOrUpdate(list, "category", category.getCode());
    }

    public List<CodeDescription> findAll(CodeDescription.CategoryEnum category) {
        return codeDescriptionDAO.select("category = :category", Map.of("category", category.getCode()));
    }

    public List<CodeDescription> findAll() {
        return codeDescriptionDAO.selectAll();
    }
}