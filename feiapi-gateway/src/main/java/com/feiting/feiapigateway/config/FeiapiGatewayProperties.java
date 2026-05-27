package com.feiting.feiapigateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 网关自定义配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "feiapi.gateway")
public class FeiapiGatewayProperties {

    private String interfaceHost = "http://localhost:8123";

    public String getNormalizedInterfaceHost() {
        if (interfaceHost == null || interfaceHost.trim().isEmpty()) {
            return "http://localhost:8123";
        }
        String normalizedInterfaceHost = interfaceHost.trim();
        while (normalizedInterfaceHost.endsWith("/")) {
            normalizedInterfaceHost = normalizedInterfaceHost.substring(0, normalizedInterfaceHost.length() - 1);
        }
        return normalizedInterfaceHost;
    }
}
