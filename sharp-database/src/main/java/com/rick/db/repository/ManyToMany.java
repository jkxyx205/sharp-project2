package com.rick.db.repository;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
@Transient
public @interface ManyToMany {

    boolean cascadeDelete() default true;

    boolean cascadeSelect() default true;

    boolean cascadeSave() default false;

    /**
     * cascadeSave = true,需要执行 mappedBy
     * @return
     */
    String mappedBy() default "";

    String tableName();

    String joinColumnId();

    String inverseJoinColumnId();

    /** TODO
     * 指定目标实体的哪一列作为外键所引用的列（默认引用对方表的主键）
     * @return
     */
    String joinReferencedColumnName() default "";

    /** TODO
     * 指定目标实体的哪一列作为外键所引用的列（默认引用对方表的主键）
     * @return
     */
    String inverseJoinReferencedColumnName() default "";
}