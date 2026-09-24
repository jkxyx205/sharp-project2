package com.rick.pay.core;

import com.rick.pay.core.model.*;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 支付门面。按 {@link com.rick.pay.core.enums.PayChannel} 分派到对应通道服务。
 *
 * <p>调用方只与本接口交互；通道实现细节（微信 wechatpay-java、支付宝 alipay-sdk-java）不外露。
 *
 * @author Rick
 * @createdAt 2026-09-24 00:00:00
 */
public interface PayService {

    /** 统一下单（扫码/JSAPI/H5/App 由 {@code request.scene} 决定） */
    PayResponse createOrder(PayRequest request);

    /** 查询订单 */
    PayQueryResponse queryOrder(com.rick.pay.core.enums.PayChannel channel, String outTradeNo);

    /** 退款 */
    RefundResponse refund(RefundRequest request);

    /** 查询退款 */
    RefundQueryResponse queryRefund(com.rick.pay.core.enums.PayChannel channel, String outRefundNo);

    /**
     * 下载对账账单。
     *
     * @param billDate 账单日期，格式 yyyy-MM-dd
     * @param billType 账单类型，通道原值（如 trade/flowfund）；为空走通道默认
     */
    BillDownloadResponse downloadBill(com.rick.pay.core.enums.PayChannel channel, String billDate, String billType);

    /**
     * 解析并验签异步通知。从 {@code request} 读取通道约定的 body/headers/form，
     * 验签后返回结构化结果。返回的 {@link PayNotifyResult#getReplyContent()} 用于回写通道。
     */
    PayNotifyResult parseNotify(HttpServletRequest request);
}
