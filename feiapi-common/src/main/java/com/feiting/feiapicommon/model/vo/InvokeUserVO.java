package com.feiting.feiapicommon.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 内部调用用户视图
 *
 * <p>仅用于网关通过 Dubbo 获取验签和计费所需的最小用户信息。</p>
 */
@Data
public class InvokeUserVO implements Serializable {

    /**
     * 用户 id
     */
    private Long id;

    /**
     * 调用方 accessKey
     */
    private String accessKey;

    /**
     * 调用方 secretKey，网关验签必需
     */
    private String secretKey;

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;
}
