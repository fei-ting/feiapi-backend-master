package com.feiting.feiapigateway.utils;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetSocketAddress;

/**
 * 网关请求工具类测试
 */
class GatewayRequestUtilsTests {

    @Test
    void shouldBuildRateLimitKeyByAccessKeyMethodAndPath() {
        String key = GatewayRequestUtils.buildRateLimitKey("ak123", "GET", "/api/test");
        assertEquals("feiapi:rate_limit:ak123:GET:/api/test", key);
    }

    @Test
    void shouldResolveClientIpFromForwardedHeaderFirst() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/")
                .header("X-Forwarded-For", "10.0.0.1, 10.0.0.2")
                .build();

        assertEquals("10.0.0.1", GatewayRequestUtils.resolveClientIp(request));
    }

    @Test
    void shouldResolveClientIpFromRemoteAddressWhenNoProxyHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                .build();

        assertEquals("127.0.0.1", GatewayRequestUtils.resolveClientIp(request));
    }
}
