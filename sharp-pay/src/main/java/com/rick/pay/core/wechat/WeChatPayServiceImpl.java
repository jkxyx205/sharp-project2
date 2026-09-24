package com.rick.pay.core.wechat;

import com.rick.pay.config.WeChatPayProperties;
import com.rick.pay.core.enums.PayChannel;
import com.rick.pay.core.model.*;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.AutoCertificateNotificationConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.billdownload.BillDownloadServiceExtension;
import com.wechat.pay.java.service.billdownload.DigestBillEntity;
import com.wechat.pay.java.service.billdownload.model.BillType;
import com.wechat.pay.java.service.billdownload.model.GetTradeBillRequest;
import com.wechat.pay.java.service.payments.app.AppService;
import com.wechat.pay.java.service.payments.app.model.Amount;
import com.wechat.pay.java.service.payments.app.model.PrepayRequest;
import com.wechat.pay.java.service.payments.h5.H5Service;
import com.wechat.pay.java.service.payments.h5.model.H5Info;
import com.wechat.pay.java.service.payments.h5.model.SceneInfo;
import com.wechat.pay.java.service.payments.jsapi.JsapiService;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.QueryByOutRefundNoRequest;
import com.wechat.pay.java.service.refund.model.Refund;
import jakarta.servlet.http.HttpServletRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 微信支付（API v3）通道实现，基于官方 SDK {@code wechatpay-java}。
 *
 * <p>签名、平台证书自动下载与回调验签均由 SDK 处理。{@link Config} 在构造时构建一次，复用于各服务。
 *
 * @author Rick
 * @createdAt 2026-09-24 00:00:00
 */
public class WeChatPayServiceImpl implements WeChatPayService {

    private final WeChatPayProperties properties;

    private final Config config;
    private final NativePayService nativePayService;
    private final JsapiService jsapiService;
    private final H5Service h5Service;
    private final AppService appService;
    private final RefundService refundService;
    private final BillDownloadServiceExtension billService;
    private final NotificationParser notificationParser;

    public WeChatPayServiceImpl(WeChatPayProperties properties) {
        this.properties = properties;
        String privateKeyPath = orString(properties.getPrivateKeyPath(), properties.getPrivateKey());
        this.config = new RSAAutoCertificateConfig.Builder()
                .merchantId(properties.getMchId())
                .merchantSerialNumber(properties.getMchSerialNo())
                .privateKeyFromPath(privateKeyPath)
                .apiV3Key(properties.getApiV3Key())
                .build();
        this.nativePayService = new NativePayService.Builder().config(config).build();
        this.jsapiService = new JsapiService.Builder().config(config).build();
        this.h5Service = new H5Service.Builder().config(config).build();
        this.appService = new AppService.Builder().config(config).build();
        this.refundService = new RefundService.Builder().config(config).build();
        this.billService = new BillDownloadServiceExtension.Builder().config(config).build();
        AutoCertificateNotificationConfig notifyConfig = new AutoCertificateNotificationConfig.Builder()
                .merchantId(properties.getMchId())
                .merchantSerialNumber(properties.getMchSerialNo())
                .privateKeyFromPath(privateKeyPath)
                .apiV3Key(properties.getApiV3Key())
                .build();
        this.notificationParser = new NotificationParser(notifyConfig);
    }

    @Override
    public PayResponse createOrder(PayRequest request) {
        return switch (request.getScene()) {
            case NATIVE -> createNative(request);
            case JSAPI -> createJsapi(request);
            case H5 -> createH5(request);
            case APP -> createApp(request);
        };
    }

    private PayResponse createNative(PayRequest request) {
        com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest prepay =
                new com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest();
        prepay.setAppid(properties.getAppId());
        prepay.setMchid(properties.getMchId());
        prepay.setDescription(request.getSubject());
        prepay.setOutTradeNo(request.getOutTradeNo());
        prepay.setNotifyUrl(orString(request.getNotifyUrl(), properties.getNotifyUrl()));
        applyExpire(prepay::setTimeExpire, request.getExpireSeconds());
        com.wechat.pay.java.service.payments.nativepay.model.Amount amount =
                new com.wechat.pay.java.service.payments.nativepay.model.Amount();
        amount.setTotal(request.getAmount());
        prepay.setAmount(amount);
        String codeUrl = nativePayService.prepay(prepay).getCodeUrl();
        return PayResponse.builder()
                .outTradeNo(request.getOutTradeNo())
                .codeUrl(codeUrl)
                .build();
    }

    private PayResponse createJsapi(PayRequest request) {
        com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest prepay =
                new com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest();
        prepay.setAppid(properties.getAppId());
        prepay.setMchid(properties.getMchId());
        prepay.setDescription(request.getSubject());
        prepay.setOutTradeNo(request.getOutTradeNo());
        prepay.setNotifyUrl(orString(request.getNotifyUrl(), properties.getNotifyUrl()));
        applyExpire(prepay::setTimeExpire, request.getExpireSeconds());
        com.wechat.pay.java.service.payments.jsapi.model.Amount amount =
                new com.wechat.pay.java.service.payments.jsapi.model.Amount();
        amount.setTotal(request.getAmount());
        prepay.setAmount(amount);
        Payer payer = new Payer();
        payer.setOpenid(request.getOpenid());
        prepay.setPayer(payer);
        String prepayId = jsapiService.prepay(prepay).getPrepayId();
        Map<String, String> params = new HashMap<>();
        params.put("prepayId", prepayId);
        params.put("appId", properties.getAppId());
        return PayResponse.builder()
                .outTradeNo(request.getOutTradeNo())
                .tradeNo(prepayId)
                .payParams(params)
                .build();
    }

    private PayResponse createH5(PayRequest request) {
        com.wechat.pay.java.service.payments.h5.model.PrepayRequest prepay =
                new com.wechat.pay.java.service.payments.h5.model.PrepayRequest();
        prepay.setAppid(properties.getAppId());
        prepay.setMchid(properties.getMchId());
        prepay.setDescription(request.getSubject());
        prepay.setOutTradeNo(request.getOutTradeNo());
        prepay.setNotifyUrl(orString(request.getNotifyUrl(), properties.getNotifyUrl()));
        applyExpire(prepay::setTimeExpire, request.getExpireSeconds());
        com.wechat.pay.java.service.payments.h5.model.Amount amount =
                new com.wechat.pay.java.service.payments.h5.model.Amount();
        amount.setTotal(request.getAmount());
        prepay.setAmount(amount);
        SceneInfo sceneInfo = new SceneInfo();
        sceneInfo.setPayerClientIp(request.getClientIp());
        H5Info h5Info = new H5Info();
        h5Info.setType("iOS");
        sceneInfo.setH5Info(h5Info);
        prepay.setSceneInfo(sceneInfo);
        String h5Url = h5Service.prepay(prepay).getH5Url();
        return PayResponse.builder()
                .outTradeNo(request.getOutTradeNo())
                .payUrl(h5Url)
                .build();
    }

    private PayResponse createApp(PayRequest request) {
        PrepayRequest prepay = new PrepayRequest();
        prepay.setAppid(properties.getAppId());
        prepay.setMchid(properties.getMchId());
        prepay.setDescription(request.getSubject());
        prepay.setOutTradeNo(request.getOutTradeNo());
        prepay.setNotifyUrl(orString(request.getNotifyUrl(), properties.getNotifyUrl()));
        applyExpire(prepay::setTimeExpire, request.getExpireSeconds());
        Amount amount = new Amount();
        amount.setTotal(request.getAmount());
        prepay.setAmount(amount);
        String prepayId = appService.prepay(prepay).getPrepayId();
        Map<String, String> params = new HashMap<>();
        params.put("prepayId", prepayId);
        params.put("appId", properties.getAppId());
        return PayResponse.builder()
                .outTradeNo(request.getOutTradeNo())
                .tradeNo(prepayId)
                .payParams(params)
                .build();
    }

    @Override
    public PayQueryResponse queryOrder(String outTradeNo) {
        QueryOrderByOutTradeNoRequest queryRequest = new QueryOrderByOutTradeNoRequest();
        queryRequest.setMchid(properties.getMchId());
        queryRequest.setOutTradeNo(outTradeNo);
        Transaction tx = nativePayService.queryOrderByOutTradeNo(queryRequest);
        return PayQueryResponse.builder()
                .channel(PayChannel.WECHAT)
                .outTradeNo(tx.getOutTradeNo())
                .tradeNo(tx.getTransactionId())
                .tradeStatus(tx.getTradeState() == null ? null : tx.getTradeState().toString())
                .amount(tx.getAmount() == null ? null : tx.getAmount().getTotal())
                .payTime(tx.getSuccessTime())
                .buyerId(tx.getPayer() == null ? null : tx.getPayer().getOpenid())
                .raw(tx.toString())
                .build();
    }

    @Override
    public RefundResponse refund(RefundRequest request) {
        CreateRequest createRequest = new CreateRequest();
        createRequest.setOutTradeNo(request.getOutTradeNo());
        createRequest.setOutRefundNo(request.getOutRefundNo());
        createRequest.setReason(request.getReason());
        createRequest.setNotifyUrl(properties.getNotifyUrl());
        AmountReq amount = new AmountReq();
        amount.setTotal(request.getTotalAmount().longValue());
        amount.setRefund(request.getRefundAmount().longValue());
        amount.setCurrency("CNY");
        createRequest.setAmount(amount);
        Refund refund = refundService.create(createRequest);
        return RefundResponse.builder()
                .refundId(refund.getRefundId())
                .outRefundNo(refund.getOutRefundNo())
                .refundStatus(refund.getStatus() == null ? null : refund.getStatus().toString())
                .refundAmount(refund.getAmount() == null ? null : refund.getAmount().getRefund().intValue())
                .build();
    }

    @Override
    public RefundQueryResponse queryRefund(String outRefundNo) {
        QueryByOutRefundNoRequest queryRequest = new QueryByOutRefundNoRequest();
        queryRequest.setOutRefundNo(outRefundNo);
        Refund refund = refundService.queryByOutRefundNo(queryRequest);
        return RefundQueryResponse.builder()
                .refundId(refund.getRefundId())
                .outRefundNo(refund.getOutRefundNo())
                .tradeNo(refund.getTransactionId())
                .outTradeNo(refund.getOutTradeNo())
                .refundStatus(refund.getStatus() == null ? null : refund.getStatus().toString())
                .refundAmount(refund.getAmount() == null ? null : refund.getAmount().getRefund().intValue())
                .build();
    }

    @Override
    public BillDownloadResponse downloadBill(String billDate, String billType) {
        GetTradeBillRequest billRequest = new GetTradeBillRequest();
        billRequest.setBillDate(billDate);
        billRequest.setBillType(parseBillType(billType));
        DigestBillEntity entity = billService.getTradeBill(billRequest);
        return BillDownloadResponse.builder()
                .billStream(entity.getInputStream())
                .billType(billType)
                .build();
    }

    @Override
    public PayNotifyResult parseNotify(HttpServletRequest request) {
        String body = readBody(request);
        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(request.getHeader("Wechatpay-Serial"))
                .nonce(request.getHeader("Wechatpay-Nonce"))
                .signature(request.getHeader("Wechatpay-Signature"))
                .timestamp(request.getHeader("Wechatpay-Timestamp"))
                .body(body)
                .build();
        Transaction tx = notificationParser.parse(requestParam, Transaction.class);
        return PayNotifyResult.builder()
                .channel(PayChannel.WECHAT)
                .outTradeNo(tx.getOutTradeNo())
                .tradeNo(tx.getTransactionId())
                .tradeStatus(tx.getTradeState() == null ? null : tx.getTradeState().toString())
                .amount(tx.getAmount() == null ? null : tx.getAmount().getTotal())
                .payTime(tx.getSuccessTime())
                .buyerId(tx.getPayer() == null ? null : tx.getPayer().getOpenid())
                .replyContent("{\"code\":\"SUCCESS\",\"message\":\"成功\"}")
                .build();
    }

    private BillType parseBillType(String billType) {
        if (billType == null || billType.isBlank()) {
            return BillType.ALL;
        }
        return BillType.valueOf(billType);
    }

    private void applyExpire(Consumer<String> setter, Integer expireSeconds) {
        if (expireSeconds == null) {
            return;
        }
        setter.accept(OffsetDateTime.now(ZoneOffset.ofHours(8)).plusSeconds(expireSeconds)
                .toInstant().toString());
    }

    private String readBody(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取微信支付回调 body 失败", e);
        }
        return sb.toString();
    }

    private static String orString(String preferred, String fallback) {
        return (preferred != null && !preferred.isBlank()) ? preferred : fallback;
    }
}
