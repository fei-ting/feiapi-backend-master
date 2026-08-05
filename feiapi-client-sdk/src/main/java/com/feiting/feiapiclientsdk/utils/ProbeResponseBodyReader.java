package com.feiting.feiapiclientsdk.utils;

import cn.hutool.http.HttpResponse;
import com.feiting.feiapiclientsdk.constant.ApiPayloadLimits;
import com.feiting.feiapiclientsdk.exception.ProbeResponseTooLargeException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 发布探测响应体受限读取工具。
 */
public class ProbeResponseBodyReader {

    /** 流式读取缓冲区大小。 */
    private static final int BUFFER_SIZE = 8192;

    /** 发布探测响应超限提示。 */
    public static final String RESPONSE_TOO_LARGE_MESSAGE = "发布探测响应体超过 1048576 字节";

    /**
     * 按响应内容类型读取并校验解压后的响应体。
     *
     * @param response Hutool 异步响应
     * @return 响应文本
     */
    public String read(HttpResponse response) {
        if (response == null) {
            return "";
        }
        try {
            return isTextResponse(response.header("Content-Type"))
                    ? readText(response.bodyStream(), resolveCharset(response))
                    : readBinary(response.bodyStream(), resolveCharset(response));
        } catch (IOException exception) {
            throw new IllegalStateException("读取发布探测响应失败", exception);
        }
    }

    /**
     * 读取文本响应，并按重新编码后的 UTF-8 字节数执行最终限制。
     *
     * @param inputStream 解压后的响应流
     * @param charset     响应字符集
     * @return 响应文本
     * @throws IOException 响应读取失败时抛出
     */
    private String readText(InputStream inputStream, Charset charset) throws IOException {
        StringBuilder bodyBuilder = new StringBuilder();
        char[] buffer = new char[BUFFER_SIZE];
        try (Reader reader = new InputStreamReader(inputStream, charset)) {
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                if (count == 0) {
                    continue;
                }
                bodyBuilder.append(buffer, 0, count);
                if (bodyBuilder.length() > ApiPayloadLimits.MAX_PROBE_RESPONSE_BODY_BYTES) {
                    throw new ProbeResponseTooLargeException(RESPONSE_TOO_LARGE_MESSAGE);
                }
            }
        }
        String body = bodyBuilder.toString();
        if (body.getBytes(StandardCharsets.UTF_8).length > ApiPayloadLimits.MAX_PROBE_RESPONSE_BODY_BYTES) {
            throw new ProbeResponseTooLargeException(RESPONSE_TOO_LARGE_MESSAGE);
        }
        return body;
    }

    /**
     * 读取二进制响应，并按解压后的原始字节数执行限制。
     *
     * @param inputStream 解压后的响应流
     * @param charset     响应字符集，用于保持 SDK 字符串返回契约
     * @return 按响应字符集转换后的文本
     * @throws IOException 响应读取失败时抛出
     */
    private String readBinary(InputStream inputStream, Charset charset) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream stream = inputStream) {
            int count;
            while ((count = stream.read(buffer)) >= 0) {
                if (count == 0) {
                    continue;
                }
                if (outputStream.size() + count > ApiPayloadLimits.MAX_PROBE_RESPONSE_BODY_BYTES) {
                    throw new ProbeResponseTooLargeException(RESPONSE_TOO_LARGE_MESSAGE);
                }
                outputStream.write(buffer, 0, count);
            }
        }
        return outputStream.toString(charset);
    }

    /**
     * 判断响应内容类型是否属于文本、JSON 或 XML。
     *
     * @param contentType 响应内容类型
     * @return 是否按文本响应处理
     */
    private boolean isTextResponse(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return mediaType.startsWith("text/")
                || "application/json".equals(mediaType)
                || "application/xml".equals(mediaType)
                || mediaType.endsWith("+json")
                || mediaType.endsWith("+xml");
    }

    /**
     * 解析响应字符集，缺失或非法时使用 UTF-8。
     *
     * @param response Hutool 响应
     * @return 响应字符集
     */
    private Charset resolveCharset(HttpResponse response) {
        String charsetName = response.charset();
        if (charsetName == null || charsetName.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(charsetName);
        } catch (IllegalArgumentException exception) {
            return StandardCharsets.UTF_8;
        }
    }
}
