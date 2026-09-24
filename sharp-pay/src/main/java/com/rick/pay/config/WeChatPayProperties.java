package com.rick.pay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 微信支付（API v3）配置。
 *
 * <p>对应配置前缀 {@code sharp.pay.wechat}。仅在配置了 {@code mch-id} 时该通道才会被装配
 * （见 {@link PayServiceAutoConfiguration} 的 {@code @ConditionalOnProperty}）。
 *
 * @author Rick
 * @createdAt 2026-09-24 00:00:00
 */
@Data
@ConfigurationProperties(prefix = "sharp.pay.wechat")
public class WeChatPayProperties {

    /** 商户号 */
    private String mchId;

    /** 应用 AppId（公众号/小程序/移动应用），下单必填 */
    private String appId;

    /** 商户 API 证书序列号 */
    private String mchSerialNo;

    /**
     * 商户 API 私钥（PEM 内容）。与 {@link #privateKeyPath} 二选一，{@code privateKey} 优先。
     */
    private String privateKey;

    /** 商户 API 私钥文件路径（PEM）。与 {@code #privateKey} 二选一。 */
    private String privateKeyPath;

    /** API v3 密钥 */
    private String apiV3Key;

    /**
     * 微信支付平台证书路径（PEM）。可为空——SDK 会自动下载并轮换平台证书。
     */
    private String certPath;

    /** 异步通知地址（https），用于回调验签 */
    private String notifyUrl;

    /** 可选：资源相对根路径，用于本地退款/对账等场景的本地路径拼接 */
    private String basePath;
}
