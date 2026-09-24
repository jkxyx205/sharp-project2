package com.rick.pay.core;

import com.rick.pay.core.alipay.AliPayService;
import com.rick.pay.core.enums.PayChannel;
import com.rick.pay.core.model.*;
import com.rick.pay.core.wechat.WeChatPayService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 门面实现：按 {@link PayChannel} 分派到对应通道。缺失通道的分派抛 {@link UnsupportedOperationException}。
 *
 * @author Rick
 * @createdAt 2026-09-24 00:00:00
 */
@RequiredArgsConstructor
public class DefaultPayServiceImpl implements PayService {

    private final WeChatPayService weChatPayService;
    private final AliPayService aliPayService;

    @Override
    public PayResponse createOrder(PayRequest request) {
        return routeCreate(request);
    }

    @Override
    public PayQueryResponse queryOrder(PayChannel channel, String outTradeNo) {
        return switch (channel) {
            case WECHAT -> requireWechat().queryOrder(outTradeNo);
            case ALIPAY -> requireAlipay().queryOrder(outTradeNo);
        };
    }

    @Override
    public RefundResponse refund(RefundRequest request) {
        return switch (request.getChannel()) {
            case WECHAT -> requireWechat().refund(request);
            case ALIPAY -> requireAlipay().refund(request);
        };
    }

    @Override
    public RefundQueryResponse queryRefund(PayChannel channel, String outRefundNo) {
        return switch (channel) {
            case WECHAT -> requireWechat().queryRefund(outRefundNo);
            case ALIPAY -> requireAlipay().queryRefund(outRefundNo);
        };
    }

    @Override
    public BillDownloadResponse downloadBill(PayChannel channel, String billDate, String billType) {
        return switch (channel) {
            case WECHAT -> requireWechat().downloadBill(billDate, billType);
            case ALIPAY -> requireAlipay().downloadBill(billDate, billType);
        };
    }

    @Override
    public PayNotifyResult parseNotify(HttpServletRequest request) {
        // 按路径约定：业务层回调 URL 区分通道；通道由请求特征判定更稳妥——这里按 header 判定。
        PayChannel channel = resolveChannel(request);
        return switch (channel) {
            case WECHAT -> requireWechat().parseNotify(request);
            case ALIPAY -> requireAlipay().parseNotify(request);
        };
    }

    private PayResponse routeCreate(PayRequest request) {
        return switch (request.getChannel()) {
            case WECHAT -> requireWechat().createOrder(request);
            case ALIPAY -> requireAlipay().createOrder(request);
        };
    }

    /**
     * 回调通道判定：微信 v3 回调带 Wechatpay-Signature 头；支付宝回调表单含 {@code notify_id}。
     */
    private PayChannel resolveChannel(HttpServletRequest request) {
        String wechatSign = request.getHeader("Wechatpay-Signature");
        if (wechatSign != null && !wechatSign.isBlank()) {
            return PayChannel.WECHAT;
        }
        String notifyId = request.getParameter("notify_id");
        if (notifyId != null && !notifyId.isBlank()) {
            return PayChannel.ALIPAY;
        }
        throw new IllegalArgumentException("无法识别支付回调通道：缺少 Wechatpay-Signature 头或 notify_id 参数");
    }

    private WeChatPayService requireWechat() {
        if (weChatPayService == null) {
            throw new UnsupportedOperationException("微信支付通道未配置（缺少 sharp.pay.wechat.mch-id）");
        }
        return weChatPayService;
    }

    private AliPayService requireAlipay() {
        if (aliPayService == null) {
            throw new UnsupportedOperationException("支付宝通道未配置（缺少 sharp.pay.alipay.app-id）");
        }
        return aliPayService;
    }
}
