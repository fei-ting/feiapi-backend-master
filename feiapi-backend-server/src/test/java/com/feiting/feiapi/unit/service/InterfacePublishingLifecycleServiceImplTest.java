package com.feiting.feiapi.unit.service;

import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.interfaceplatform.lifecycle.model.snapshot.LockedInterfaceSnapshot;
import com.feiting.feiapi.interfaceplatform.lifecycle.service.api.InterfaceStateManager;
import com.feiting.feiapi.interfaceplatform.publishing.model.context.InterfacePublishContext;
import com.feiting.feiapi.interfaceplatform.publishing.service.api.InterfacePublishCheckService;
import com.feiting.feiapi.interfaceplatform.publishing.service.impl.InterfacePublishingLifecycleServiceImpl;
import com.feiting.feiapi.mapper.InterfaceInfoMapper;
import com.feiting.feiapi.model.enums.InterfaceChangeTypeEnum;
import com.feiting.feiapi.service.InterfaceChangeAuditService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.enums.InterfaceInfoStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 接口发布生命周期协作服务单元测试。
 */
@DisplayName("接口发布生命周期协作服务单元测试")
class InterfacePublishingLifecycleServiceImplTest {

    /** 接口信息 ID。 */
    private static final long INTERFACE_INFO_ID = 1L;

    /** 接口状态管理服务。 */
    private InterfaceStateManager stateManager;

    /** 发布前静态检查服务。 */
    private InterfacePublishCheckService publishCheckService;

    /** 接口主记录 Mapper。 */
    private InterfaceInfoMapper interfaceInfoMapper;

    /** 接口变更审计服务。 */
    private InterfaceChangeAuditService interfaceChangeAuditService;

    /** 被测发布生命周期协作服务。 */
    private InterfacePublishingLifecycleServiceImpl publishingLifecycleService;

    /**
     * 初始化被测对象及依赖。
     */
    @BeforeEach
    void setUp() {
        stateManager = mock(InterfaceStateManager.class);
        publishCheckService = mock(InterfacePublishCheckService.class);
        interfaceInfoMapper = mock(InterfaceInfoMapper.class);
        interfaceChangeAuditService = mock(InterfaceChangeAuditService.class);
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setId(INTERFACE_INFO_ID);
        interfaceInfo.setName("测试接口");
        when(interfaceInfoMapper.selectById(INTERFACE_INFO_ID)).thenReturn(interfaceInfo);
        publishingLifecycleService = new InterfacePublishingLifecycleServiceImpl(
                stateManager, publishCheckService, interfaceInfoMapper, interfaceChangeAuditService);
    }

    /**
     * 发布开始成功时应先执行发布静态检查，再切换发布中状态。
     */
    @Test
    @DisplayName("发布开始先检查再标记发布中")
    void shouldBuildPublishContextBeforeMarkingPublishing() {
        LockedInterfaceSnapshot offlineSnapshot = buildLockedSnapshot(InterfaceInfoStatusEnum.OFFLINE.getValue());
        InterfacePublishContext publishContext = buildPublishContext();
        when(stateManager.lockForUpdate(INTERFACE_INFO_ID)).thenReturn(offlineSnapshot);
        when(stateManager.recoverExpiredPublishingStatus(offlineSnapshot)).thenReturn(offlineSnapshot);
        when(publishCheckService.buildContextForPublish(INTERFACE_INFO_ID)).thenReturn(publishContext);

        InterfacePublishContext result = publishingLifecycleService.startPublishingWithContext(INTERFACE_INFO_ID);

        assertThat(result).isSameAs(publishContext);
        assertThat(result.getInterfaceInfo().getStatus()).isEqualTo(InterfaceInfoStatusEnum.PUBLISHING.getValue());
        InOrder inOrder = inOrder(stateManager, publishCheckService);
        inOrder.verify(stateManager).lockForUpdate(INTERFACE_INFO_ID);
        inOrder.verify(stateManager).recoverExpiredPublishingStatus(offlineSnapshot);
        inOrder.verify(publishCheckService).buildContextForPublish(INTERFACE_INFO_ID);
        inOrder.verify(stateManager).markPublishing(INTERFACE_INFO_ID);
    }

    /**
     * 发布中接口再次发布时应保持原错误消息且不执行检查和状态更新。
     */
    @Test
    @DisplayName("发布中接口再次发布失败")
    void shouldRejectPublishingInterfaceBeforeBuildContext() {
        LockedInterfaceSnapshot publishingSnapshot = buildLockedSnapshot(InterfaceInfoStatusEnum.PUBLISHING.getValue());
        when(stateManager.lockForUpdate(INTERFACE_INFO_ID)).thenReturn(publishingSnapshot);
        when(stateManager.recoverExpiredPublishingStatus(publishingSnapshot)).thenReturn(publishingSnapshot);

        assertThatThrownBy(() -> publishingLifecycleService.startPublishingWithContext(INTERFACE_INFO_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("接口正在发布验证中，请稍后重试");

        verifyNoInteractions(publishCheckService);
        verify(stateManager, never()).markPublishing(INTERFACE_INFO_ID);
    }

    /**
     * 非下线接口发布时应保持原错误消息。
     */
    @Test
    @DisplayName("上线接口不能发布")
    void shouldRejectOnlineInterfacePublishing() {
        LockedInterfaceSnapshot onlineSnapshot = buildLockedSnapshot(InterfaceInfoStatusEnum.ONLINE.getValue());
        when(stateManager.lockForUpdate(INTERFACE_INFO_ID)).thenReturn(onlineSnapshot);
        when(stateManager.recoverExpiredPublishingStatus(onlineSnapshot)).thenReturn(onlineSnapshot);

        assertThatThrownBy(() -> publishingLifecycleService.startPublishingWithContext(INTERFACE_INFO_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("接口仅支持从下线状态发布");

        verifyNoInteractions(publishCheckService);
        verify(stateManager, never()).markPublishing(INTERFACE_INFO_ID);
    }

    /**
     * 完成发布应委托状态管理服务切换上线。
     */
    @Test
    @DisplayName("完成发布标记上线")
    void shouldCompletePublishingByMarkingOnline() {
        publishingLifecycleService.completePublishing(INTERFACE_INFO_ID);

        verify(stateManager).markOnline(INTERFACE_INFO_ID);
        verify(interfaceChangeAuditService).recordChange(
                INTERFACE_INFO_ID, "测试接口", InterfaceChangeTypeEnum.ONLINE);
    }

    /**
     * 发布失败回滚应委托状态管理服务切回下线。
     */
    @Test
    @DisplayName("发布失败回滚下线")
    void shouldRollbackPublishingByMarkingOffline() {
        publishingLifecycleService.rollbackPublishing(INTERFACE_INFO_ID);

        verify(stateManager).rollbackToOffline(INTERFACE_INFO_ID);
    }

    /**
     * 构造已锁定接口快照。
     *
     * @param status 接口状态
     * @return 已锁定接口快照
     */
    private LockedInterfaceSnapshot buildLockedSnapshot(Integer status) {
        return LockedInterfaceSnapshot.builder()
                .interfaceInfoId(INTERFACE_INFO_ID)
                .name("测试接口")
                .status(status)
                .updateTime(new Date())
                .build();
    }

    /**
     * 构造发布上下文。
     *
     * @return 发布上下文
     */
    private InterfacePublishContext buildPublishContext() {
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setId(INTERFACE_INFO_ID);
        interfaceInfo.setStatus(InterfaceInfoStatusEnum.OFFLINE.getValue());
        InterfacePublishContext publishContext = new InterfacePublishContext();
        publishContext.setInterfaceInfo(interfaceInfo);
        return publishContext;
    }
}
