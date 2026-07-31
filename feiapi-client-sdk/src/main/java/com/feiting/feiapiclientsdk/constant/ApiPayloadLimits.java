package com.feiting.feiapiclientsdk.constant;

/**
 * FeiAPI 签名请求与发布探测响应固定报文上限。
 */
public final class ApiPayloadLimits {

    /** SDK 与网关签名请求体最大 UTF-8 字节数。 */
    public static final int MAX_SIGNED_REQUEST_BODY_BYTES = 65535;

    /** 发布探测响应体最大字节数。 */
    public static final int MAX_PROBE_RESPONSE_BODY_BYTES = 1024 * 1024;

    /**
     * 常量类禁止实例化。
     */
    private ApiPayloadLimits() {
    }
}
