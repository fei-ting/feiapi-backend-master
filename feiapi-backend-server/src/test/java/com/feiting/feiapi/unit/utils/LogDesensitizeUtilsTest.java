package com.feiting.feiapi.unit.utils;

import com.feiting.feiapi.utils.LogDesensitizeUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LogDesensitizeUtils 日志脱敏工具测试")
class LogDesensitizeUtilsTest {

    private static final String MASK = "******";

    @Nested
    @DisplayName("toSafeJson 方法")
    class ToSafeJsonTests {

        @Test
        @DisplayName("null 参数返回空数组")
        void shouldReturnEmptyArrayForNull() {
            assertEquals("[]", LogDesensitizeUtils.toSafeJson(null));
        }

        @Test
        @DisplayName("空数组参数返回空数组")
        void shouldReturnEmptyArrayForEmptyArgs() {
            assertEquals("[]", LogDesensitizeUtils.toSafeJson(new Object[]{}));
        }

        @Test
        @DisplayName("普通对象正常序列化")
        void shouldSerializeNormalObject() {
            Map<String, String> obj = new HashMap<>();
            obj.put("name", "test");

            String result = LogDesensitizeUtils.toSafeJson(new Object[]{obj});

            assertTrue(result.contains("\"name\""));
            assertTrue(result.contains("\"test\""));
        }

        @Test
        @DisplayName("包含 password 字段的对象被脱敏")
        void shouldMaskPasswordField() {
            Map<String, String> obj = new HashMap<>();
            obj.put("userPassword", "secret123");
            obj.put("name", "test");

            String result = LogDesensitizeUtils.toSafeJson(new Object[]{obj});

            assertTrue(result.contains(MASK));
            assertFalse(result.contains("secret123"));
            assertTrue(result.contains("\"test\""));
        }

        @Test
        @DisplayName("包含 secretKey 字段被脱敏")
        void shouldMaskSecretKeyField() {
            Map<String, String> obj = new HashMap<>();
            obj.put("secretKey", "my-secret");

            String result = LogDesensitizeUtils.toSafeJson(new Object[]{obj});

            assertTrue(result.contains(MASK));
            assertFalse(result.contains("my-secret"));
        }

        @Test
        @DisplayName("包含 accessKey 字段被脱敏")
        void shouldMaskAccessKeyField() {
            Map<String, String> obj = new HashMap<>();
            obj.put("accessKey", "my-access");

            String result = LogDesensitizeUtils.toSafeJson(new Object[]{obj});

            assertTrue(result.contains(MASK));
            assertFalse(result.contains("my-access"));
        }

        @Test
        @DisplayName("包含 token 字段被脱敏")
        void shouldMaskTokenField() {
            Map<String, String> obj = new HashMap<>();
            obj.put("token", "jwt-token-value");

            String result = LogDesensitizeUtils.toSafeJson(new Object[]{obj});

            assertTrue(result.contains(MASK));
            assertFalse(result.contains("jwt-token-value"));
        }

        @Test
        @DisplayName("嵌套对象中的敏感字段被脱敏")
        void shouldMaskNestedSensitiveFields() {
            Map<String, Object> inner = new HashMap<>();
            inner.put("password", "nested-secret");
            inner.put("name", "inner-name");

            Map<String, Object> outer = new HashMap<>();
            outer.put("data", inner);

            String result = LogDesensitizeUtils.toSafeJson(new Object[]{outer});

            assertTrue(result.contains(MASK));
            assertFalse(result.contains("nested-secret"));
            assertTrue(result.contains("inner-name"));
        }

        @Test
        @DisplayName("数组中的敏感字段被脱敏")
        void shouldMaskSensitiveFieldsInArray() {
            Map<String, String> item = new HashMap<>();
            item.put("password", "arr-secret");
            item.put("id", "123");

            String result = LogDesensitizeUtils.toSafeJson(new Object[]{Collections.singletonList(item)});

            assertTrue(result.contains(MASK));
            assertFalse(result.contains("arr-secret"));
        }
    }

    @Nested
    @DisplayName("toSafeMap 方法")
    class ToSafeMapTests {

        @Test
        @DisplayName("null 返回空 Map")
        void shouldReturnEmptyMapForNull() {
            Map<String, Object> result = LogDesensitizeUtils.toSafeMap(null);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("空 Map 返回空 Map")
        void shouldReturnEmptyMapForEmpty() {
            Map<String, Object> result = LogDesensitizeUtils.toSafeMap(Collections.emptyMap());

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("普通字段保持原样")
        void shouldKeepNormalFields() {
            Map<String, String> source = new HashMap<>();
            source.put("name", "test");
            source.put("id", "123");

            Map<String, Object> result = LogDesensitizeUtils.toSafeMap(source);

            assertEquals("test", result.get("name"));
            assertEquals("123", result.get("id"));
        }

        @Test
        @DisplayName("password 字段被脱敏")
        void shouldMaskPasswordField() {
            Map<String, String> source = new HashMap<>();
            source.put("password", "secret");
            source.put("name", "test");

            Map<String, Object> result = LogDesensitizeUtils.toSafeMap(source);

            assertEquals(MASK, result.get("password"));
            assertEquals("test", result.get("name"));
        }

        @Test
        @DisplayName("大小写不敏感的敏感字段检测")
        void shouldDetectCaseInsensitiveSensitiveFields() {
            Map<String, String> source = new HashMap<>();
            source.put("PASSWORD", "secret1");
            source.put("SecretKey", "secret2");
            source.put("ACCESSKEY", "secret3");

            Map<String, Object> result = LogDesensitizeUtils.toSafeMap(source);

            assertEquals(MASK, result.get("PASSWORD"));
            assertEquals(MASK, result.get("SecretKey"));
            assertEquals(MASK, result.get("ACCESSKEY"));
        }

        @Test
        @DisplayName("包含 password 子串的字段被脱敏")
        void shouldMaskFieldsContainingPassword() {
            Map<String, String> source = new HashMap<>();
            source.put("oldPassword", "old");
            source.put("newPassword", "new");
            source.put("checkPassword", "check");

            Map<String, Object> result = LogDesensitizeUtils.toSafeMap(source);

            assertEquals(MASK, result.get("oldPassword"));
            assertEquals(MASK, result.get("newPassword"));
            assertEquals(MASK, result.get("checkPassword"));
        }
    }
}
