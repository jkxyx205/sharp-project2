package com.rick.db.repository;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
@Column
public @interface ManyToOne {

    @AliasFor(annotation = Column.class)
    String value() default "";

    @AliasFor(annotation = Column.class)
    boolean updatable() default true;

    @AliasFor(annotation = Column.class)
    String comment() default  "";

    boolean cascadeSelect() default true;

    boolean cascadeSave() default false;

    /**
     * TODO
     * 指定目标实体的哪一列作为外键所引用的列（默认引用对方表的主键）
     * @return
     */
    String referencedColumnName() default "";
}