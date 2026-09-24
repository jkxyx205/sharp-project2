package com.rick.pay.core.model;

import lombok.Builder;
import lombok.Data;

import java.io.InputStream;

/**
 * 对账账单下载响应。{@link #billStream} 由业务方负责关闭。
 *
 * @author Rick
 * @createdAt 2026-09-24 00:00:00
 */
@Data
@Builder
public class BillDownloadResponse {

    /** 账单下载链接（部分通道直接返回 url，部分返回流） */
    private String billUrl;

    /** 账单内容流（gzip/zip 原样，业务方自行解压） */
    private InputStream billStream;

    /** 账单类型提示（如 trade/bill，通道原值） */
    private String billType;
}
