package com.feiting.feiapi.interfaceplatform.documentation.service.impl;

import com.feiting.feiapi.interfaceplatform.documentation.model.entity.InterfaceDoc;
import com.feiting.feiapi.interfaceplatform.documentation.model.entity.InterfaceDocErrorCode;
import com.feiting.feiapi.interfaceplatform.documentation.model.entity.InterfaceDocParam;
import com.feiting.feiapi.interfaceplatform.documentation.service.api.InterfaceDocPersistenceService;
import com.feiting.feiapi.service.InterfaceDocErrorCodeService;
import com.feiting.feiapi.service.InterfaceDocParamService;
import com.feiting.feiapi.service.InterfaceDocService;
import org.springframework.stereotype.Service;

/**
 * 接口文档持久化服务实现。
 *
 * <p>该实现集中封装生命周期删除使用的三张文档表，保持既有删除顺序。</p>
 */
@Service
public class InterfaceDocPersistenceServiceImpl implements InterfaceDocPersistenceService {

    /**
     * 文档主信息服务。
     */
    private final InterfaceDocService interfaceDocService;

    /**
     * 文档参数服务。
     */
    private final InterfaceDocParamService interfaceDocParamService;

    /**
     * 文档错误码服务。
     */
    private final InterfaceDocErrorCodeService interfaceDocErrorCodeService;

    /**
     * 创建接口文档持久化服务。
     *
     * @param interfaceDocService          文档主信息服务
     * @param interfaceDocParamService     文档参数服务
     * @param interfaceDocErrorCodeService 文档错误码服务
     */
    public InterfaceDocPersistenceServiceImpl(InterfaceDocService interfaceDocService,
                                              InterfaceDocParamService interfaceDocParamService,
                                              InterfaceDocErrorCodeService interfaceDocErrorCodeService) {
        this.interfaceDocService = interfaceDocService;
        this.interfaceDocParamService = interfaceDocParamService;
        this.interfaceDocErrorCodeService = interfaceDocErrorCodeService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteAllByInterfaceInfoId(Long interfaceInfoId) {
        interfaceDocParamService.lambdaUpdate()
                .eq(InterfaceDocParam::getInterfaceInfoId, interfaceInfoId)
                .remove();
        interfaceDocErrorCodeService.lambdaUpdate()
                .eq(InterfaceDocErrorCode::getInterfaceInfoId, interfaceInfoId)
                .remove();
        interfaceDocService.lambdaUpdate()
                .eq(InterfaceDoc::getInterfaceInfoId, interfaceInfoId)
                .remove();
    }
}
