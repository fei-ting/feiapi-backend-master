package com.feiting.feiapigateway.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("GatewayRequestUtils 网关请求工具测试")
class GatewayRequestUtilsTest {

    @Nested
    @DisplayName("buildRateLimitKey 方法")
    class BuildRateLimitKeyTests {

        @Test
        @DisplayName("正确拼接限流 key")
        void shouldBuildCorrectKey() {
            String key = GatewayRequestUtils.buildRateLimitKey("ak123", "POST", "/api/name/user");

            assertEquals("feiapi:rate_limit:ak123:POST:/api/name/user", key);
        }

        @Test
        @DisplayName("不同参数产生不同 key")
        void shouldDifferWithDifferentParams() {
            String key1 = GatewayRequestUtils.buildRateLimitKey("ak1", "GET", "/api/a");
            String key2 = GatewayRequestUtils.buildRateLimitKey("ak2", "GET", "/api/a");
            String key3 = GatewayRequestUtils.buildRateLimitKey("ak1", "POST", "/api/a");

            assertNotEquals(key1, key2);
            assertNotEquals(key1, key3);
        }
    }

    @Nested
    @DisplayName("resolveClientIp 方法")
    class ResolveClientIpTests {

        @Test
        @DisplayName("优先使用 X-Forwarded-For")
        void shouldUseXForwardedFor() {
            ServerHttpRequest request = mock(ServerHttpRequest.class);
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "10.0.0.1, 192.168.1.1");
            when(request.getHeaders()).thenReturn(headers);

            String ip = GatewayRequestUtils.resolveClientIp(request);

            assertEquals("10.0.0.1", ip);
        }

        @Test
        @DisplayName("无 X-Forwarded-For 时使用 X-Real-IP")
        void shouldUseXRealIp() {
            ServerHttpRequest request = mock(ServerHttpRequest.class);
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Real-IP", "10.0.0.2");
            when(request.getHeaders()).thenReturn(headers);

            String ip = GatewayRequestUtils.resolveClientIp(request);

            assertEquals("10.0.0.2", ip);
        }

        @Test
        @DisplayName("无自定义 Header 时返回 remoteAddress")
        void shouldFallbackToRemoteAddress() {
            ServerHttpRequest request = mock(ServerHttpRequest.class);
            HttpHeaders headers = new HttpHeaders();
            when(request.getHeaders()).thenReturn(headers);
            when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("192.168.1.100", 8080));

            String ip = GatewayRequestUtils.resolveClientIp(request);

            assertEquals("192.168.1.100", ip);
        }

        @Test
        @DisplayName("remoteAddress 为 null 时返回 unknown")
        void shouldReturnUnknownWhenNoRemoteAddress() {
            ServerHttpRequest request = mock(ServerHttpRequest.class);
            HttpHeaders headers = new HttpHeaders();
            when(request.getHeaders()).thenReturn(headers);
            when(request.getRemoteAddress()).thenReturn(null);

            String ip = GatewayRequestUtils.resolveClientIp(request);

            assertEquals("unknown", ip);
        }
    }
}
