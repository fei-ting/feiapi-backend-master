package com.feiting.feiapi.interfaceplatform.facade.service.impl;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.interfaceplatform.definition.model.snapshot.InterfaceDefinitionSnapshot;
import com.feiting.feiapi.interfaceplatform.definition.model.snapshot.SdkContractSnapshot;
import com.feiting.feiapi.interfaceplatform.definition.service.api.InterfaceDefinitionChangeService;
import com.feiting.feiapi.interfaceplatform.definition.service.api.InterfaceDefinitionCommandService;
import com.feiting.feiapi.interfaceplatform.definition.service.api.InterfaceDefinitionReader;
import com.feiting.feiapi.interfaceplatform.documentation.service.api.InterfaceDocLifecycleService;
import com.feiting.feiapi.interfaceplatform.facade.service.api.InterfaceInfoApplicationService;
import com.feiting.feiapi.interfaceplatform.lifecycle.model.snapshot.LockedInterfaceSnapshot;
import com.feiting.feiapi.interfaceplatform.lifecycle.service.api.InterfaceStateManager;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 接口信息应用协调服务实现。
 */
@Service
public class InterfaceInfoApplicationServiceImpl implements InterfaceInfoApplicationService {

    /**
     * 接口定义命令服务。
     */
    private final InterfaceDefinitionCommandService definitionCommandService;

    /**
     * 接口定义只读服务。
     */
    private final InterfaceDefinitionReader definitionReader;

    /**
     * 接口定义变更判断服务。
     */
    private final InterfaceDefinitionChangeService definitionChangeService;

    /**
     * 接口文档生命周期协作服务。
     */
    private final InterfaceDocLifecycleService docLifecycleService;

    /**
     * 接口状态管理服务。
     */
    private final InterfaceStateManager stateManager;

    /**
     * 创建接口信息应用协调服务实现。
     *
     * @param definitionCommandService 接口定义命令服务
     * @param definitionReader         接口定义只读服务
     * @param definitionChangeService  接口定义变更判断服务
     * @param docLifecycleService      接口文档生命周期协作服务
     * @param stateManager             接口状态管理服务
     */
    public InterfaceInfoApplicationServiceImpl(InterfaceDefinitionCommandService definitionCommandService,
                                               InterfaceDefinitionReader definitionReader,
                                               InterfaceDefinitionChangeService definitionChangeService,
                                               InterfaceDocLifecycleService docLifecycleService,
                                               InterfaceStateManager stateManager) {
        this.definitionCommandService = definitionCommandService;
        this.definitionReader = definitionReader;
        this.definitionChangeService = definitionChangeService;
        this.docLifecycleService = docLifecycleService;
        this.stateManager = stateManager;
    }

    /**
     * 新增接口信息并初始化结构化接口文档。
     *
     * @param interfaceInfo 接口信息
     * @return 新增接口 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addInterfaceInfoWithDoc(InterfaceInfo interfaceInfo) {
        validateRegisteredSdkMethod(interfaceInfo);
        Long interfaceInfoId = definitionCommandService.save(interfaceInfo);
        docLifecycleService.initializeFromDefinition(definitionReader.getRequiredSnapshot(interfaceInfoId));
        return interfaceInfoId;
    }

    /**
     * 校验新增接口绑定的 SDK 方法已经注册。
     *
     * @param interfaceInfo 待新增接口信息
     */
    private void validateRegisteredSdkMethod(InterfaceInfo interfaceInfo) {
        if (interfaceInfo == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        SdkContractSnapshot sdkContract = definitionReader.getSdkContract(interfaceInfo.getSdkMethodName());
        if (sdkContract == null || !sdkContract.isSupported()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "SDK 方法不存在或未注册");
        }
    }

    /**
     * 更新接口信息并按原规则同步或降级接口文档。
     *
     * @param interfaceInfo 接口信息
     * @return 是否更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateInterfaceInfoWithDoc(InterfaceInfo interfaceInfo) {
        if (interfaceInfo == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        validateInterfaceInfoId(interfaceInfo.getId());
        LockedInterfaceSnapshot lockedInterface = stateManager.lockForUpdate(interfaceInfo.getId());
        stateManager.assertOffline(lockedInterface);
        InterfaceDefinitionSnapshot oldDefinition = definitionReader.getRequiredSnapshot(interfaceInfo.getId());
        definitionCommandService.updateOffline(interfaceInfo);
        InterfaceDefinitionSnapshot latestDefinition = definitionReader.getRequiredSnapshot(interfaceInfo.getId());
        boolean controlledConfigChanged = definitionChangeService.controlledConfigChanged(oldDefinition, latestDefinition);
        if (definitionChangeService.requestDocTemplateChanged(oldDefinition, latestDefinition)) {
            docLifecycleService.synchronizeRequestParams(latestDefinition);
        }
        if (controlledConfigChanged) {
            docLifecycleService.downgradeToDraft(latestDefinition.getInterfaceInfoId());
        }
        return true;
    }

    /**
     * 删除处于下线状态的接口信息。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 是否删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteOfflineInterfaceInfo(Long interfaceInfoId) {
        validateInterfaceInfoId(interfaceInfoId);
        LockedInterfaceSnapshot lockedInterface = stateManager.lockForUpdate(interfaceInfoId);
        stateManager.assertDeletableOffline(lockedInterface);
        docLifecycleService.deleteAllByInterfaceInfoId(interfaceInfoId);
        stateManager.deleteOffline(interfaceInfoId);
        return true;
    }

    /**
     * 将上线接口下线。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 是否下线成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean offlineInterfaceInfo(Long interfaceInfoId) {
        validateInterfaceInfoId(interfaceInfoId);
        LockedInterfaceSnapshot lockedInterface = stateManager.lockForUpdate(interfaceInfoId);
        stateManager.assertOnline(lockedInterface);
        stateManager.markOffline(interfaceInfoId);
        return true;
    }

    /**
     * 校验接口信息 ID。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    private void validateInterfaceInfoId(Long interfaceInfoId) {
        if (interfaceInfoId == null || interfaceInfoId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
    }
}
