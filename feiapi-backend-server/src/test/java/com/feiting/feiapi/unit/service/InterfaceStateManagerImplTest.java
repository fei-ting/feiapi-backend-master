package com.feiting.feiapi.unit.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.interfaceplatform.lifecycle.model.snapshot.LockedInterfaceSnapshot;
import com.feiting.feiapi.interfaceplatform.lifecycle.service.impl.InterfaceStateManagerImpl;
import com.feiting.feiapi.mapper.InterfaceInfoMapper;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.enums.InterfaceInfoStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 接口状态管理服务单元测试。
 */
@DisplayName("接口状态管理服务单元测试")
class InterfaceStateManagerImplTest {

    /** 接口信息 ID。 */
    private static final long INTERFACE_INFO_ID = 1L;

    /** 接口信息数据访问对象。 */
    private InterfaceInfoMapper interfaceInfoMapper;

    /** 被测状态管理服务。 */
    private InterfaceStateManagerImpl stateManager;

    /**
     * 初始化被测对象及依赖。
     */
    @BeforeEach
    void setUp() {
        interfaceInfoMapper = mock(InterfaceInfoMapper.class);
        stateManager = new InterfaceStateManagerImpl(interfaceInfoMapper);
    }

    /**
     * 非上线接口下线时应保持原错误消息。
     */
    @Test
    @DisplayName("非上线接口不能下线")
    void shouldRejectOfflineWhenInterfaceIsNotOnline() {
        LockedInterfaceSnapshot snapshot = buildSnapshot(InterfaceInfoStatusEnum.OFFLINE.getValue(), new Date());

        assertThatThrownBy(() -> stateManager.assertOnline(snapshot))
                .isInstanceOf(BusinessException.class)
                .hasMessage("接口仅支持从上线状态下线");
    }

    /**
     * 删除上线接口时应提示先下线。
     */
    @Test
    @DisplayName("上线接口删除提示先下线")
    void shouldRejectOnlineInterfaceDeletion() {
        LockedInterfaceSnapshot snapshot = buildSnapshot(InterfaceInfoStatusEnum.ONLINE.getValue(), new Date());

        assertThatThrownBy(() -> stateManager.assertDeletableOffline(snapshot))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请先下线接口后再删除");
    }

    /**
     * 删除发布中接口时应保持发布中错误消息。
     */
    @Test
    @DisplayName("发布中接口不能删除")
    void shouldRejectPublishingInterfaceDeletion() {
        LockedInterfaceSnapshot snapshot = buildSnapshot(InterfaceInfoStatusEnum.PUBLISHING.getValue(), new Date());

        assertThatThrownBy(() -> stateManager.assertDeletableOffline(snapshot))
                .isInstanceOf(BusinessException.class)
                .hasMessage("接口正在发布验证中，不能删除");
    }

    /**
     * 删除未知状态接口时应保持状态异常消息。
     */
    @Test
    @DisplayName("未知状态接口不能删除")
    void shouldRejectUnknownStatusDeletion() {
        LockedInterfaceSnapshot snapshot = buildSnapshot(99, new Date());

        assertThatThrownBy(() -> stateManager.assertDeletableOffline(snapshot))
                .isInstanceOf(BusinessException.class)
                .hasMessage("接口状态异常，不能删除");
    }

    /**
     * 超时发布中状态应恢复为下线。
     */
    @Test
    @DisplayName("超时发布中状态恢复为下线")
    void shouldRecoverExpiredPublishingStatus() {
        Date expiredUpdateTime = new Date(System.currentTimeMillis() - 11 * 60 * 1000L);
        LockedInterfaceSnapshot snapshot = buildSnapshot(InterfaceInfoStatusEnum.PUBLISHING.getValue(), expiredUpdateTime);
        when(interfaceInfoMapper.update(any(InterfaceInfo.class), any(UpdateWrapper.class))).thenReturn(1);

        LockedInterfaceSnapshot recoveredSnapshot = stateManager.recoverExpiredPublishingStatus(snapshot);

        assertThat(recoveredSnapshot.getStatus()).isEqualTo(InterfaceInfoStatusEnum.OFFLINE.getValue());
        assertThat(recoveredSnapshot.getInterfaceInfoId()).isEqualTo(INTERFACE_INFO_ID);
        verify(interfaceInfoMapper).update(any(InterfaceInfo.class), any(UpdateWrapper.class));
    }

    /**
     * 标记发布中条件更新失败时应保持原错误消息。
     */
    @Test
    @DisplayName("标记发布中失败提示刷新")
    void shouldKeepMarkPublishingFailureMessage() {
        when(interfaceInfoMapper.update(any(InterfaceInfo.class), any(UpdateWrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> stateManager.markPublishing(INTERFACE_INFO_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("接口发布状态更新失败，请刷新后重试");
    }

    /**
     * 标记上线条件更新失败时应保持原错误消息。
     */
    @Test
    @DisplayName("标记上线失败提示刷新")
    void shouldKeepMarkOnlineFailureMessage() {
        when(interfaceInfoMapper.update(any(InterfaceInfo.class), any(UpdateWrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> stateManager.markOnline(INTERFACE_INFO_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("接口发布状态已变化，请刷新后重试");
    }

    /**
     * 回滚下线条件更新失败时应保持原错误消息。
     */
    @Test
    @DisplayName("回滚下线失败提示")
    void shouldKeepRollbackFailureMessage() {
        when(interfaceInfoMapper.update(any(InterfaceInfo.class), any(UpdateWrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> stateManager.rollbackToOffline(INTERFACE_INFO_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("接口发布验证失败后回滚状态失败");
    }

    /**
     * 下线条件更新失败时应保持原错误消息。
     */
    @Test
    @DisplayName("下线失败提示刷新")
    void shouldKeepMarkOfflineFailureMessage() {
        when(interfaceInfoMapper.update(any(InterfaceInfo.class), any(UpdateWrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> stateManager.markOffline(INTERFACE_INFO_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("接口下线状态已变化，请刷新后重试");
    }

    /**
     * 删除下线接口条件更新失败时应保持原错误消息。
     */
    @Test
    @DisplayName("删除下线接口失败提示刷新")
    void shouldKeepDeleteOfflineFailureMessage() {
        when(interfaceInfoMapper.logicDeleteOfflineById(
                INTERFACE_INFO_ID, InterfaceInfoStatusEnum.OFFLINE.getValue())).thenReturn(0);

        assertThatThrownBy(() -> stateManager.deleteOffline(INTERFACE_INFO_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("接口状态已变化，请刷新后重试");
    }

    /**
     * 构造已锁定接口快照。
     *
     * @param status     接口状态
     * @param updateTime 更新时间
     * @return 已锁定接口快照
     */
    private LockedInterfaceSnapshot buildSnapshot(Integer status, Date updateTime) {
        return LockedInterfaceSnapshot.builder()
                .interfaceInfoId(INTERFACE_INFO_ID)
                .name("测试接口")
                .status(status)
                .updateTime(updateTime)
                .build();
    }
}
