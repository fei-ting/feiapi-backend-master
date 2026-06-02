package com.feiting.feiapiclientsdk;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * FeiAPI 客户端配置属性。
 */
@Data
@ConfigurationProperties(prefix = "feiapi.client")
public class FeiapiClientProperties {

    /**
     * 调用方访问标识。
     */
    private String accessKey;

    /**
     * 调用方签名密钥。
     */
    private String secretKey;

    /**
     * 网关基础地址。
     */
    private String gatewayHost;
}
