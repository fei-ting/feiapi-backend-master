package com.feiting.feiapi.unit.service;

import com.feiting.feiapi.interfaceplatform.definition.model.snapshot.InterfaceDefinitionSnapshot;
import com.feiting.feiapi.interfaceplatform.definition.service.api.InterfaceDefinitionChangeService;
import com.feiting.feiapi.interfaceplatform.definition.service.api.InterfaceDefinitionCommandService;
import com.feiting.feiapi.interfaceplatform.definition.service.api.InterfaceDefinitionReader;
import com.feiting.feiapi.interfaceplatform.documentation.service.api.InterfaceDocLifecycleService;
import com.feiting.feiapi.interfaceplatform.facade.service.impl.InterfaceInfoApplicationServiceImpl;
import com.feiting.feiapi.interfaceplatform.lifecycle.model.snapshot.LockedInterfaceSnapshot;
import com.feiting.feiapi.interfaceplatform.lifecycle.service.api.InterfaceStateManager;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.enums.InterfaceInfoStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 接口信息应用协调服务单元测试。
 */
@DisplayName("接口信息应用协调服务单元测试")
class InterfaceInfoApplicationServiceImplTest {

    /** 接口信息 ID。 */
    private static final long INTERFACE_INFO_ID = 1L;

    /** 接口定义命令服务。 */
    private InterfaceDefinitionCommandService definitionCommandService;

    /** 接口定义只读服务。 */
    private InterfaceDefinitionReader definitionReader;

    /** 接口定义变更判断服务。 */
    private InterfaceDefinitionChangeService definitionChangeService;

    /** 接口文档生命周期协作服务。 */
    private InterfaceDocLifecycleService docLifecycleService;

    /** 接口状态管理服务。 */
    private InterfaceStateManager stateManager;

    /** 被测应用协调服务。 */
    private InterfaceInfoApplicationServiceImpl applicationService;

    /**
     * 初始化被测对象及依赖。
     */
    @BeforeEach
    void setUp() {
        definitionCommandService = mock(InterfaceDefinitionCommandService.class);
        definitionReader = mock(InterfaceDefinitionReader.class);
        definitionChangeService = mock(InterfaceDefinitionChangeService.class);
        docLifecycleService = mock(InterfaceDocLifecycleService.class);
        stateManager = mock(InterfaceStateManager.class);
        applicationService = new InterfaceInfoApplicationServiceImpl(
                definitionCommandService,
                definitionReader,
                definitionChangeService,
                docLifecycleService,
                stateManager);
    }

    /**
     * 新增接口后应按新接口定义初始化文档。
     */
    @Test
    @DisplayName("新增接口后初始化文档")
    void shouldInitializeDocAfterAddingInterface() {
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        InterfaceDefinitionSnapshot snapshot = buildDefinitionSnapshot("POST", "{}");
        when(definitionCommandService.save(interfaceInfo)).thenReturn(INTERFACE_INFO_ID);
        when(definitionReader.getRequiredSnapshot(INTERFACE_INFO_ID)).thenReturn(snapshot);

        Long result = applicationService.addInterfaceInfoWithDoc(interfaceInfo);

        assertThat(result).isEqualTo(INTERFACE_INFO_ID);
        InOrder inOrder = inOrder(definitionCommandService, definitionReader, docLifecycleService);
        inOrder.verify(definitionCommandService).save(interfaceInfo);
        inOrder.verify(definitionReader).getRequiredSnapshot(INTERFACE_INFO_ID);
        inOrder.verify(docLifecycleService).initializeFromDefinition(snapshot);
    }

    /**
     * 更新下线接口时应先锁定和断言，再更新定义，最后按变更同步和降级文档。
     */
    @Test
    @DisplayName("更新下线接口同步并降级文档")
    void shouldUpdateOfflineInterfaceAndSyncDocChanges() {
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setId(INTERFACE_INFO_ID);
        LockedInterfaceSnapshot lockedSnapshot = buildLockedSnapshot(InterfaceInfoStatusEnum.OFFLINE.getValue());
        InterfaceDefinitionSnapshot oldDefinition = buildDefinitionSnapshot("GET", "{\"keyword\":\"string\"}");
        InterfaceDefinitionSnapshot latestDefinition = buildDefinitionSnapshot("POST", "{\"name\":\"string\"}");
        when(stateManager.lockForUpdate(INTERFACE_INFO_ID)).thenReturn(lockedSnapshot);
        when(definitionReader.getRequiredSnapshot(INTERFACE_INFO_ID)).thenReturn(oldDefinition, latestDefinition);
        when(definitionChangeService.controlledConfigChanged(oldDefinition, latestDefinition)).thenReturn(true);
        when(definitionChangeService.requestDocTemplateChanged(oldDefinition, latestDefinition)).thenReturn(true);

        Boolean result = applicationService.updateInterfaceInfoWithDoc(interfaceInfo);

        assertThat(result).isTrue();
        InOrder inOrder = inOrder(stateManager, definitionReader, definitionCommandService,
                definitionChangeService, docLifecycleService);
        inOrder.verify(stateManager).lockForUpdate(INTERFACE_INFO_ID);
        inOrder.verify(stateManager).assertOffline(lockedSnapshot);
        inOrder.verify(definitionReader).getRequiredSnapshot(INTERFACE_INFO_ID);
        inOrder.verify(definitionCommandService).updateOffline(interfaceInfo);
        inOrder.verify(definitionReader).getRequiredSnapshot(INTERFACE_INFO_ID);
        inOrder.verify(definitionChangeService).controlledConfigChanged(oldDefinition, latestDefinition);
        inOrder.verify(definitionChangeService).requestDocTemplateChanged(oldDefinition, latestDefinition);
        inOrder.verify(docLifecycleService).synchronizeRequestParams(latestDefinition);
        inOrder.verify(docLifecycleService).downgradeToDraft(INTERFACE_INFO_ID);
    }

    /**
     * 删除接口时应先删除文档数据，再逻辑删除接口主记录。
     */
    @Test
    @DisplayName("删除下线接口时先删文档再删主记录")
    void shouldDeleteDocumentsBeforeDeletingOfflineInterface() {
        LockedInterfaceSnapshot lockedSnapshot = buildLockedSnapshot(InterfaceInfoStatusEnum.OFFLINE.getValue());
        when(stateManager.lockForUpdate(INTERFACE_INFO_ID)).thenReturn(lockedSnapshot);

        Boolean result = applicationService.deleteOfflineInterfaceInfo(INTERFACE_INFO_ID);

        assertThat(result).isTrue();
        InOrder inOrder = inOrder(stateManager, docLifecycleService);
        inOrder.verify(stateManager).lockForUpdate(INTERFACE_INFO_ID);
        inOrder.verify(stateManager).assertDeletableOffline(lockedSnapshot);
        inOrder.verify(docLifecycleService).deleteAllByInterfaceInfoId(INTERFACE_INFO_ID);
        inOrder.verify(stateManager).deleteOffline(INTERFACE_INFO_ID);
    }

    /**
     * 下线接口时应只通过状态管理服务完成上线断言和状态迁移。
     */
    @Test
    @DisplayName("下线接口委托状态管理服务")
    void shouldOfflineInterfaceThroughStateManager() {
        LockedInterfaceSnapshot lockedSnapshot = buildLockedSnapshot(InterfaceInfoStatusEnum.ONLINE.getValue());
        when(stateManager.lockForUpdate(INTERFACE_INFO_ID)).thenReturn(lockedSnapshot);

        Boolean result = applicationService.offlineInterfaceInfo(INTERFACE_INFO_ID);

        assertThat(result).isTrue();
        InOrder inOrder = inOrder(stateManager);
        inOrder.verify(stateManager).lockForUpdate(INTERFACE_INFO_ID);
        inOrder.verify(stateManager).assertOnline(lockedSnapshot);
        inOrder.verify(stateManager).markOffline(INTERFACE_INFO_ID);
        verifyNoMoreInteractions(docLifecycleService);
    }

    /**
     * 构造接口定义快照。
     *
     * @param method        请求方法
     * @param requestParams 运行时请求参数模板
     * @return 接口定义快照
     */
    private InterfaceDefinitionSnapshot buildDefinitionSnapshot(String method, String requestParams) {
        return InterfaceDefinitionSnapshot.builder()
                .interfaceInfoId(INTERFACE_INFO_ID)
                .method(method)
                .requestParams(requestParams)
                .build();
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
                .build();
    }
}
