package com.feiting.feiapi.interfaceplatform.documentation.service.impl;

import com.feiting.feiapi.interfaceplatform.documentation.service.api.InterfaceDocFacadeService;
import com.feiting.feiapi.interfaceplatform.documentation.service.api.InterfaceDocSyncService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import org.springframework.stereotype.Service;

/**
 * 接口文档同步服务实现。
 */
@Service
public class InterfaceDocSyncServiceImpl implements InterfaceDocSyncService {

    /**
     * 接口文档兼容门面。
     */
    private final InterfaceDocFacadeService facadeService;

    /**
     * 创建接口文档同步服务。
     *
     * @param facadeService 接口文档兼容门面
     */
    public InterfaceDocSyncServiceImpl(InterfaceDocFacadeService facadeService) {
        this.facadeService = facadeService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void syncRequestDocFromInterfaceInfo(InterfaceInfo interfaceInfo) {
        facadeService.syncRequestDocFromInterfaceInfo(interfaceInfo);
    }
}
