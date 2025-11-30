package com.rick.meta.config.validator;

import com.rick.meta.dict.model.DictType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * @author Rick.Xu
 * @date 2025/11/28 17:01
 */
public class DefaultDictValidator implements ConstraintValidator<DictType, Object> {
    @Override
    public boolean isValid(Object o, ConstraintValidatorContext constraintValidatorContext) {
        return true;
    }
}
