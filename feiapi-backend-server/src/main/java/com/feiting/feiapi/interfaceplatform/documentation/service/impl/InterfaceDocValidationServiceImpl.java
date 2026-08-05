package com.feiting.feiapi.interfaceplatform.documentation.service.impl;

import com.feiting.feiapi.interfaceplatform.documentation.service.api.InterfaceDocFacadeService;
import com.feiting.feiapi.interfaceplatform.documentation.service.api.InterfaceDocValidationService;
import org.springframework.stereotype.Service;

/**
 * 接口文档业务校验服务实现。
 */
@Service
public class InterfaceDocValidationServiceImpl implements InterfaceDocValidationService {

    /**
     * 接口文档兼容门面。
     */
    private final InterfaceDocFacadeService facadeService;

    /**
     * 创建接口文档业务校验服务。
     *
     * @param facadeService 接口文档兼容门面
     */
    public InterfaceDocValidationServiceImpl(InterfaceDocFacadeService facadeService) {
        this.facadeService = facadeService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateReadyForPublish(Long interfaceInfoId) {
        facadeService.validateReadyForPublish(interfaceInfoId);
    }
}
