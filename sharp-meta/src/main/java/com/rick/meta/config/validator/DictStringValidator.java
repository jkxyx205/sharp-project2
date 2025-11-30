package com.rick.meta.config.validator;

import com.rick.db.repository.TableDAO;
import com.rick.meta.dict.model.DictType;
import com.rick.meta.dict.service.DictService;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.apache.commons.lang3.StringUtils;

/**
 * @author Rick.Xu
 * @date 2024/8/19 01:56
 */
public class DictStringValidator extends AbstractDictValidator implements ConstraintValidator<DictType, String> {

    public DictStringValidator(DictService dictService, TableDAO tableDAO) {
        super(dictService, tableDAO);
    }

    @Override
    public boolean isValid(String dictCode, ConstraintValidatorContext constraintValidatorContext) {
        if (StringUtils.isNotBlank(dictCode)) {
            return isValid(constraintValidatorContext, dictCode, null);
        }

        return true;
    }
}
