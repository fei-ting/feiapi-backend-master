package com.feiting.feiapiclientsdk.utils;

import cn.hutool.http.HttpResponse;
import com.feiting.feiapiclientsdk.constant.ApiPayloadLimits;
import com.feiting.feiapiclientsdk.exception.ProbeResponseTooLargeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 发布探测响应体受限读取工具测试。
 */
@DisplayName("ProbeResponseBodyReader 测试")
class ProbeResponseBodyReaderTest {

    /** 被测响应体读取工具。 */
    private final ProbeResponseBodyReader reader = new ProbeResponseBodyReader();

    /**
     * 文本响应恰好 1 MiB UTF-8 字节时允许读取。
     */
    @Test
    @DisplayName("文本响应恰好 1 MiB 时允许读取")
    void shouldAllowTextResponseAtExactUtf8Limit() {
        String body = "中".repeat(349525) + "a";
        HttpResponse response = mockResponse("application/json; charset=UTF-8",
                body.getBytes(StandardCharsets.UTF_8), "UTF-8");

        assertEquals(body, reader.read(response));
    }

    /**
     * 文本响应超过 1 MiB UTF-8 字节时拒绝读取。
     */
    @Test
    @DisplayName("文本响应超过 1 MiB 时拒绝")
    void shouldRejectTextResponseExceedingUtf8Limit() {
        String body = "中".repeat(349525) + "aa";
        HttpResponse response = mockResponse("application/json",
                body.getBytes(StandardCharsets.UTF_8), "UTF-8");

        ProbeResponseTooLargeException exception = assertThrows(
                ProbeResponseTooLargeException.class, () -> reader.read(response));

        assertEquals(ProbeResponseBodyReader.RESPONSE_TOO_LARGE_MESSAGE, exception.getMessage());
    }

    /**
     * 二进制响应恰好 1 MiB 时允许读取。
     */
    @Test
    @DisplayName("二进制响应恰好 1 MiB 时允许读取")
    void shouldAllowBinaryResponseAtExactLimit() {
        byte[] body = new byte[ApiPayloadLimits.MAX_PROBE_RESPONSE_BODY_BYTES];
        HttpResponse response = mockResponse("application/octet-stream", body, "ISO-8859-1");

        assertEquals(body.length, reader.read(response).length());
    }

    /**
     * 二进制响应超过 1 MiB 时拒绝读取。
     */
    @Test
    @DisplayName("二进制响应超过 1 MiB 时拒绝")
    void shouldRejectBinaryResponseExceedingLimit() {
        byte[] body = new byte[ApiPayloadLimits.MAX_PROBE_RESPONSE_BODY_BYTES + 1];
        HttpResponse response = mockResponse("application/octet-stream", body, "ISO-8859-1");

        ProbeResponseTooLargeException exception = assertThrows(
                ProbeResponseTooLargeException.class, () -> reader.read(response));

        assertEquals(ProbeResponseBodyReader.RESPONSE_TOO_LARGE_MESSAGE, exception.getMessage());
    }

    /**
     * 构建指定内容类型、正文和字符集的 Hutool 响应。
     *
     * @param contentType 响应内容类型
     * @param body        解压后的响应正文
     * @param charset     响应字符集
     * @return 模拟响应
     */
    private HttpResponse mockResponse(String contentType, byte[] body, String charset) {
        HttpResponse response = mock(HttpResponse.class);
        when(response.header("Content-Type")).thenReturn(contentType);
        when(response.charset()).thenReturn(charset);
        when(response.bodyStream()).thenReturn(new ByteArrayInputStream(body));
        return response;
    }
}
