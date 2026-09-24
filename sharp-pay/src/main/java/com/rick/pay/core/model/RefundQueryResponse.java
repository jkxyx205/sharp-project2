package com.rick.pay.core.model;

import lombok.Builder;
import lombok.Data;

/**
 * 退款查询响应。
 *
 * @author Rick
 * @createdAt 2026-09-24 00:00:00
 */
@Data
@Builder
public class RefundQueryResponse {

    /** 通道退款单号 */
    private String refundId;

    /** 商户退款单号 */
    private String outRefundNo;

    /** 通道订单号 */
    private String tradeNo;

    /** 商户订单号 */
    private String outTradeNo;

    /** 退款状态 */
    private String refundStatus;

    /** 退款金额（分） */
    private Integer refundAmount;
}
