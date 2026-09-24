package com.rick.pay.core.model;

import com.rick.pay.core.enums.PayChannel;
import lombok.Builder;
import lombok.Data;

/**
 * 异步通知解析结果。由 {@link com.rick.pay.core.PayService#parseNotify} 解析并验签后返回。
 *
 * @author Rick
 * @createdAt 2026-09-24 00:00:00
 */
@Data
@Builder
public class PayNotifyResult {

    private PayChannel channel;

    /** 商户订单号 */
    private String outTradeNo;

    /** 通道订单号 */
    private String tradeNo;

    /** 交易状态：SUCCESS / 等等 */
    private String tradeStatus;

    /** 实付金额（分） */
    private Integer amount;

    /** 支付完成时间 */
    private String payTime;

    /** 买家标识 */
    private String buyerId;

    /** 通道要求回写的应答报文（如微信 SUCCESS，支付宝 "success"） */
    private String replyContent;
}
