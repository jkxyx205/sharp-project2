package com.rick.test.module.db.user.controller.schema

import io.swagger.v3.oas.annotations.media.Schema

/**
 * @author Rick.Xu
 * @date 2025/11/13 22:12
 */
@Schema(name = "IdCard", description = "身份证")
data class IdCardSchema2(

    /**
     * 在 Kotlin 中：
     * val id: String 和 val code: String 是构造函数参数；
     * Kotlin 编译器默认不会把构造参数上的注解转移到「字段（field）」层面；
     * 但 Springdoc/Swagger 是通过 Java反射读取字段或getter方法的注解 来生成文档的；
     * 所以 Swagger 扫描时就看不到这些 @Schema。
     *
     * 解决方式一：加上 @field: 前缀（推荐 ✅）
     */
    @field:Schema(description = "主键", example = "321283197719123452")
    val id: String,

    @field:Schema(description = "身份证号", example = "321283197719123452", required = true)
    val code: String
)