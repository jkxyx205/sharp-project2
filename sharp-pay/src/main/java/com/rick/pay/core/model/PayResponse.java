package com.rick.pay.core.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 统一下单响应。
 *
 * <ul>
 *   <li>扫码 NATIVE：取 {@code codeUrl}（微信 code_url / 支付宝 qr_code）。</li>
 *   <li>JSAPI：取 {@code payParams}（微信调起参数 / 支付宝 trade_no+需 SDK 二次签名，统一放 Map）。</li>
 *   <li>H5：取 {@code payUrl}（微信 h5_url / 支付宝 form 表单 html）。</li>
 *   <li>APP：取 {@code payParams}（微信预支付交易会话标识 / 支付宝 orderInfoStr）。</li>
 * </ul>
 *
 * @author Rick
 * @createdAt 2026-09-24 00:00:00
 */
@Data
@Builder
public class PayResponse {

    /** 通道订单号（微信 transaction_id 暂不下发，取 prepayId；支付宝 trade_no） */
    private String tradeNo;

    /** 商户订单号回显 */
    private String outTradeNo;

    /** 扫码支付链接（NATIVE 场景） */
    private String codeUrl;

    /** H5 跳转链接或支付表单 HTML（H5 场景） */
    private String payUrl;

    /** JSAPI/App 调起参数（键值对，业务方按通道规则使用） */
    private Map<String, String> payParams;
}
