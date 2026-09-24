package com.rick.pay.core.alipay;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.*;
import com.alipay.api.response.*;
import com.rick.pay.config.AliPayProperties;
import com.rick.pay.core.enums.PayChannel;
import com.rick.pay.core.model.*;
import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝通道实现，基于官方 SDK {@code alipay-sdk-java}（V2 通用版）。
 *
 * <p>使用公钥模式（{@code alipay-public-key}）；回调验签通过 {@link AlipaySignature#rsaCheckV1}。
 * 金额单位：对外 {@link PayRequest#getAmount()} 为分，内部换算为元字符串后下发。
 *
 * @author Rick
 * @createdAt 2026-09-24 00:00:00
 */
public class AliPayServiceImpl implements AliPayService {

    private final AliPayProperties properties;
    private final AlipayClient client;

    public AliPayServiceImpl(AliPayProperties properties) {
        this.properties = properties;
        this.client = new DefaultAlipayClient(
                properties.getGateway(),
                properties.getAppId(),
                properties.getPrivateKey(),
                properties.getFormat(),
                properties.getCharset(),
                properties.getAlipayPublicKey(),
                properties.getSignType());
    }

    @Override
    public PayResponse createOrder(PayRequest request) {
        return switch (request.getScene()) {
            case NATIVE -> precreate(request);
            case JSAPI -> create(request);
            case H5 -> pagePay(request);
            case APP -> appPay(request);
        };
    }

    private PayResponse precreate(PayRequest request) {
        AlipayTradePrecreateRequest req = new AlipayTradePrecreateRequest();
        req.setBizContent(bizContent(request, false));
        req.setNotifyUrl(orString(request.getNotifyUrl(), properties.getNotifyUrl()));
        try {
            AlipayTradePrecreateResponse resp = client.execute(req);
            ensureSuccess(resp);
            return PayResponse.builder()
                    .outTradeNo(resp.getOutTradeNo())
                    .codeUrl(resp.getQrCode())
                    .build();
        } catch (AlipayApiException e) {
            throw new IllegalStateException("支付宝预下单失败: " + e.getErrMsg(), e);
        }
    }

    private PayResponse create(PayRequest request) {
        AlipayTradeCreateRequest req = new AlipayTradeCreateRequest();
        req.setBizContent(bizContent(request, true));
        req.setNotifyUrl(orString(request.getNotifyUrl(), properties.getNotifyUrl()));
        try {
            AlipayTradeCreateResponse resp = client.execute(req);
            ensureSuccess(resp);
            Map<String, String> params = new HashMap<>();
            params.put("tradeNo", resp.getTradeNo());
            params.put("buyerId", request.getOpenid());
            return PayResponse.builder()
                    .outTradeNo(resp.getOutTradeNo())
                    .tradeNo(resp.getTradeNo())
                    .payParams(params)
                    .build();
        } catch (AlipayApiException e) {
            throw new IllegalStateException("支付宝创建交易失败: " + e.getErrMsg(), e);
        }
    }

    private PayResponse pagePay(PayRequest request) {
        AlipayTradePagePayRequest req = new AlipayTradePagePayRequest();
        req.setBizContent(bizContent(request, false));
        req.setNotifyUrl(orString(request.getNotifyUrl(), properties.getNotifyUrl()));
        try {
            // pageExecute 返回完整支付表单 HTML，业务方直接写入响应或重定向。
            String form = client.pageExecute(req).getBody();
            return PayResponse.builder()
                    .outTradeNo(request.getOutTradeNo())
                    .payUrl(form)
                    .build();
        } catch (AlipayApiException e) {
            throw new IllegalStateException("支付宝电脑网站支付失败: " + e.getErrMsg(), e);
        }
    }

    private PayResponse appPay(PayRequest request) {
        AlipayTradeAppPayRequest req = new AlipayTradeAppPayRequest();
        req.setBizContent(bizContent(request, false));
        req.setNotifyUrl(orString(request.getNotifyUrl(), properties.getNotifyUrl()));
        try {
            // sdkExecute 返回 orderInfoStr，供客户端 SDK 拉起支付。
            String orderInfo = client.sdkExecute(req).getBody();
            Map<String, String> params = new HashMap<>();
            params.put("orderInfo", orderInfo);
            return PayResponse.builder()
                    .outTradeNo(request.getOutTradeNo())
                    .payParams(params)
                    .build();
        } catch (AlipayApiException e) {
            throw new IllegalStateException("支付宝 App 支付失败: " + e.getErrMsg(), e);
        }
    }

    @Override
    public PayQueryResponse queryOrder(String outTradeNo) {
        AlipayTradeQueryRequest req = new AlipayTradeQueryRequest();
        req.setBizContent("{\"out_trade_no\":\"" + escape(outTradeNo) + "\"}");
        try {
            AlipayTradeQueryResponse resp = client.execute(req);
            return PayQueryResponse.builder()
                    .channel(PayChannel.ALIPAY)
                    .outTradeNo(resp.getOutTradeNo())
                    .tradeNo(resp.getTradeNo())
                    .tradeStatus(resp.getTradeStatus())
                    .amount(parseFen(resp.getTotalAmount()))
                    .payTime(resp.getSendPayDate() == null ? null : resp.getSendPayDate().toString())
                    .buyerId(resp.getBuyerOpenId())
                    .raw(resp.getBody())
                    .build();
        } catch (AlipayApiException e) {
            throw new IllegalStateException("支付宝订单查询失败: " + e.getErrMsg(), e);
        }
    }

    @Override
    public RefundResponse refund(RefundRequest request) {
        AlipayTradeRefundRequest req = new AlipayTradeRefundRequest();
        req.setBizContent("{\"out_trade_no\":\"" + escape(request.getOutTradeNo())
                + "\",\"refund_amount\":\"" + yuan(request.getRefundAmount())
                + "\",\"out_request_no\":\"" + escape(request.getOutRefundNo()) + "\""
                + (request.getReason() != null ? ",\"refund_reason\":\"" + escape(request.getReason()) + "\"" : "")
                + "}");
        req.setNotifyUrl(properties.getNotifyUrl());
        try {
            AlipayTradeRefundResponse resp = client.execute(req);
            return RefundResponse.builder()
                    .outRefundNo(request.getOutRefundNo())
                    .refundStatus(resp.isSuccess() ? "SUCCESS" : "FAIL")
                    .refundAmount(parseFen(resp.getRefundFee()))
                    .build();
        } catch (AlipayApiException e) {
            throw new IllegalStateException("支付宝退款失败: " + e.getErrMsg(), e);
        }
    }

    @Override
    public RefundQueryResponse queryRefund(String outRefundNo) {
        AlipayTradeFastpayRefundQueryRequest req = new AlipayTradeFastpayRefundQueryRequest();
        req.setBizContent("{\"out_request_no\":\"" + escape(outRefundNo) + "\",\"out_trade_no\":\"\"}");
        try {
            AlipayTradeFastpayRefundQueryResponse resp = client.execute(req);
            return RefundQueryResponse.builder()
                    .outRefundNo(resp.getOutRequestNo())
                    .tradeNo(resp.getTradeNo())
                    .outTradeNo(resp.getOutTradeNo())
                    .refundStatus(resp.getRefundStatus())
                    .refundAmount(parseFen(resp.getRefundAmount()))
                    .build();
        } catch (AlipayApiException e) {
            throw new IllegalStateException("支付宝退款查询失败: " + e.getErrMsg(), e);
        }
    }

    @Override
    public BillDownloadResponse downloadBill(String billDate, String billType) {
        AlipayDataDataserviceBillDownloadurlQueryRequest req =
                new AlipayDataDataserviceBillDownloadurlQueryRequest();
        String type = orString(billType, "trade");
        req.setBizContent("{\"bill_type\":\"" + escape(type) + "\",\"bill_date\":\"" + escape(billDate) + "\"}");
        try {
            AlipayDataDataserviceBillDownloadurlQueryResponse resp = client.execute(req);
            return BillDownloadResponse.builder()
                    .billUrl(resp.getBillDownloadUrl())
                    .billType(type)
                    .build();
        } catch (AlipayApiException e) {
            throw new IllegalStateException("支付宝账单下载链接获取失败: " + e.getErrMsg(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public PayNotifyResult parseNotify(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> params.put(k, v != null && v.length > 0 ? v[0] : ""));
        try {
            boolean verified = AlipaySignature.rsaCheckV1(
                    params, properties.getAlipayPublicKey(), properties.getCharset(), properties.getSignType());
            if (!verified) {
                throw new IllegalStateException("支付宝回调验签失败");
            }
        } catch (AlipayApiException e) {
            throw new IllegalStateException("支付宝回调验签异常: " + e.getErrMsg(), e);
        }
        return PayNotifyResult.builder()
                .channel(PayChannel.ALIPAY)
                .outTradeNo(params.get("out_trade_no"))
                .tradeNo(params.get("trade_no"))
                .tradeStatus(params.get("trade_status"))
                .amount(parseFen(params.get("total_amount")))
                .payTime(params.get("gmt_payment"))
                .buyerId(params.get("buyer_open_id"))
                .replyContent("success")
                .build();
    }

    /** 组装下单 bizContent（JSON）。includeBuyer 控制是否带 buyer_id（JSAPI 场景）。 */
    private String bizContent(PayRequest request, boolean includeBuyer) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"out_trade_no\":\"").append(escape(request.getOutTradeNo())).append('"');
        sb.append(",\"total_amount\":\"").append(yuan(request.getAmount())).append('"');
        sb.append(",\"subject\":\"").append(escape(request.getSubject())).append('"');
        if (includeBuyer && request.getOpenid() != null && !request.getOpenid().isBlank()) {
            sb.append(",\"buyer_id\":\"").append(escape(request.getOpenid())).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private void ensureSuccess(com.alipay.api.AlipayResponse resp) {
        if (!resp.isSuccess()) {
            throw new IllegalStateException("支付宝接口失败: " + resp.getSubCode() + " / " + resp.getSubMsg());
        }
    }

    private static String yuan(int fen) {
        return new BigDecimal(fen).movePointLeft(2).toPlainString();
    }

    private static Integer parseFen(String yuan) {
        if (yuan == null || yuan.isBlank()) {
            return null;
        }
        return new BigDecimal(yuan).movePointRight(2).intValue();
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String orString(String preferred, String fallback) {
        return (preferred != null && !preferred.isBlank()) ? preferred : fallback;
    }
}
