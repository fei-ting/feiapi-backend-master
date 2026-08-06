package com.feiting.feiapi.interfaceplatform.definition.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理员可选 SDK 方法视图对象。
 */
@Data
public class SdkMethodOptionVO implements Serializable {

    /**
     * SDK 方法名。
     */
    private String sdkMethodName;

    /**
     * SDK 方法是否需要请求参数。
     */
    private Boolean needParams;

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;
}
