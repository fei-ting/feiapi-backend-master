package com.feiting.feiapi.component;

import com.feiting.feiapi.exception.RequestBodyTooLargeException;
import com.feiting.feiapi.model.dto.interfaceDoc.InterfaceDocSaveRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

/**
 * 接口文档聚合保存请求正文大小限制处理器。
 * 
 * RequestBodyAdviceAdapter 是 Spring 提供的请求体处理增强接口
 * 加上 @ControllerAdvice 后，Spring 会自动将它应用到所有 Controller 的请求体处理流程中
 * 通过 supports() 方法决定是否对特定类型生效
 */
@ControllerAdvice
public class InterfaceDocRequestBodyAdvice extends RequestBodyAdviceAdapter {

    /** 聚合文档保存请求正文最大字节数。 */
    public static final int MAX_INTERFACE_DOC_REQUEST_BYTES = 1024 * 1024;

    /** 聚合文档保存请求正文超限提示。 */
    public static final String REQUEST_TOO_LARGE_MESSAGE = "接口文档保存请求体不能超过 1048576 字节";

    /**
     * 判断当前请求是否需要执行接口文档正文限制。
     *
     * @param methodParameter 方法参数
     * @param targetType      目标类型
     * @param converterType   消息转换器类型
     * @return 目标类型为接口文档聚合保存请求时返回 true
     */
    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return InterfaceDocSaveRequest.class.equals(targetType);
    }

    /**
     * 在 JSON 解析前检查声明长度并包装实际读取流。
     *
     * @param inputMessage  HTTP 输入消息
     * @param parameter     方法参数
     * @param targetType    目标类型
     * @param converterType 消息转换器类型
     * @return 带实际读取字节限制的 HTTP 输入消息
     */
    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter,
                                           Type targetType,
                                           Class<? extends HttpMessageConverter<?>> converterType) {
        long contentLength = inputMessage.getHeaders().getContentLength();
        if (contentLength > MAX_INTERFACE_DOC_REQUEST_BYTES) {
            throw new RequestBodyTooLargeException(REQUEST_TOO_LARGE_MESSAGE);
        }
        return new LimitedHttpInputMessage(inputMessage);
    }

    /**
     * 带正文限制的 HTTP 输入消息。
     */
    private static final class LimitedHttpInputMessage implements HttpInputMessage {

        /** 原始 HTTP 输入消息。 */
        private final HttpInputMessage delegate;

        /**
         * 创建带正文限制的 HTTP 输入消息。
         *
         * @param delegate 原始 HTTP 输入消息
         */
        private LimitedHttpInputMessage(HttpInputMessage delegate) {
            this.delegate = delegate;
        }

        /**
         * 获取带累计读取限制的正文流。
         *
         * @return 受限正文流
         * @throws IOException 读取原始正文失败时抛出
         */
        @Override
        public InputStream getBody() throws IOException {
            return new LimitedInputStream(delegate.getBody());
        }

        /**
         * 获取原始请求头。
         *
         * @return HTTP 请求头
         */
        @Override
        public org.springframework.http.HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }
    }

    /**
     * 按实际读取字节累计限制的输入流。
     */
    private static final class LimitedInputStream extends FilterInputStream {

        /** 已读取字节数。 */
        private long bytesRead;

        /**
         * 创建受限输入流。
         *
         * @param inputStream 原始输入流
         */
        private LimitedInputStream(InputStream inputStream) {
            super(inputStream);
        }

        /**
         * 读取单个字节并累计实际读取量。
         *
         * @return 读取到的字节或 -1
         * @throws IOException 原始流读取失败时抛出
         */
        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                assertWithinLimit(1);
            }
            return value;
        }

        /**
         * 批量读取字节并累计实际读取量。
         *
         * @param buffer 目标缓冲区
         * @param offset 写入偏移量
         * @param length 最大读取长度
         * @return 实际读取字节数或 -1
         * @throws IOException 原始流读取失败时抛出
         */
        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = super.read(buffer, offset, length);
            if (count > 0) {
                assertWithinLimit(count);
            }
            return count;
        }

        /**
         * 累计读取字节并在超过上限时立即失败。
         *
         * @param count 本次读取字节数
         */
        private void assertWithinLimit(int count) {
            bytesRead += count;
            if (bytesRead > MAX_INTERFACE_DOC_REQUEST_BYTES) {
                RequestBodyTooLargeException exception =
                        new RequestBodyTooLargeException(REQUEST_TOO_LARGE_MESSAGE);
                try {
                    close();
                } catch (IOException closeException) {
                    // 关闭失败不能覆盖请求体超限契约，保留为附加异常供诊断。
                    exception.addSuppressed(closeException);
                }
                throw exception;
            }
        }
    }
}
