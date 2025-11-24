package com.rick.formflow.config;


import com.rick.db.config.SharpDatabaseAutoConfiguration;
import com.rick.db.plugin.page.GridService;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * All rights Reserved, Designed By www.xhope.top
 *
 * @version V1.0
 * @Description: 自动配置类
 * @author: Rick.Xu
 * @date: 9/12/19 7:42 PM
 * @Copyright: 2019 www.yodean.com. All rights reserved.
 */
@Configuration
@ConditionalOnSingleCandidate(GridService.class)
@AutoConfigureAfter({SharpDatabaseAutoConfiguration.class})
@Import(FormServiceConfiguration.class)
public class FormFlowServiceAutoConfiguration {

    public FormFlowServiceAutoConfiguration() {
    }


}
