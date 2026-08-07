package com.feiting.feiapi.interfaceplatform.invocation.model.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/**
 * 在线调试调用结果视图对象。
 */
@Data
public class InterfaceInvokeResultVO {

    /**
     * 下游 HTTP 响应是否为 2xx。
     */
    @NotNull
    private Boolean successful;

    /**
     * 下游 HTTP 响应状态码；未收到 HTTP 响应时为空。
     */
    @PositiveOrZero
    private Integer statusCode;

    /**
     * 从开始执行 SDK 方法到调用结束的耗时，单位为毫秒。
     */
    @NotNull
    @PositiveOrZero
    private Long durationMs;

    /**
     * 下游响应内容类型；未收到 HTTP 响应或响应未声明时为空。
     */
    private String contentType;

    /**
     * 下游公开响应正文。
     */
    private String body;

    /**
     * 未收到 HTTP 响应时可安全展示的错误信息。
     */
    private String errorMessage;
}
