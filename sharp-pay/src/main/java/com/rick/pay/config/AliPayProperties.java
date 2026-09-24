package com.rick.pay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付宝配置（通用版 SDK，alipay-sdk-java）。
 *
 * <p>对应配置前缀 {@code sharp.pay.alipay}。仅在配置了 {@code app-id} 时该通道才会被装配
 * （见 {@link PayServiceAutoConfiguration} 的 {@code @ConditionalOnProperty}）。
 *
 * @author Rick
 * @createdAt 2026-09-24 00:00:00
 */
@Data
@ConfigurationProperties(prefix = "sharp.pay.alipay")
public class AliPayProperties {

    /** 应用 APPID */
    private String appId;

    /** 应用私钥（PKCS8 PEM 内容，不含 BEGIN/END 行） */
    private String privateKey;

    /** 支付宝公钥 */
    private String alipayPublicKey;

    /** 签名算法，默认 RSA2 */
    private String signType = "RSA2";

    /** 字符集，默认 UTF-8 */
    private String charset = "UTF-8";

    /** 数据格式，默认 json */
    private String format = "json";

    /** 网关地址：正式 {@code https://openapi.alipay.com/gateway.do}，沙箱 {@code ...openapi.alipaydev.com/gateway.do} */
    private String gateway;

    /** 异步通知地址（https） */
    private String notifyUrl;

    /** 可选：应用公钥证书路径（证书模式） */
    private String appCertPath;

    /** 可选：支付宝公钥证书路径（证书模式） */
    private String alipayPublicCertPath;

    /** 可选：根证书路径（证书模式） */
    private String rootCertPath;
}
