package com.rick.pay.core.model;

import com.rick.pay.core.enums.PayChannel;
import com.rick.pay.core.enums.PayScene;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 统一下单请求。通道无关的字段在此，通道特有的参数放 {@link #extras}。
 *
 * <p>金额单位：分（{@code amount}），与微信 API v3 一致；支付宝侧由实现按 1 元 = 100 分换算为元字符串。
 *
 * @author Rick
 * @createdAt 2026-09-24 00:00:00
 */
@Data
@Builder
public class PayRequest {

    /** 支付通道，必填 */
    private PayChannel channel;

    /** 支付场景，必填 */
    private PayScene scene;

    /** 商户订单号（商户侧唯一），必填 */
    private String outTradeNo;

    /** 商品描述/标题，必填 */
    private String subject;

    /** 金额（分），必填 */
    private Integer amount;

    /** 异步回调地址；为空时使用通道配置中的 {@code notifyUrl} */
    private String notifyUrl;

    /**
     * 用户标识。微信 JSAPI 必填（openid），支付宝 JSAPI 为 buyer_id。其余场景忽略。
     */
    private String openid;

    /**
     * 终端 IP。H5 场景必填，其余可选。
     */
    private String clientIp;

    /** 过期时间（秒），可选；为空走通道默认 */
    private Integer expireSeconds;

    /** 通道特有参数透传（如微信 profit_sharing、支付宝 extend_params），可选 */
    private Map<String, String> extras;
}
