package com.feiting.feiapi.interfaceplatform.documentation.service.impl;

import com.feiting.feiapi.interfaceplatform.documentation.model.dto.interfaceDoc.InterfaceDocSaveRequest;
import com.feiting.feiapi.interfaceplatform.documentation.service.api.InterfaceDocCommandService;
import com.feiting.feiapi.interfaceplatform.documentation.service.api.InterfaceDocFacadeService;
import org.springframework.stereotype.Service;

/**
 * 接口文档命令服务实现。
 */
@Service
public class InterfaceDocCommandServiceImpl implements InterfaceDocCommandService {

    /**
     * 接口文档兼容门面。
     */
    private final InterfaceDocFacadeService facadeService;

    /**
     * 创建接口文档命令服务。
     *
     * @param facadeService 接口文档兼容门面
     */
    public InterfaceDocCommandServiceImpl(InterfaceDocFacadeService facadeService) {
        this.facadeService = facadeService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean saveDoc(InterfaceDocSaveRequest saveRequest) {
        return facadeService.saveDoc(saveRequest);
    }
}
