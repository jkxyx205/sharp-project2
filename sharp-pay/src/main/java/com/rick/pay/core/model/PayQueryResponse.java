package com.rick.pay.core.model;

import com.rick.pay.core.enums.PayChannel;
import lombok.Builder;
import lombok.Data;

/**
 * 订单查询响应（统一字段）。
 *
 * @author Rick
 * @createdAt 2026-09-24 00:00:00
 */
@Data
@Builder
public class PayQueryResponse {

    private PayChannel channel;

    /** 商户订单号 */
    private String outTradeNo;

    /** 通道订单号 */
    private String tradeNo;

    /** 交易状态：SUCCESS / NOTPAY / REFUND / CLOSED 等（通道原值映射） */
    private String tradeStatus;

    /** 实付金额（分） */
    private Integer amount;

    /** 支付完成时间（通道原始字符串） */
    private String payTime;

    /** 买家标识（微信 openid / 支付宝 buyer_open_id） */
    private String buyerId;

    /** 原始响应（调试用） */
    private String raw;
}
