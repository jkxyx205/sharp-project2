package com.rick.formflow.config;

import com.rick.formflow.form.service.FormAdvice;
import com.rick.formflow.form.service.bo.FormBO;
import com.rick.formflow.form.service.convert.DateTimeToStringConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ComponentScan(value = "com.rick.formflow.form")
public class FormServiceConfiguration {

    @Bean
    public DateTimeToStringConverter dateTimeToStringConverter() {
        return new DateTimeToStringConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    public FormAdvice formAdvice() {
        return new FormAdvice() {
            @Override
            public void beforeInstanceHandle(FormBO form, Long instanceId, Map<String, Object> values) {

            }

            @Override
            public void afterInstanceHandle(FormBO form, Long instanceId, Map<String, Object> values) {

            }

//                @Override
//                public Map<String, Object> getValue(Long formId, Long instanceId) {
//                    return Collections.emptyMap();
//                }
        };
    }
}