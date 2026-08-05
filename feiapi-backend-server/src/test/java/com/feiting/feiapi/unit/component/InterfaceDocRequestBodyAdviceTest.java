package com.feiting.feiapi.unit.component;

import com.feiting.feiapi.interfaceplatform.documentation.component.InterfaceDocRequestBodyAdvice;
import com.feiting.feiapi.exception.RequestBodyTooLargeException;
import com.feiting.feiapi.interfaceplatform.documentation.model.dto.interfaceDoc.InterfaceDocSaveRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.mock.http.MockHttpInputMessage;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 接口文档聚合保存请求正文限制处理器测试。
 */
@DisplayName("InterfaceDocRequestBodyAdvice 测试")
class InterfaceDocRequestBodyAdviceTest {

    /** 被测请求正文限制处理器。 */
    private final InterfaceDocRequestBodyAdvice advice = new InterfaceDocRequestBodyAdvice();

    /**
     * 校验恰好 1 MiB 的实际正文允许完整读取。
     *
     * @throws IOException 读取测试正文失败时抛出
     */
    @Test
    @DisplayName("恰好 1 MiB 的正文允许读取")
    void shouldAllowBodyAtExactLimit() throws IOException {
        byte[] body = new byte[InterfaceDocRequestBodyAdvice.MAX_INTERFACE_DOC_REQUEST_BYTES];
        MockHttpInputMessage inputMessage = new MockHttpInputMessage(body);

        HttpInputMessage limitedMessage = advice.beforeBodyRead(
                inputMessage, null, InterfaceDocSaveRequest.class, null);

        assertThat(limitedMessage.getBody().readAllBytes()).hasSize(body.length);
    }

    /**
     * 校验未知声明长度的实际正文超过 1 MiB 时停止读取。
     */
    @Test
    @DisplayName("无 Content-Length 的超限正文按实际读取拒绝")
    void shouldRejectActualBodyExceedingLimit() {
        byte[] body = new byte[InterfaceDocRequestBodyAdvice.MAX_INTERFACE_DOC_REQUEST_BYTES + 1];
        CloseTrackingInputStream inputStream = new CloseTrackingInputStream(body, false);
        MockHttpInputMessage inputMessage = new MockHttpInputMessage(inputStream);

        assertThatThrownBy(() -> advice.beforeBodyRead(
                inputMessage, null, InterfaceDocSaveRequest.class, null).getBody().readAllBytes())
                .isInstanceOf(RequestBodyTooLargeException.class)
                .hasMessage(InterfaceDocRequestBodyAdvice.REQUEST_TOO_LARGE_MESSAGE);
        assertThat(inputStream.isClosed()).isTrue();
    }

    /**
     * 校验关闭底层流失败时仍保留请求体超限异常，并附加关闭异常用于诊断。
     */
    @Test
    @DisplayName("超限流关闭失败时不覆盖 413 业务异常")
    void shouldPreservePayloadExceptionWhenClosingStreamFails() {
        byte[] body = new byte[InterfaceDocRequestBodyAdvice.MAX_INTERFACE_DOC_REQUEST_BYTES + 1];
        CloseTrackingInputStream inputStream = new CloseTrackingInputStream(body, true);
        MockHttpInputMessage inputMessage = new MockHttpInputMessage(inputStream);

        Throwable throwable = catchThrowable(() -> advice.beforeBodyRead(
                inputMessage, null, InterfaceDocSaveRequest.class, null).getBody().readAllBytes());

        assertThat(throwable)
                .isInstanceOf(RequestBodyTooLargeException.class)
                .hasMessage(InterfaceDocRequestBodyAdvice.REQUEST_TOO_LARGE_MESSAGE);
        assertThat(throwable.getSuppressed())
                .singleElement()
                .satisfies(suppressed -> assertThat(suppressed)
                        .isInstanceOf(IOException.class)
                        .hasMessage("模拟关闭失败"));
        assertThat(inputStream.isClosed()).isTrue();
    }

    /**
     * 校验声明长度超过 1 MiB 时在读取前失败。
     */
    @Test
    @DisplayName("Content-Length 超限时立即拒绝")
    void shouldRejectDeclaredContentLengthExceedingLimit() {
        MockHttpInputMessage inputMessage = new MockHttpInputMessage(new byte[0]);
        inputMessage.getHeaders().setContentLength(
                InterfaceDocRequestBodyAdvice.MAX_INTERFACE_DOC_REQUEST_BYTES + 1L);

        assertThatThrownBy(() -> advice.beforeBodyRead(
                inputMessage, null, InterfaceDocSaveRequest.class, null))
                .isInstanceOf(RequestBodyTooLargeException.class)
                .hasMessage(InterfaceDocRequestBodyAdvice.REQUEST_TOO_LARGE_MESSAGE);
    }

    /**
     * 可记录关闭状态并模拟关闭失败的请求正文流。
     */
    private static final class CloseTrackingInputStream extends ByteArrayInputStream {

        /** 是否在关闭时抛出异常。 */
        private final boolean failOnClose;

        /** 是否已调用关闭方法。 */
        private boolean closed;

        /**
         * 创建可追踪关闭状态的正文流。
         *
         * @param body        正文字节
         * @param failOnClose 是否模拟关闭失败
         */
        private CloseTrackingInputStream(byte[] body, boolean failOnClose) {
            super(body);
            this.failOnClose = failOnClose;
        }

        /**
         * 记录关闭状态，并按测试场景决定是否抛出异常。
         *
         * @throws IOException 模拟关闭失败时抛出
         */
        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
            if (failOnClose) {
                throw new IOException("模拟关闭失败");
            }
        }

        /**
         * 获取当前流是否已关闭。
         *
         * @return 已调用关闭方法时返回 true
         */
        private boolean isClosed() {
            return closed;
        }
    }
}
