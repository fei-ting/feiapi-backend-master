package com.feiting.feiapigateway.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("网关 LogDesensitizeUtils 工具测试")
class LogDesensitizeUtilsGatewayTest {

    private static final String MASK = "******";

    @Nested
    @DisplayName("toSafeQueryParams 方法")
    class ToSafeQueryParamsTests {

        @Test
        @DisplayName("null 返回空 Map")
        void shouldReturnEmptyMapForNull() {
            Map<String, Object> result = LogDesensitizeUtils.toSafeQueryParams(null);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("空 Map 返回空 Map")
        void shouldReturnEmptyMapForEmpty() {
            MultiValueMap<String, String> empty = new LinkedMultiValueMap<>();

            Map<String, Object> result = LogDesensitizeUtils.toSafeQueryParams(empty);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("普通参数保持原样")
        void shouldKeepNormalParams() {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("name", "test");
            params.add("id", "123");

            Map<String, Object> result = LogDesensitizeUtils.toSafeQueryParams(params);

            assertEquals(Collections.singletonList("test"), result.get("name"));
            assertEquals(Collections.singletonList("123"), result.get("id"));
        }

        @Test
        @DisplayName("password 参数被脱敏")
        void shouldMaskPasswordParam() {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("password", "secret");
            params.add("name", "test");

            Map<String, Object> result = LogDesensitizeUtils.toSafeQueryParams(params);

            assertEquals(MASK, result.get("password"));
            assertEquals(Collections.singletonList("test"), result.get("name"));
        }

        @Test
        @DisplayName("sign 参数被脱敏")
        void shouldMaskSignParam() {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("sign", "abc123");

            Map<String, Object> result = LogDesensitizeUtils.toSafeQueryParams(params);

            assertEquals(MASK, result.get("sign"));
        }

        @Test
        @DisplayName("token 参数被脱敏")
        void shouldMaskTokenParam() {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("token", "jwt-value");

            Map<String, Object> result = LogDesensitizeUtils.toSafeQueryParams(params);

            assertEquals(MASK, result.get("token"));
        }

        @Test
        @DisplayName("多值参数保持列表形式")
        void shouldKeepMultiValueParams() {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.put("tags", Arrays.asList("a", "b", "c"));

            Map<String, Object> result = LogDesensitizeUtils.toSafeQueryParams(params);

            assertEquals(Arrays.asList("a", "b", "c"), result.get("tags"));
        }
    }
}
