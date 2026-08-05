package com.feiting.feiapi.unit.service;

import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.interfaceplatform.definition.model.snapshot.InterfaceDefinitionSnapshot;
import com.feiting.feiapi.interfaceplatform.documentation.service.api.InterfaceDocFacadeService;
import com.feiting.feiapi.interfaceplatform.documentation.service.api.InterfaceDocPersistenceService;
import com.feiting.feiapi.interfaceplatform.documentation.service.api.InterfaceDocSyncService;
import com.feiting.feiapi.interfaceplatform.documentation.service.impl.InterfaceDocLifecycleServiceImpl;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 接口文档生命周期协作服务单元测试。
 */
@DisplayName("接口文档生命周期协作服务单元测试")
class InterfaceDocLifecycleServiceImplTest {

    /**
     * 同步请求参数时应完整传递定义快照字段。
     */
    @Test
    @DisplayName("同步请求参数时传递定义快照字段")
    void shouldPassDefinitionSnapshotToSyncService() {
        InterfaceDocSyncService syncService = mock(InterfaceDocSyncService.class);
        InterfaceDocLifecycleServiceImpl service = new InterfaceDocLifecycleServiceImpl(
                syncService, mock(InterfaceDocFacadeService.class), mock(InterfaceDocPersistenceService.class));
        Date createTime = new Date(1_000L);
        Date updateTime = new Date(2_000L);

        service.synchronizeRequestParams(InterfaceDefinitionSnapshot.builder()
                .interfaceInfoId(1L)
                .name("测试接口")
                .sdkMethodName("getTest")
                .description("接口说明")
                .url("http://show")
                .path("/test")
                .targetHost("http://target")
                .requestParams("{\"name\":\"string\"}")
                .requestHeader("{}")
                .responseHeader("{}")
                .status(0)
                .method("POST")
                .quotaType("DAILY")
                .userId(10L)
                .createTime(createTime)
                .updateTime(updateTime)
                .build());

        var captor = forClass(InterfaceInfo.class);
        verify(syncService).syncRequestDocFromInterfaceInfo(captor.capture());
        InterfaceInfo interfaceInfo = captor.getValue();
        assertThat(interfaceInfo.getId()).isEqualTo(1L);
        assertThat(interfaceInfo.getRequestParams()).isEqualTo("{\"name\":\"string\"}");
        assertThat(interfaceInfo.getMethod()).isEqualTo("POST");
        assertThat(interfaceInfo.getCreateTime()).isSameAs(createTime);
        assertThat(interfaceInfo.getUpdateTime()).isSameAs(updateTime);
    }

    /**
     * 快照为空时应保持业务参数异常语义。
     */
    @Test
    @DisplayName("快照为空时抛出业务参数异常")
    void shouldRejectNullDefinitionSnapshot() {
        InterfaceDocLifecycleServiceImpl service = new InterfaceDocLifecycleServiceImpl(
                mock(InterfaceDocSyncService.class),
                mock(InterfaceDocFacadeService.class),
                mock(InterfaceDocPersistenceService.class));

        assertThatThrownBy(() -> service.initializeFromDefinition(null))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 降级和删除应委托文档域专用服务。
     */
    @Test
    @DisplayName("降级和删除委托文档域服务")
    void shouldDelegateDraftDowngradeAndDeletion() {
        InterfaceDocFacadeService facadeService = mock(InterfaceDocFacadeService.class);
        InterfaceDocPersistenceService persistenceService = mock(InterfaceDocPersistenceService.class);
        InterfaceDocLifecycleServiceImpl service = new InterfaceDocLifecycleServiceImpl(
                mock(InterfaceDocSyncService.class), facadeService, persistenceService);

        service.downgradeToDraft(1L);
        service.deleteAllByInterfaceInfoId(1L);

        verify(facadeService).downgradeToDraft(1L);
        verify(persistenceService).deleteAllByInterfaceInfoId(1L);
    }
}
