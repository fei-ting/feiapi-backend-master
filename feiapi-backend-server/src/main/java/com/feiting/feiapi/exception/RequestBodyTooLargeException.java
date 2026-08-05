package com.feiting.feiapi.exception;

/**
 * 请求正文超过允许字节上限异常。
 */
public class RequestBodyTooLargeException extends RuntimeException {

    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /**
     * 创建请求正文超限异常。
     *
     * @param message 可公开的错误消息
     */
    public RequestBodyTooLargeException(String message) {
        super(message);
    }
}
