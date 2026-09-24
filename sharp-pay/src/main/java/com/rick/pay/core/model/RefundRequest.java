package com.rick.pay.core.model;

import com.rick.pay.core.enums.PayChannel;
import lombok.Builder;
import lombok.Data;

/**
 * 退款请求。
 *
 * @author Rick
 * @createdAt 2026-09-24 00:00:00
 */
@Data
@Builder
public class RefundRequest {

    private PayChannel channel;

    /** 商户订单号（与通道订单号二选一） */
    private String outTradeNo;

    /** 通道订单号（与商户订单号二选一，优先） */
    private String tradeNo;

    /** 商户退款单号，必填 */
    private String outRefundNo;

    /** 订单总金额（分），必填 */
    private Integer totalAmount;

    /** 退款金额（分），必填 */
    private Integer refundAmount;

    /** 退款原因，可选 */
    private String reason;
}
