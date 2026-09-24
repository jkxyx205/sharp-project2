package com.rick.pay.core.enums;

/**
 * 支付场景。决定 {@link com.rick.pay.core.model.PayRequest} 在各通道走哪种下单接口。
 *
 * @author Rick
 * @createdAt 2026-09-24 00:00:00
 */
public enum PayScene {

    /** 扫码支付（微信 Native / 支付宝 当面付 precreate） */
    NATIVE,

    /** 公众号/小程序支付（微信 JSAPI / 支付宝 JSAPI） */
    JSAPI,

    /** 手机浏览器支付（微信 H5 / 支付宝 电脑网站 alipay.trade.page） */
    H5,

    /** App 拉起支付（微信 App / 支付宝 App） */
    APP
}
