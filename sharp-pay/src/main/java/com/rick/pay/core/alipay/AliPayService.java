package com.rick.pay.core.alipay;

import com.rick.pay.core.model.*;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 支付宝通道服务。实现基于官方 SDK {@code alipay-sdk-java}（V2 通用版）。
 *
 * @author Rick
 * @createdAt 2026-09-24 00:00:00
 */
public interface AliPayService {

    PayResponse createOrder(PayRequest request);

    PayQueryResponse queryOrder(String outTradeNo);

    RefundResponse refund(RefundRequest request);

    RefundQueryResponse queryRefund(String outRefundNo);

    BillDownloadResponse downloadBill(String billDate, String billType);

    PayNotifyResult parseNotify(HttpServletRequest request);
}
