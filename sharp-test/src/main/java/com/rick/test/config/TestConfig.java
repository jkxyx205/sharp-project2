package com.rick.test.config;

import com.rick.common.http.web.SharpWebMvcConfigurer;
import com.rick.db.repository.support.IdToEntityConverterFactory;
import com.rick.db.repository.support.InsertUpdateCallback;
import com.rick.db.repository.support.baseinfo.ExtendInsertUpdateCallback;
import com.rick.db.repository.support.baseinfo.ExtendTableDAOImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;

/**
 * @author Rick.Xu
 * @date 2025/11/10 16:48
 */
@Configuration
@ComponentScan(basePackageClasses = {TestApiExceptionHandler.class})
public class TestConfig extends SharpWebMvcConfigurer {

    @Bean
    public ExtendTableDAOImpl tableDAO(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        return new ExtendTableDAOImpl(namedParameterJdbcTemplate);
    }

    @Bean
    public InsertUpdateCallback insertCallback() {
        return new ExtendInsertUpdateCallback();
    }

    @Override
    public List<ConverterFactory> converterFactories() {
        // 发起 GET 请求的时候，允许值映射到实体对象的 id 字段上，不常用，提供传参的多样性
        // private Person person;
        // GET person = "1" => person.setId(1L)
        return List.of(new IdToEntityConverterFactory());
    }

}

