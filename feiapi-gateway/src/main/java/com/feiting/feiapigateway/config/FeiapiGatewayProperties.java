package com.feiting.feiapigateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关自定义配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "feiapi.gateway")
public class FeiapiGatewayProperties {

    private String interfaceHost = "http://localhost:8123";
    private String probeSecret;
    private InterfaceTarget interfaceTarget = new InterfaceTarget();
    private RateLimit rateLimit = new RateLimit();

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

    /**
     * 接口真实目标地址安全配置
     */
    @Data
    public static class InterfaceTarget {

        /**
         * 允许转发的接口真实目标主机名白名单
         */
        private List<String> allowedHostnames = new ArrayList<>(List.of("feiapi-interface"));
    }

    /**
     * 网关限流配置
     */
    @Data
    public static class RateLimit {

        /**
         * 每个时间窗口最多允许的请求数
         */
        private int maxRequests = 20;

        /**
         * 限流时间窗口，单位：秒
         */
        private int windowSeconds = 1;
    }
}
