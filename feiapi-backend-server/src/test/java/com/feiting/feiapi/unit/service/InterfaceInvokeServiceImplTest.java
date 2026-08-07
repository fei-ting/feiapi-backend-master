package com.feiting.feiapi.unit.service;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.component.InterfaceRequestParamValidator;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.interfaceplatform.definition.component.SdkMethodRegistry;
import com.feiting.feiapi.interfaceplatform.invocation.model.vo.InterfaceInvokeResultVO;
import com.feiting.feiapi.interfaceplatform.invocation.service.impl.InterfaceInvokeServiceImpl;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.entity.User;
import com.feiting.feiapicommon.model.enums.InterfaceInfoStatusEnum;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 在线调试调用服务实现测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InterfaceInvokeServiceImpl 在线调试调用服务测试")
class InterfaceInvokeServiceImplTest {

    /**
     * 接口信息服务模拟对象。
     */
    @Mock
    private InterfaceInfoService interfaceInfoService;

    /**
     * 请求参数校验器模拟对象。
     */
    @Mock
    private InterfaceRequestParamValidator interfaceRequestParamValidator;

    /**
     * 创建已上线的接口信息。
     *
     * @param sdkMethodName SDK 方法名
     * @return 接口信息
     */
    private InterfaceInfo createOnlineInterface(String sdkMethodName) {
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setId(1L);
        interfaceInfo.setStatus(InterfaceInfoStatusEnum.ONLINE.getValue());
        interfaceInfo.setSdkMethodName(sdkMethodName);
        interfaceInfo.setRequestParams("{}");
        return interfaceInfo;
    }

    /**
     * 创建具备 APIKey 的登录用户。
     *
     * @return 登录用户
     */
    private User createLoginUser() {
        User user = new User();
        user.setId(2L);
        user.setAccessKey("ak");
        user.setSecretKey("sk");
        return user;
    }

    /**
     * 验证真实 SDK 成功响应会转换为完整在线调试结果。
     *
     * @throws Exception 本地测试服务启动失败时抛出
     */
    @Test
    @DisplayName("成功响应返回状态、媒体类型、正文和耗时")
    void shouldReturnSuccessfulHttpMetadata() throws Exception {
        HttpServer server = createResponseServer(200, "application/json; charset=UTF-8", "{\"ok\":true}");
        SdkMethodRegistry registry = new SdkMethodRegistry();
        registry.init();
        InterfaceInvokeServiceImpl service = new InterfaceInvokeServiceImpl(
                interfaceInfoService,
                interfaceRequestParamValidator,
                registry,
                "http://127.0.0.1:" + server.getAddress().getPort());
        when(interfaceInfoService.getById(1L)).thenReturn(createOnlineInterface("getLoveWords"));

        try {
            InterfaceInvokeResultVO result = service.invoke(1L, null, createLoginUser());

            assertThat(result.getSuccessful()).isTrue();
            assertThat(result.getStatusCode()).isEqualTo(200);
            assertThat(result.getContentType()).contains("application/json");
            assertThat(result.getBody()).isEqualTo("{\"ok\":true}");
            assertThat(result.getDurationMs()).isNotNegative();
            assertThat(result.getErrorMessage()).isNull();
        } finally {
            server.stop(0);
        }
    }

    /**
     * 验证真实 SDK 非 2xx 响应仍保留公开响应内容。
     *
     * @throws Exception 本地测试服务启动失败时抛出
     */
    @Test
    @DisplayName("非 2xx 响应保留真实状态和正文")
    void shouldReturnFailedHttpMetadata() throws Exception {
        HttpServer server = createResponseServer(422, "text/plain; charset=UTF-8", "username 不能为空");
        SdkMethodRegistry registry = new SdkMethodRegistry();
        registry.init();
        InterfaceInvokeServiceImpl service = new InterfaceInvokeServiceImpl(
                interfaceInfoService,
                interfaceRequestParamValidator,
                registry,
                "http://127.0.0.1:" + server.getAddress().getPort());
        when(interfaceInfoService.getById(1L)).thenReturn(createOnlineInterface("getLoveWords"));

        try {
            InterfaceInvokeResultVO result = service.invoke(1L, null, createLoginUser());

            assertThat(result.getSuccessful()).isFalse();
            assertThat(result.getStatusCode()).isEqualTo(422);
            assertThat(result.getContentType()).contains("text/plain");
            assertThat(result.getBody()).isEqualTo("username 不能为空");
            assertThat(result.getErrorMessage()).isNull();
        } finally {
            server.stop(0);
        }
    }

    /**
     * 验证接口不存在时保持既有业务错误语义。
     */
    @Test
    @DisplayName("接口不存在时抛出数据不存在异常")
    void shouldRejectMissingInterface() {
        SdkMethodRegistry registry = new SdkMethodRegistry();
        InterfaceInvokeServiceImpl service = new InterfaceInvokeServiceImpl(
                interfaceInfoService, interfaceRequestParamValidator, registry, "http://localhost:8090");
        when(interfaceInfoService.getById(1L)).thenReturn(null);

        assertThatThrownBy(() -> service.invoke(1L, "{}", createLoginUser()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.NOT_FOUND_ERROR.getCode()));
        verify(interfaceRequestParamValidator, never()).validate(any(), any());
    }

    /**
     * 验证未产生 HTTP 响应的 SDK 异常不会泄露内部消息。
     */
    @Test
    @DisplayName("SDK 异常返回固定安全文案")
    void shouldReturnSafeMessageForSdkFailure() {
        SdkMethodRegistry registry = org.mockito.Mockito.mock(SdkMethodRegistry.class);
        InterfaceInvokeServiceImpl service = new InterfaceInvokeServiceImpl(
                interfaceInfoService, interfaceRequestParamValidator, registry, "http://localhost:8090");
        when(interfaceInfoService.getById(1L)).thenReturn(createOnlineInterface("getLoveWords"));
        when(registry.invoke(any(), eq("getLoveWords"), eq("{}")))
                .thenThrow(new BusinessException(ErrorCode.SYSTEM_ERROR, "包含内部地址和堆栈"));

        InterfaceInvokeResultVO result = service.invoke(1L, "{}", createLoginUser());

        assertThat(result.getSuccessful()).isFalse();
        assertThat(result.getStatusCode()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo("接口调用失败，请稍后重试");
        assertThat(result.getErrorMessage()).doesNotContain("内部地址");
    }

    /**
     * 创建固定响应的本地 HTTP 服务。
     *
     * @param statusCode  响应状态码
     * @param contentType 响应内容类型
     * @param body        响应正文
     * @return 已启动的 HTTP 服务
     * @throws Exception 本地端口创建失败时抛出
     */
    private HttpServer createResponseServer(int statusCode, String contentType, String body) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/love_words", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        });
        server.start();
        return server;
    }
}
