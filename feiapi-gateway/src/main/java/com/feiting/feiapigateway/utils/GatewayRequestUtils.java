package com.feiting.feiapigateway.utils;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;

/**
 * 网关请求工具类
 */
public final class GatewayRequestUtils {

    private static final String UNKNOWN_CLIENT = "unknown";
    private static final String RATE_LIMIT_KEY_PREFIX = "feiapi:rate_limit:";

    private GatewayRequestUtils() {
    }

    public static String resolveClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp.trim();
        }

        if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getHostString();
        }
        return UNKNOWN_CLIENT;
    }

    public static String buildRateLimitKey(String accessKey, String method, String requestPath) {
        return RATE_LIMIT_KEY_PREFIX + accessKey + ":" + method + ":" + requestPath;
    }
}
