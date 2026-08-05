package com.feiting.feiapi.interfaceplatform.publishing.model.enums;

/**
 * 发布探测失败阶段枚举。
 */
public enum PublishProbeFailureStageEnum {

    /**
     * SDK 反射调用、参数调用或客户端执行失败。
     */
    SDK_INVOCATION,

    /**
     * 普通签名或内部探测签名失败。
     */
    GATEWAY_AUTH,

    /**
     * 发布中接口未命中或网关目标路由拒绝。
     */
    GATEWAY_ROUTE,

    /**
     * 连接下游链路超时。
     */
    CONNECTION_TIMEOUT,

    /**
     * 等待或读取响应超时。
     */
    RESPONSE_TIMEOUT,

    /**
     * 单次探测超过总超时时间。
     */
    TOTAL_TIMEOUT,

    /**
     * 下游返回非 2xx 状态。
     */
    DOWNSTREAM_STATUS,

    /**
     * 响应媒体类型、JSON 语法或根类型不匹配。
     */
    RESPONSE_FORMAT,

    /**
     * 结构化响应字段缺失、空值或类型不匹配。
     */
    RESPONSE_STRUCTURE
}
