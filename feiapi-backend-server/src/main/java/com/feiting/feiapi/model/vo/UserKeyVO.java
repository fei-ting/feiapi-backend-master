package com.feiting.feiapi.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 当前用户访问密钥视图
 */
@Data
public class UserKeyVO implements Serializable {

    /**
     * 签名 accessKey
     */
    private String accessKey;

    /**
     * 签名 secretKey
     */
    private String secretKey;

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;
}
