package com.feiting.feiapi.interfaceplatform.documentation.service.impl;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.interfaceplatform.definition.model.snapshot.InterfaceDefinitionSnapshot;
import com.feiting.feiapi.interfaceplatform.documentation.service.api.InterfaceDocFacadeService;
import com.feiting.feiapi.interfaceplatform.documentation.service.api.InterfaceDocLifecycleService;
import com.feiting.feiapi.interfaceplatform.documentation.service.api.InterfaceDocPersistenceService;
import com.feiting.feiapi.interfaceplatform.documentation.service.api.InterfaceDocSyncService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import org.springframework.stereotype.Service;

/**
 * 接口文档生命周期协作服务实现。
 */
@Service
public class InterfaceDocLifecycleServiceImpl implements InterfaceDocLifecycleService {

    /**
     * 接口文档同步服务。
     */
    private final InterfaceDocSyncService syncService;

    /**
     * 接口文档兼容门面。
     */
    private final InterfaceDocFacadeService facadeService;

    /**
     * 接口文档持久化服务。
     */
    private final InterfaceDocPersistenceService persistenceService;

    /**
     * 创建接口文档生命周期协作服务。
     *
     * @param syncService        文档同步服务
     * @param facadeService      文档兼容门面
     * @param persistenceService 文档持久化服务
     */
    public InterfaceDocLifecycleServiceImpl(InterfaceDocSyncService syncService,
                                             InterfaceDocFacadeService facadeService,
                                             InterfaceDocPersistenceService persistenceService) {
        this.syncService = syncService;
        this.facadeService = facadeService;
        this.persistenceService = persistenceService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initializeFromDefinition(InterfaceDefinitionSnapshot definition) {
        syncService.syncRequestDocFromInterfaceInfo(toInterfaceInfo(definition));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void synchronizeRequestParams(InterfaceDefinitionSnapshot definition) {
        syncService.syncRequestDocFromInterfaceInfo(toInterfaceInfo(definition));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void downgradeToDraft(Long interfaceInfoId) {
        facadeService.downgradeToDraft(interfaceInfoId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteAllByInterfaceInfoId(Long interfaceInfoId) {
        persistenceService.deleteAllByInterfaceInfoId(interfaceInfoId);
    }

    /**
     * 将定义快照转换为文档同步兼容所需的接口信息对象。
     *
     * @param definition 接口定义快照
     * @return 接口信息对象
     */
    private InterfaceInfo toInterfaceInfo(InterfaceDefinitionSnapshot definition) {
        if (definition == null || definition.getInterfaceInfoId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setId(definition.getInterfaceInfoId());
        interfaceInfo.setName(definition.getName());
        interfaceInfo.setSdkMethodName(definition.getSdkMethodName());
        interfaceInfo.setDescription(definition.getDescription());
        interfaceInfo.setUrl(definition.getUrl());
        interfaceInfo.setPath(definition.getPath());
        interfaceInfo.setTargetHost(definition.getTargetHost());
        interfaceInfo.setRequestParams(definition.getRequestParams());
        interfaceInfo.setRequestHeader(definition.getRequestHeader());
        interfaceInfo.setResponseHeader(definition.getResponseHeader());
        interfaceInfo.setStatus(definition.getStatus());
        interfaceInfo.setMethod(definition.getMethod());
        interfaceInfo.setQuotaType(definition.getQuotaType());
        interfaceInfo.setUserId(definition.getUserId());
        interfaceInfo.setCreateTime(definition.getCreateTime());
        interfaceInfo.setUpdateTime(definition.getUpdateTime());
        return interfaceInfo;
    }
}
