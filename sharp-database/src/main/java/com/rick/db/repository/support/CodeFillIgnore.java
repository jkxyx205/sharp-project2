package com.rick.db.repository.support;

import java.lang.annotation.*;

/**
 * @author Rick.Xu
 * @date 2025/11/26 12:11
 * 忽略 fill code
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface CodeFillIgnore {
}
