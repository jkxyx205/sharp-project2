package com.rick.db.repository.support;

import java.lang.annotation.*;

/**
 * @author Rick.Xu
 * @date 2025/11/26 12:11
 * fill code，如果不存在也不抛出异常
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface CodeFillUncheck {
}
