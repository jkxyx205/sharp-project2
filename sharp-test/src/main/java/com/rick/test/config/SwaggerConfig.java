package com.rick.test.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Rick.Xu
 * @date 2025/11/13 13:38
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder()
                .displayName("用户API")
                .group("user-api")                  // 分组名称
                .packagesToScan("com.rick.test.module.db.user") // 扫描包
                .pathsToMatch("/users/**")         // 可选，按路径匹配
                .build();
    }

}