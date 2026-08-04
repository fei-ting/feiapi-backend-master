package com.feiting.feiapiclientsdk.model;

import lombok.Data;

/**
 * SDK 发布探测响应元数据。
 */
@Data
public class ProbeInvocationResult {

    /**
     * HTTP 响应状态码。
     */
    private Integer statusCode;

    /**
     * 原始响应内容类型。
     */
    private String contentType;

    /**
     * 受大小限制读取的响应体。
     */
    private String body;

    /**
     * 网关受控失败阶段。
     */
    private String gatewayFailureStage;
}
