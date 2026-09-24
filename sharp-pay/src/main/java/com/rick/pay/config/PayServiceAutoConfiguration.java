package com.rick.pay.config;

import com.rick.pay.core.DefaultPayServiceImpl;
import com.rick.pay.core.PayService;
import com.rick.pay.core.alipay.AliPayService;
import com.rick.pay.core.alipay.AliPayServiceImpl;
import com.rick.pay.core.wechat.WeChatPayService;
import com.rick.pay.core.wechat.WeChatPayServiceImpl;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * sharp-pay 自动配置。
 *
 * <p>通道按需装配：仅当对应配置前缀的关键属性存在时才创建该通道的 Bean，
 * 因此只用支付宝的工程无需配置微信（反之亦然）。{@link PayService} 门面在至少一个通道存在时装配，
 * 缺失通道的分派调用会抛 {@link UnsupportedOperationException}。
 *
 * @author Rick
 * @createdAt 2026-09-24 00:00:00
 */
@AutoConfiguration
@EnableConfigurationProperties({WeChatPayProperties.class, AliPayProperties.class})
public class PayServiceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "sharp.pay.wechat", name = "mch-id")
    public WeChatPayService weChatPayService(WeChatPayProperties properties) {
        return new WeChatPayServiceImpl(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "sharp.pay.alipay", name = "app-id")
    public AliPayService aliPayService(AliPayProperties properties) {
        return new AliPayServiceImpl(properties);
    }

    @Bean
    @ConditionalOnMissingBean(PayService.class)
    public PayService payService(ObjectProvider<WeChatPayService> weChatPayServiceProvider,
                                 ObjectProvider<AliPayService> aliPayServiceProvider) {
        return new DefaultPayServiceImpl(weChatPayServiceProvider.getIfAvailable(), aliPayServiceProvider.getIfAvailable());
    }
}
