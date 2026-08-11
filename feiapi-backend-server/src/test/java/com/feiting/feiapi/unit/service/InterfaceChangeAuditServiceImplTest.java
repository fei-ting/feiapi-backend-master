package com.feiting.feiapi.unit.service;

import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.mapper.InterfaceChangeLogMapper;
import com.feiting.feiapi.model.enums.InterfaceChangeTypeEnum;
import com.feiting.feiapi.service.impl.InterfaceChangeAuditServiceImpl;
import com.feiting.feiapicommon.model.entity.InterfaceChangeLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 接口变更审计服务单元测试。
 */
@DisplayName("接口变更审计服务单元测试")
class InterfaceChangeAuditServiceImplTest {

    /** 接口变更审计日志 Mapper。 */
    private InterfaceChangeLogMapper interfaceChangeLogMapper;

    /** 被测接口变更审计服务。 */
    private InterfaceChangeAuditServiceImpl interfaceChangeAuditService;

    /** 初始化被测服务及依赖。 */
    @BeforeEach
    void setUp() {
        interfaceChangeLogMapper = mock(InterfaceChangeLogMapper.class);
        interfaceChangeAuditService = new InterfaceChangeAuditServiceImpl(interfaceChangeLogMapper);
    }

    /** 写入审计记录时应保存接口快照、变更类型和统一事件时间。 */
    @Test
    @DisplayName("写入完整接口变更审计记录")
    void shouldWriteCompleteInterfaceChangeLog() {
        when(interfaceChangeLogMapper.insert(any(InterfaceChangeLog.class))).thenReturn(1);

        interfaceChangeAuditService.recordChange(1L, "测试接口", InterfaceChangeTypeEnum.UPDATED);

        ArgumentCaptor<InterfaceChangeLog> captor = ArgumentCaptor.forClass(InterfaceChangeLog.class);
        verify(interfaceChangeLogMapper).insert(captor.capture());
        InterfaceChangeLog changeLog = captor.getValue();
        assertThat(changeLog.getInterfaceInfoId()).isEqualTo(1L);
        assertThat(changeLog.getInterfaceName()).isEqualTo("测试接口");
        assertThat(changeLog.getChangeType()).isEqualTo(InterfaceChangeTypeEnum.UPDATED.getCode());
        assertThat(changeLog.getEventTime()).isNotNull().isEqualTo(changeLog.getCreateTime());
    }

    /** 接口名称为空时应写入稳定的默认名称。 */
    @Test
    @DisplayName("接口名称为空时使用默认名称")
    void shouldUseDefaultNameWhenInterfaceNameIsNull() {
        when(interfaceChangeLogMapper.insert(any(InterfaceChangeLog.class))).thenReturn(1);

        interfaceChangeAuditService.recordChange(1L, null, InterfaceChangeTypeEnum.CREATED);

        ArgumentCaptor<InterfaceChangeLog> captor = ArgumentCaptor.forClass(InterfaceChangeLog.class);
        verify(interfaceChangeLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getInterfaceName()).isEqualTo("未命名接口");
    }

    /** 审计参数不合法时应在访问数据库前拒绝。 */
    @Test
    @DisplayName("审计参数不合法时拒绝写入")
    void shouldRejectInvalidAuditArguments() {
        assertThatThrownBy(() -> interfaceChangeAuditService.recordChange(null, "测试接口", null))
                .isInstanceOf(BusinessException.class);
    }

    /** Mapper 未成功写入时应抛出业务异常以触发调用方事务回滚。 */
    @Test
    @DisplayName("审计写入失败时抛出业务异常")
    void shouldThrowBusinessExceptionWhenInsertFails() {
        when(interfaceChangeLogMapper.insert(any(InterfaceChangeLog.class))).thenReturn(0);

        assertThatThrownBy(() -> interfaceChangeAuditService.recordChange(
                1L, "测试接口", InterfaceChangeTypeEnum.ONLINE))
                .isInstanceOf(BusinessException.class)
                .hasMessage("接口变更审计记录写入失败");
    }
}
