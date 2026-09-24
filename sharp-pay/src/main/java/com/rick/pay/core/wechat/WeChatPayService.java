package com.rick.pay.core.wechat;

import com.rick.pay.core.model.*;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 微信支付（API v3）通道服务。实现基于官方 SDK {@code wechatpay-java}。
 *
 * @author Rick
 * @createdAt 2026-09-24 00:00:00
 */
public interface WeChatPayService {

    PayResponse createOrder(PayRequest request);

    PayQueryResponse queryOrder(String outTradeNo);

    RefundResponse refund(RefundRequest request);

    RefundQueryResponse queryRefund(String outRefundNo);

    BillDownloadResponse downloadBill(String billDate, String billType);

    PayNotifyResult parseNotify(HttpServletRequest request);
}
