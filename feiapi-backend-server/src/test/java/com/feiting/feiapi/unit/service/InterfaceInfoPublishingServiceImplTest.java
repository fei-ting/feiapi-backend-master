package com.feiting.feiapi.unit.service;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.component.SdkMethodRegistry;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.service.InterfaceInfoLifecycleService;
import com.feiting.feiapi.service.impl.InterfaceInfoPublishingServiceImpl;
import com.feiting.feiapiclientsdk.client.FeiApiClient;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 接口发布编排服务单元测试。
 */
@DisplayName("接口发布编排服务单元测试")
class InterfaceInfoPublishingServiceImplTest {

    /** 接口信息 ID。 */
    private static final long INTERFACE_INFO_ID = 1L;

    /** 接口生命周期服务。 */
    private InterfaceInfoLifecycleService interfaceInfoLifecycleService;

    /** 平台 SDK 客户端。 */
    private FeiApiClient feiApiClient;

    /** SDK 方法注册器。 */
    private SdkMethodRegistry sdkMethodRegistry;

    /** 被测发布编排服务。 */
    private InterfaceInfoPublishingServiceImpl publishingService;

    /**
     * 初始化被测对象及依赖。
     */
    @BeforeEach
    void setUp() {
        interfaceInfoLifecycleService = mock(InterfaceInfoLifecycleService.class);
        feiApiClient = mock(FeiApiClient.class);
        sdkMethodRegistry = mock(SdkMethodRegistry.class);
        publishingService = new InterfaceInfoPublishingServiceImpl(
                interfaceInfoLifecycleService,
                feiApiClient,
                sdkMethodRegistry);
    }

    /**
     * 探测成功后应完成发布且不执行回滚。
     */
    @Test
    @DisplayName("探测成功完成发布")
    void shouldCompletePublishingWhenProbeSucceeds() {
        InterfaceInfo interfaceInfo = buildPublishingInterface();
        when(interfaceInfoLifecycleService.startPublishing(INTERFACE_INFO_ID)).thenReturn(interfaceInfo);
        when(sdkMethodRegistry.invoke(feiApiClient, "getLoveWords", null)).thenReturn("调用成功");

        boolean result = publishingService.publish(INTERFACE_INFO_ID);

        assertThat(result).isTrue();
        verify(interfaceInfoLifecycleService).completePublishing(INTERFACE_INFO_ID);
        verify(interfaceInfoLifecycleService, never()).rollbackPublishing(INTERFACE_INFO_ID);
        verify(feiApiClient).enableProbeMode();
        verify(feiApiClient).disableProbeMode();
    }

    /**
     * 发布开始失败时不得回滚可能属于其他请求的发布中状态。
     */
    @Test
    @DisplayName("发布开始失败不执行回滚")
    void shouldNotRollbackWhenPublishingStartFails() {
        BusinessException startException = new BusinessException(
                ErrorCode.OPERATION_ERROR,
                "接口正在发布验证中，请稍后重试");
        when(interfaceInfoLifecycleService.startPublishing(INTERFACE_INFO_ID)).thenThrow(startException);

        assertThatThrownBy(() -> publishingService.publish(INTERFACE_INFO_ID))
                .isSameAs(startException);

        verify(interfaceInfoLifecycleService, never()).rollbackPublishing(INTERFACE_INFO_ID);
        verify(interfaceInfoLifecycleService, never()).completePublishing(INTERFACE_INFO_ID);
        verifyNoInteractions(feiApiClient, sdkMethodRegistry);
    }

    /**
     * 探测业务异常应原样抛出并回滚发布状态。
     */
    @Test
    @DisplayName("探测业务异常回滚发布状态")
    void shouldRollbackAndKeepBusinessExceptionWhenProbeFails() {
        InterfaceInfo interfaceInfo = buildPublishingInterface();
        BusinessException probeException = new BusinessException(ErrorCode.SYSTEM_ERROR, "下游拒绝探测");
        when(interfaceInfoLifecycleService.startPublishing(INTERFACE_INFO_ID)).thenReturn(interfaceInfo);
        when(sdkMethodRegistry.invoke(feiApiClient, "getLoveWords", null)).thenThrow(probeException);

        assertThatThrownBy(() -> publishingService.publish(INTERFACE_INFO_ID))
                .isSameAs(probeException);

        verify(interfaceInfoLifecycleService).rollbackPublishing(INTERFACE_INFO_ID);
        verify(interfaceInfoLifecycleService, never()).completePublishing(INTERFACE_INFO_ID);
        verify(feiApiClient).disableProbeMode();
    }

    /**
     * 探测返回空结果应按验证失败处理并回滚。
     */
    @Test
    @DisplayName("探测空结果回滚发布状态")
    void shouldRollbackWhenProbeReturnsNull() {
        InterfaceInfo interfaceInfo = buildPublishingInterface();
        when(interfaceInfoLifecycleService.startPublishing(INTERFACE_INFO_ID)).thenReturn(interfaceInfo);
        when(sdkMethodRegistry.invoke(feiApiClient, "getLoveWords", null)).thenReturn(null);

        assertThatThrownBy(() -> publishingService.publish(INTERFACE_INFO_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("接口验证失败");

        verify(interfaceInfoLifecycleService).rollbackPublishing(INTERFACE_INFO_ID);
        verify(feiApiClient).disableProbeMode();
    }

    /**
     * 未知探测异常应转换为统一业务异常并回滚。
     */
    @Test
    @DisplayName("未知探测异常转换后回滚发布状态")
    void shouldWrapUnexpectedProbeExceptionAndRollback() {
        InterfaceInfo interfaceInfo = buildPublishingInterface();
        when(interfaceInfoLifecycleService.startPublishing(INTERFACE_INFO_ID)).thenReturn(interfaceInfo);
        when(sdkMethodRegistry.invoke(feiApiClient, "getLoveWords", null))
                .thenThrow(new IllegalStateException("连接中断"));

        assertThatThrownBy(() -> publishingService.publish(INTERFACE_INFO_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("接口验证失败：连接中断");

        verify(interfaceInfoLifecycleService).rollbackPublishing(INTERFACE_INFO_ID);
        verify(feiApiClient).disableProbeMode();
    }

    /**
     * 构造发布中的接口快照。
     *
     * @return 接口快照
     */
    private InterfaceInfo buildPublishingInterface() {
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setId(INTERFACE_INFO_ID);
        interfaceInfo.setSdkMethodName("getLoveWords");
        return interfaceInfo;
    }
}
