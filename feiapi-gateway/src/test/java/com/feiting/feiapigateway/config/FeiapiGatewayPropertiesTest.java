package com.feiting.feiapigateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FeiapiGatewayProperties 网关配置测试")
class FeiapiGatewayPropertiesTest {

    @Nested
    @DisplayName("getNormalizedInterfaceHost 方法")
    class GetNormalizedInterfaceHostTests {

        @Test
        @DisplayName("正常 URL 保持不变")
        void shouldReturnNormalUrl() {
            FeiapiGatewayProperties props = new FeiapiGatewayProperties();
            props.setInterfaceHost("http://localhost:8123");

            assertEquals("http://localhost:8123", props.getNormalizedInterfaceHost());
        }

        @Test
        @DisplayName("末尾单个斜杠被去除")
        void shouldRemoveTrailingSlash() {
            FeiapiGatewayProperties props = new FeiapiGatewayProperties();
            props.setInterfaceHost("http://localhost:8123/");

            assertEquals("http://localhost:8123", props.getNormalizedInterfaceHost());
        }

        @Test
        @DisplayName("末尾多个斜杠被去除")
        void shouldRemoveMultipleTrailingSlashes() {
            FeiapiGatewayProperties props = new FeiapiGatewayProperties();
            props.setInterfaceHost("http://localhost:8123///");

            assertEquals("http://localhost:8123", props.getNormalizedInterfaceHost());
        }

        @Test
        @DisplayName("null 返回默认值")
        void shouldReturnDefaultForNull() {
            FeiapiGatewayProperties props = new FeiapiGatewayProperties();
            props.setInterfaceHost(null);

            assertEquals("http://localhost:8123", props.getNormalizedInterfaceHost());
        }

        @Test
        @DisplayName("空字符串返回默认值")
        void shouldReturnDefaultForEmpty() {
            FeiapiGatewayProperties props = new FeiapiGatewayProperties();
            props.setInterfaceHost("   ");

            assertEquals("http://localhost:8123", props.getNormalizedInterfaceHost());
        }

        @Test
        @DisplayName("两端空白被去除")
        void shouldTrimWhitespace() {
            FeiapiGatewayProperties props = new FeiapiGatewayProperties();
            props.setInterfaceHost("  http://localhost:8123  ");

            assertEquals("http://localhost:8123", props.getNormalizedInterfaceHost());
        }
    }

    @Nested
    @DisplayName("RateLimit 默认值")
    class RateLimitDefaultsTests {

        @Test
        @DisplayName("默认 maxRequests 为 20")
        void shouldHaveDefaultMaxRequests() {
            FeiapiGatewayProperties props = new FeiapiGatewayProperties();

            assertEquals(20, props.getRateLimit().getMaxRequests());
        }

        @Test
        @DisplayName("默认 windowSeconds 为 1")
        void shouldHaveDefaultWindowSeconds() {
            FeiapiGatewayProperties props = new FeiapiGatewayProperties();

            assertEquals(1, props.getRateLimit().getWindowSeconds());
        }
    }
}
