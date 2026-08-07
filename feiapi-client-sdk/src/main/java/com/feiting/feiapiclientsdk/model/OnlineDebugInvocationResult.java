package com.feiting.feiapiclientsdk.model;

import lombok.Data;

/**
 * SDK 在线调试响应元数据。
 */
@Data
public class OnlineDebugInvocationResult {

    /**
     * HTTP 响应状态码。
     */
    private Integer statusCode;

    /**
     * 原始响应内容类型。
     */
    private String contentType;

    /**
     * 响应正文。
     */
    private String body;

    /**
     * 从发起 HTTP 请求到读取完响应正文的耗时，单位为毫秒。
     */
    private Long durationMs;
}
