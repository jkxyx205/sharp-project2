package com.rick.pay.core.model;

import lombok.Builder;
import lombok.Data;

/**
 * 退款响应。
 *
 * @author Rick
 * @createdAt 2026-09-24 00:00:00
 */
@Data
@Builder
public class RefundResponse {

    /** 通道退款单号 */
    private String refundId;

    /** 商户退款单号 */
    private String outRefundNo;

    /** 退款状态：SUCCESS / PROCESSING / FAIL 等 */
    private String refundStatus;

    /** 退款金额（分） */
    private Integer refundAmount;
}
