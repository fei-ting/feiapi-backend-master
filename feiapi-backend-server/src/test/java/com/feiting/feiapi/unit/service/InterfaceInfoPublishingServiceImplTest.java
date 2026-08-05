package com.feiting.feiapi.unit.service;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.model.publish.InterfacePublishContext;
import com.feiting.feiapi.service.InterfaceInfoLifecycleService;
import com.feiting.feiapi.service.InterfacePublishProbeService;
import com.feiting.feiapi.service.impl.InterfaceInfoPublishingServiceImpl;
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

    /** 发布探测执行服务。 */
    private InterfacePublishProbeService interfacePublishProbeService;

    /** 被测发布编排服务。 */
    private InterfaceInfoPublishingServiceImpl publishingService;

    /**
     * 初始化被测对象及依赖。
     */
    @BeforeEach
    void setUp() {
        interfaceInfoLifecycleService = mock(InterfaceInfoLifecycleService.class);
        interfacePublishProbeService = mock(InterfacePublishProbeService.class);
        publishingService = new InterfaceInfoPublishingServiceImpl(
                interfaceInfoLifecycleService,
                interfacePublishProbeService);
    }

    /**
     * 探测成功后应完成发布且不执行回滚。
     */
    @Test
    @DisplayName("探测成功完成发布")
    void shouldCompletePublishingWhenProbeSucceeds() {
        InterfacePublishContext publishContext = buildPublishingContext();
        when(interfaceInfoLifecycleService.startPublishingWithContext(INTERFACE_INFO_ID)).thenReturn(publishContext);

        boolean result = publishingService.publish(INTERFACE_INFO_ID);

        assertThat(result).isTrue();
        verify(interfacePublishProbeService).probe(publishContext);
        verify(interfaceInfoLifecycleService).completePublishing(INTERFACE_INFO_ID);
        verify(interfaceInfoLifecycleService, never()).rollbackPublishing(INTERFACE_INFO_ID);
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
        when(interfaceInfoLifecycleService.startPublishingWithContext(INTERFACE_INFO_ID)).thenThrow(startException);

        assertThatThrownBy(() -> publishingService.publish(INTERFACE_INFO_ID))
                .isSameAs(startException);

        verify(interfaceInfoLifecycleService, never()).rollbackPublishing(INTERFACE_INFO_ID);
        verify(interfaceInfoLifecycleService, never()).completePublishing(INTERFACE_INFO_ID);
        verifyNoInteractions(interfacePublishProbeService);
    }

    /**
     * 探测业务异常应原样抛出并回滚发布状态。
     */
    @Test
    @DisplayName("探测业务异常回滚发布状态")
    void shouldRollbackAndKeepBusinessExceptionWhenProbeFails() {
        InterfacePublishContext publishContext = buildPublishingContext();
        BusinessException probeException = new BusinessException(ErrorCode.SYSTEM_ERROR, "下游拒绝探测");
        when(interfaceInfoLifecycleService.startPublishingWithContext(INTERFACE_INFO_ID)).thenReturn(publishContext);
        org.mockito.Mockito.doThrow(probeException).when(interfacePublishProbeService).probe(publishContext);

        assertThatThrownBy(() -> publishingService.publish(INTERFACE_INFO_ID))
                .isSameAs(probeException);

        verify(interfaceInfoLifecycleService).rollbackPublishing(INTERFACE_INFO_ID);
        verify(interfaceInfoLifecycleService, never()).completePublishing(INTERFACE_INFO_ID);
    }

    /**
     * 探测返回空结果应按验证失败处理并回滚。
     */
    @Test
    @DisplayName("探测空结果回滚发布状态")
    void shouldRollbackWhenProbeReturnsNull() {
        InterfacePublishContext publishContext = buildPublishingContext();
        BusinessException probeException = new BusinessException(ErrorCode.OPERATION_ERROR, "SDK 未返回探测响应元数据");
        when(interfaceInfoLifecycleService.startPublishingWithContext(INTERFACE_INFO_ID)).thenReturn(publishContext);
        org.mockito.Mockito.doThrow(probeException).when(interfacePublishProbeService).probe(publishContext);

        assertThatThrownBy(() -> publishingService.publish(INTERFACE_INFO_ID))
                .isSameAs(probeException);

        verify(interfaceInfoLifecycleService).rollbackPublishing(INTERFACE_INFO_ID);
    }

    /**
     * 未知探测异常应转换为统一业务异常并回滚。
     */
    @Test
    @DisplayName("未知探测异常转换后回滚发布状态")
    void shouldWrapUnexpectedProbeExceptionAndRollback() {
        InterfacePublishContext publishContext = buildPublishingContext();
        when(interfaceInfoLifecycleService.startPublishingWithContext(INTERFACE_INFO_ID)).thenReturn(publishContext);
        org.mockito.Mockito.doThrow(new IllegalStateException("连接中断"))
                .when(interfacePublishProbeService).probe(publishContext);

        assertThatThrownBy(() -> publishingService.publish(INTERFACE_INFO_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("接口验证失败：连接中断");

        verify(interfaceInfoLifecycleService).rollbackPublishing(INTERFACE_INFO_ID);
    }

    /**
     * 构造发布中的接口快照。
     *
     * @return 接口快照
     */
    private InterfacePublishContext buildPublishingContext() {
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setId(INTERFACE_INFO_ID);
        interfaceInfo.setSdkMethodName("getLoveWords");
        InterfacePublishContext publishContext = new InterfacePublishContext();
        publishContext.setInterfaceInfo(interfaceInfo);
        return publishContext;
    }
}
