package com.feiting.feiapiinterface.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 接口服务错误响应视图对象。
 *
 * <p>用于将参数校验、业务拒绝和系统异常统一返回为 JSON，避免在线调用页展示 Spring 默认 HTML 错误页。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterfaceErrorResponse {

    /**
     * 业务错误码。
     */
    private Integer code;

    /**
     * 错误提示信息。
     */
    private String message;

    /**
     * 错误扩展数据。
     */
    private Object data;
}
