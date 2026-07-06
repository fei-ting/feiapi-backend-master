package com.feiting.feiapiclientsdk.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FeiApiClient 客户端测试")
class FeiApiClientTest {

    @Nested
    @DisplayName("构造函数与 gatewayHost 归一化")
    class ConstructorTests {

        @Test
        @DisplayName("无参构造函数不抛异常")
        void shouldCreateWithNoArgs() {
            FeiApiClient client = new FeiApiClient();
            assertNotNull(client);
        }

        @Test
        @DisplayName("两参构造函数")
        void shouldCreateWithAccessKeyAndSecretKey() {
            FeiApiClient client = new FeiApiClient("ak", "sk");
            assertNotNull(client);
        }

        @Test
        @DisplayName("三参构造函数，gatewayHost 末尾斜杠被去除")
        void shouldNormalizeGatewayHostTrailingSlash() {
            FeiApiClient client = new FeiApiClient("ak", "sk", "http://localhost:8090/");

            // 通过反射验证内部字段
            String host = getFieldValue(client, "gatewayHost");
            assertEquals("http://localhost:8090", host);
        }

        @Test
        @DisplayName("三参构造函数，gatewayHost 多个末尾斜杠被去除")
        void shouldNormalizeMultipleTrailingSlashes() {
            FeiApiClient client = new FeiApiClient("ak", "sk", "http://localhost:8090///");

            String host = getFieldValue(client, "gatewayHost");
            assertEquals("http://localhost:8090", host);
        }

        @Test
        @DisplayName("三参构造函数，gatewayHost 为 null 时使用默认值")
        void shouldUseDefaultHostForNull() {
            FeiApiClient client = new FeiApiClient("ak", "sk", null);

            String host = getFieldValue(client, "gatewayHost");
            assertEquals("http://localhost:8090", host);
        }

        @Test
        @DisplayName("三参构造函数，gatewayHost 为空字符串时使用默认值")
        void shouldUseDefaultHostForEmpty() {
            FeiApiClient client = new FeiApiClient("ak", "sk", "   ");

            String host = getFieldValue(client, "gatewayHost");
            assertEquals("http://localhost:8090", host);
        }
    }

    @Nested
    @DisplayName("probeMode 探测模式")
    class ProbeModeTests {

        @Test
        @DisplayName("enableProbeMode 和 disableProbeMode 配对调用")
        void shouldEnableAndDisableProbeMode() {
            FeiApiClient client = new FeiApiClient("ak", "sk");

            client.enableProbeMode();
            client.disableProbeMode();
        }

        @Test
        @DisplayName("disableProbeMode 使用 remove() 清理，可重复调用")
        void shouldAllowMultipleDisable() {
            FeiApiClient client = new FeiApiClient("ak", "sk");

            client.disableProbeMode();
            client.disableProbeMode();
        }

        @Test
        @DisplayName("开启 probe 模式后，getHeaderMap 应包含探测 Header")
        void shouldContainProbeHeadersWhenProbeModeEnabled() throws Exception {
            FeiApiClient client = new FeiApiClient("ak", "sk", "http://localhost:8090", "my-probe-secret");
            client.enableProbeMode();

            try {
                Method getHeaderMap = FeiApiClient.class.getDeclaredMethod("getHeaderMap", String.class, String.class, String.class);
                getHeaderMap.setAccessible(true);

                @SuppressWarnings("unchecked")
                java.util.Map<String, String> headers = (java.util.Map<String, String>) getHeaderMap.invoke(client, "GET", "/api/test", null);

                assertEquals("true", headers.get("X-FeiAPI-Probe"), "应包含探测标记");
                assertNotNull(headers.get("X-FeiAPI-Probe-Nonce"), "应包含探测 nonce");
                assertNotNull(headers.get("X-FeiAPI-Probe-Timestamp"), "应包含探测时间戳");
                assertNotNull(headers.get("X-FeiAPI-Probe-Sign"), "应包含探测签名");

                // 普通签名字段也应存在
                assertNotNull(headers.get("accessKey"));
                assertNotNull(headers.get("nonce"));
                assertNotNull(headers.get("sign"));
                assertNotNull(headers.get("timestamp"));
            } finally {
                client.disableProbeMode();
            }
        }

        @Test
        @DisplayName("probeSecret 为 null 时，开启 probe 模式应抛出异常并包含明确消息")
        void shouldThrowWhenProbeSecretNull() throws Exception {
            FeiApiClient client = new FeiApiClient("ak", "sk");
            client.enableProbeMode();

            try {
                Method getHeaderMap = FeiApiClient.class.getDeclaredMethod("getHeaderMap", String.class, String.class, String.class);
                getHeaderMap.setAccessible(true);

                java.lang.reflect.InvocationTargetException ex = assertThrows(
                        java.lang.reflect.InvocationTargetException.class,
                        () -> getHeaderMap.invoke(client, "GET", "/api/test", null));

                assertNotNull(ex.getCause(), "应有 cause");
                assertTrue(ex.getCause() instanceof RuntimeException, "cause 应为 RuntimeException");
                assertTrue(ex.getCause().getMessage().contains("发布探测密钥不能为空"),
                        "消息应包含'发布探测密钥不能为空'，实际: " + ex.getCause().getMessage());
            } finally {
                client.disableProbeMode();
            }
        }

        @Test
        @DisplayName("probeSecret 为空字符串时，开启 probe 模式应抛出异常并包含明确消息")
        void shouldThrowWhenProbeSecretBlank() throws Exception {
            FeiApiClient client = new FeiApiClient("ak", "sk");
            client.setProbeSecret("   ");
            client.enableProbeMode();

            try {
                Method getHeaderMap = FeiApiClient.class.getDeclaredMethod("getHeaderMap", String.class, String.class, String.class);
                getHeaderMap.setAccessible(true);

                java.lang.reflect.InvocationTargetException ex = assertThrows(
                        java.lang.reflect.InvocationTargetException.class,
                        () -> getHeaderMap.invoke(client, "GET", "/api/test", null));

                assertNotNull(ex.getCause(), "应有 cause");
                assertTrue(ex.getCause() instanceof RuntimeException, "cause 应为 RuntimeException");
                assertTrue(ex.getCause().getMessage().contains("发布探测密钥不能为空"),
                        "消息应包含'发布探测密钥不能为空'，实际: " + ex.getCause().getMessage());
            } finally {
                client.disableProbeMode();
            }
        }
    }

    @Nested
    @DisplayName("getHeaderMap 方法（反射测试）")
    class GetHeaderMapTests {

        @Test
        @DisplayName("普通模式下不包含探测 Header")
        void shouldNotContainProbeHeadersInNormalMode() throws Exception {
            FeiApiClient client = new FeiApiClient("ak", "sk");

            Method getHeaderMap = FeiApiClient.class.getDeclaredMethod("getHeaderMap", String.class, String.class, String.class);
            getHeaderMap.setAccessible(true);

            @SuppressWarnings("unchecked")
            java.util.Map<String, String> headers = (java.util.Map<String, String>) getHeaderMap.invoke(client, "GET", "/api/test", null);

            assertNotNull(headers.get("accessKey"));
            assertNotNull(headers.get("nonce"));
            assertNotNull(headers.get("sign"));
            assertNotNull(headers.get("timestamp"));
            assertNull(headers.get("X-FeiAPI-Probe"));
        }
    }

    @Nested
    @DisplayName("@SdkInvoke 注解方法")
    class SdkInvokeAnnotationTests {

        @Test
        @DisplayName("getLoveWords 方法标记了 @SdkInvoke(needParams=false)")
        void getLoveWordsShouldBeAnnotated() throws NoSuchMethodException {
            Method method = FeiApiClient.class.getMethod("getLoveWords");
            com.feiting.feiapiclientsdk.annotation.SdkInvoke annotation =
                    method.getAnnotation(com.feiting.feiapiclientsdk.annotation.SdkInvoke.class);

            assertNotNull(annotation);
            assertFalse(annotation.needParams());
        }

        @Test
        @DisplayName("getUsernameByPost 方法标记了 @SdkInvoke(needParams=true)")
        void getUsernameByPostShouldBeAnnotated() throws NoSuchMethodException {
            Method method = FeiApiClient.class.getMethod("getUsernameByPost", String.class);
            com.feiting.feiapiclientsdk.annotation.SdkInvoke annotation =
                    method.getAnnotation(com.feiting.feiapiclientsdk.annotation.SdkInvoke.class);

            assertNotNull(annotation);
            assertTrue(annotation.needParams());
        }
    }

    @Nested
    @DisplayName("下游异常响应处理")
    class ErrorResponseTests {

        @Test
        @DisplayName("非 2xx 响应消息包含状态码和响应内容")
        void shouldBuildErrorMessageWithResponseBody() throws Exception {
            FeiApiClient client = new FeiApiClient();
            Method buildErrorMessage = FeiApiClient.class.getDeclaredMethod("buildErrorMessage", int.class, String.class);
            buildErrorMessage.setAccessible(true);

            String message = (String) buildErrorMessage.invoke(client, 400, "username 不能为空");

            assertEquals("调用接口失败，响应状态码：400，响应内容：username 不能为空", message);
        }

        @Test
        @DisplayName("过长响应内容会被截断")
        void shouldTruncateLongResponseBody() throws Exception {
            FeiApiClient client = new FeiApiClient();
            Method buildErrorMessage = FeiApiClient.class.getDeclaredMethod("buildErrorMessage", int.class, String.class);
            buildErrorMessage.setAccessible(true);

            String longBody = new String(new char[250]).replace('\0', 'x');
            String message = (String) buildErrorMessage.invoke(client, 500, longBody);

            assertTrue(message.endsWith("..."));
            assertFalse(message.contains(longBody));
        }
    }

    /**
     * 通过反射获取私有字段值
     */
    @SuppressWarnings("unchecked")
    private <T> T getFieldValue(Object obj, String fieldName) {
        try {
            // 尝试直接字段访问
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get field: " + fieldName, e);
        }
    }
}
