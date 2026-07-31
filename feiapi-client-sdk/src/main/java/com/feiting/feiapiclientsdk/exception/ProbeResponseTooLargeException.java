package com.feiting.feiapiclientsdk.exception;

/**
 * 发布探测响应体超过固定上限异常。
 */
public class ProbeResponseTooLargeException extends RuntimeException {

    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /**
     * 创建发布探测响应超限异常。
     *
     * @param message 可公开的错误消息
     */
    public ProbeResponseTooLargeException(String message) {
        super(message);
    }
}
