package com.feiting.feiapiclientsdk.client;

import com.sun.net.httpserver.HttpServer;
import com.feiting.feiapiclientsdk.model.OnlineDebugInvocationResult;
import com.feiting.feiapiclientsdk.model.ProbeInvocationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

        /**
         * 使用真实分块 HTTP 响应验证 Hutool 异步响应流在响应关闭前能够完整读取。
         *
         * @throws Exception 本地测试服务启动或请求执行失败时抛出
         */
        @Test
        @DisplayName("探测模式完整读取 Hutool 异步网络响应流")
        void shouldReadRealAsyncResponseStreamBeforeResponseCloses() throws Exception {
            byte[] firstChunk = "async-".getBytes(StandardCharsets.UTF_8);
            byte[] secondChunk = "probe-body".getBytes(StandardCharsets.UTF_8);
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/love_words", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                exchange.sendResponseHeaders(200, 0);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(firstChunk);
                    outputStream.flush();
                    outputStream.write(secondChunk);
                }
            });
            server.start();
            FeiApiClient client = new FeiApiClient("ak", "sk",
                    "http://127.0.0.1:" + server.getAddress().getPort(), "probe-secret");
            client.enableProbeMode();

            try {
                assertThat(client.getLoveWords()).isEqualTo("async-probe-body");
            } finally {
                client.disableProbeMode();
                server.stop(0);
            }
        }

        /**
         * 探测模式下即使下游返回非 2xx，也应保留探测元数据并正常返回响应体，
         * 交由后端统一响应校验器分类。
         *
         * @throws Exception 本地测试服务启动或请求执行失败时抛出
         */
        @Test
        @DisplayName("探测模式下非 2xx 仍保留探测元数据")
        void shouldKeepProbeMetadataWhenProbeResponseIsNon2xx() throws Exception {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/love_words", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                exchange.getResponseHeaders().set("X-FeiAPI-Probe-Failure-Stage", "GATEWAY_AUTH");
                byte[] responseBytes = "probe-denied".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(403, responseBytes.length);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(responseBytes);
                }
            });
            server.start();
            FeiApiClient client = new FeiApiClient("ak", "sk",
                    "http://127.0.0.1:" + server.getAddress().getPort(), "probe-secret");
            client.enableProbeMode();

            try {
                assertThat(client.getLoveWords()).isEqualTo("probe-denied");

                ProbeInvocationResult result = client.getProbeInvocationResult();
                assertThat(result).isNotNull();
                assertThat(result.getStatusCode()).isEqualTo(403);
                assertThat(result.getContentType()).contains("text/plain");
                assertThat(result.getGatewayFailureStage()).isEqualTo("GATEWAY_AUTH");
                assertThat(result.getBody()).isEqualTo("probe-denied");
            } finally {
                client.disableProbeMode();
                server.stop(0);
            }
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
    @DisplayName("onlineDebugMode 在线调试模式")
    class OnlineDebugModeTests {

        /**
         * 验证在线调试模式会捕获成功响应元数据。
         *
         * @throws Exception 本地测试服务启动失败时抛出
         */
        @Test
        @DisplayName("成功响应应捕获状态、媒体类型、正文和耗时")
        void shouldCaptureSuccessfulResponseMetadata() throws Exception {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/api/love_words", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                byte[] responseBytes = "{\"message\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, responseBytes.length);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(responseBytes);
                }
            });
            server.start();
            FeiApiClient client = new FeiApiClient("ak", "sk",
                    "http://127.0.0.1:" + server.getAddress().getPort());
            client.enableOnlineDebugMode();

            try {
                assertThat(client.getLoveWords()).isEqualTo("{\"message\":\"ok\"}");

                OnlineDebugInvocationResult result = client.getOnlineDebugInvocationResult();
                assertThat(result).isNotNull();
                assertThat(result.getStatusCode()).isEqualTo(200);
                assertThat(result.getContentType()).contains("application/json");
                assertThat(result.getBody()).isEqualTo("{\"message\":\"ok\"}");
                assertThat(result.getDurationMs()).isNotNegative();
            } finally {
                client.disableOnlineDebugMode();
                server.stop(0);
            }

            assertThat(client.getOnlineDebugInvocationResult()).isNull();
        }

        /**
         * 验证在线调试模式保留非成功响应，而普通调用仍抛出异常。
         *
         * @throws Exception 本地测试服务启动失败时抛出
         */
        @Test
        @DisplayName("非 2xx 响应应被捕获且不改变普通调用异常语义")
        void shouldCaptureFailedResponseWithoutChangingNormalMode() throws Exception {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/api/love_words", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                byte[] responseBytes = "参数错误".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(422, responseBytes.length);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(responseBytes);
                }
            });
            server.start();
            FeiApiClient client = new FeiApiClient("ak", "sk",
                    "http://127.0.0.1:" + server.getAddress().getPort());

            try {
                client.enableOnlineDebugMode();
                assertThat(client.getLoveWords()).isEqualTo("参数错误");
                assertThat(client.getOnlineDebugInvocationResult().getStatusCode()).isEqualTo(422);
                assertThat(client.getOnlineDebugInvocationResult().getBody()).isEqualTo("参数错误");
                client.disableOnlineDebugMode();

                assertThatThrownBy(client::getLoveWords)
                        .isInstanceOf(RuntimeException.class)
                        .hasMessageContaining("响应状态码：422")
                        .hasMessageContaining("参数错误");
            } finally {
                client.disableOnlineDebugMode();
                server.stop(0);
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

        /**
         * 请求体恰好达到 65,535 字节时允许生成签名 Header。
         */
        @Test
        @DisplayName("请求体恰好 65535 字节允许生成签名")
        void shouldAllowRequestBodyAtExactLimit() throws Exception {
            FeiApiClient client = new FeiApiClient("ak", "sk");
            Method getHeaderMap = FeiApiClient.class.getDeclaredMethod(
                    "getHeaderMap", String.class, String.class, String.class);
            getHeaderMap.setAccessible(true);

            assertDoesNotThrow(() -> getHeaderMap.invoke(client, "POST", "/api/test", "a".repeat(65535)));
        }

        /**
         * 请求体超过 65,535 字节时应在签名前失败。
         */
        @Test
        @DisplayName("请求体超过 65535 字节时拒绝生成签名")
        void shouldRejectRequestBodyExceedingLimitBeforeSigning() throws Exception {
            FeiApiClient client = new FeiApiClient("ak", "sk");
            Method getHeaderMap = FeiApiClient.class.getDeclaredMethod(
                    "getHeaderMap", String.class, String.class, String.class);
            getHeaderMap.setAccessible(true);

            InvocationTargetException exception = assertThrows(InvocationTargetException.class,
                    () -> getHeaderMap.invoke(client, "POST", "/api/test", "a".repeat(65536)));

            assertInstanceOf(IllegalArgumentException.class, exception.getCause());
            assertEquals("请求体不能超过 65535 字节", exception.getCause().getMessage());
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

        @Test
        @DisplayName("generateQrCode 方法标记了 @SdkInvoke(needParams=true)")
        void generateQrCodeShouldBeAnnotated() throws NoSuchMethodException {
            Method method = FeiApiClient.class.getMethod("generateQrCode", String.class);
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
