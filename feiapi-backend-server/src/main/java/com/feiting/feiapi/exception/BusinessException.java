package com.feiting.feiapi.exception;

import com.feiting.feiapi.common.ErrorCode;

/**
 * 自定义异常类
 *
 * @author feiting
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    /**
     * 使用指定业务错误码、公开消息和原始异常创建业务异常。
     *
     * @param errorCode 业务错误码
     * @param message   可安全公开的异常消息
     * @param cause     原始异常
     */
    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.code = errorCode.getCode();
    }

    public int getCode() {
        return code;
    }
}
