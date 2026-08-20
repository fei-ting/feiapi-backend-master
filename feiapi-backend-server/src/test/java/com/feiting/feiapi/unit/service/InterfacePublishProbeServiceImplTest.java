package com.feiting.feiapi.unit.service;

import com.feiting.feiapi.interfaceplatform.publishing.component.InterfaceProbeResponseValidator;
import com.feiting.feiapi.interfaceplatform.publishing.component.InterfaceProbeClientFactory;
import com.feiting.feiapi.interfaceplatform.definition.component.SdkMethodRegistry;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.interfaceplatform.publishing.exception.InterfacePublishProbeException;
import com.feiting.feiapi.interfaceplatform.publishing.model.enums.PublishProbeFailureStageEnum;
import com.feiting.feiapi.interfaceplatform.publishing.service.impl.InterfacePublishProbeServiceImpl;
import com.feiting.feiapiclientsdk.exception.ProbeResponseTooLargeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 接口发布探测服务单元测试。
 */
@DisplayName("接口发布探测服务单元测试")
class InterfacePublishProbeServiceImplTest {

    /**
     * 未知 SDK 异常不得向调用方公开底层连接信息。
     */
    @Test
    @DisplayName("未知 SDK 异常使用固定安全消息")
    void shouldHideUnknownSdkFailureDetails() throws Exception {
        BusinessException cause = new BusinessException(
                com.feiting.feiapi.common.ErrorCode.SYSTEM_ERROR,
                "SDK 方法调用失败",
                new IllegalStateException("连接 http://internal-gateway:8090 失败"));

        InterfacePublishProbeException result = classify(cause);

        assertThat(result.getStage()).isEqualTo(PublishProbeFailureStageEnum.SDK_INVOCATION);
        assertThat(result.getReason()).isEqualTo("SDK 调用失败");
        assertThat(result.getMessage()).doesNotContain("internal-gateway");
    }

    /**
     * 保留的异常链仍应支持探测阶段分类。
     */
    @Test
    @DisplayName("异常链中的 SocketTimeoutException 分类为响应超时")
    void shouldClassifySocketTimeoutFromCauseChain() throws Exception {
        BusinessException cause = new BusinessException(
                com.feiting.feiapi.common.ErrorCode.SYSTEM_ERROR,
                "SDK 方法调用失败",
                new SocketTimeoutException("Read timed out from http://internal-gateway:8090"));

        InterfacePublishProbeException result = classify(cause);

        assertThat(result.getStage()).isEqualTo(PublishProbeFailureStageEnum.RESPONSE_TIMEOUT);
        assertThat(result.getReason()).isEqualTo("等待或读取响应超时");
        assertThat(result.getMessage()).doesNotContain("internal-gateway");
    }

    /**
     * 多层包装不能导致响应体超限异常丢失原有分类。
     */
    @Test
    @DisplayName("异常链中的响应体超限分类为响应格式失败")
    void shouldClassifyWrappedOversizedResponse() throws Exception {
        BusinessException cause = new BusinessException(
                com.feiting.feiapi.common.ErrorCode.SYSTEM_ERROR,
                "SDK 方法调用失败",
                new IllegalStateException("反射调用失败",
                        new ProbeResponseTooLargeException("包含内部响应细节")));

        InterfacePublishProbeException result = classify(cause);

        assertThat(result.getStage()).isEqualTo(PublishProbeFailureStageEnum.RESPONSE_FORMAT);
        assertThat(result.getReason()).isEqualTo("响应体超过 1 MiB");
        assertThat(result.getMessage()).doesNotContain("内部响应细节");
    }

    /**
     * 连接阶段套接字超时应与读取响应超时区分。
     */
    @Test
    @DisplayName("连接阶段 SocketTimeoutException 分类为连接超时")
    void shouldClassifyConnectSocketTimeoutFromCauseChain() throws Exception {
        BusinessException cause = new BusinessException(
                com.feiting.feiapi.common.ErrorCode.SYSTEM_ERROR,
                "SDK 方法调用失败",
                new SocketTimeoutException("connect timed out: internal-gateway"));

        InterfacePublishProbeException result = classify(cause);

        assertThat(result.getStage()).isEqualTo(PublishProbeFailureStageEnum.CONNECTION_TIMEOUT);
        assertThat(result.getReason()).isEqualTo("连接网关或下游服务超时");
        assertThat(result.getMessage()).doesNotContain("internal-gateway");
    }

    /**
     * 调用异常分类私有方法。
     *
     * @param cause 原始异常
     * @return 分类后的发布探测异常
     */
    private InterfacePublishProbeException classify(Throwable cause) throws Exception {
        InterfacePublishProbeServiceImpl service = new InterfacePublishProbeServiceImpl(
                mock(SdkMethodRegistry.class),
                mock(InterfaceProbeClientFactory.class),
                mock(InterfaceProbeResponseValidator.class));
        Method method = InterfacePublishProbeServiceImpl.class.getDeclaredMethod(
                "classifyExecutionException", Throwable.class);
        method.setAccessible(true);
        return (InterfacePublishProbeException) method.invoke(service, cause);
    }
}
