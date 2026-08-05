package com.feiting.feiapi.interfaceplatform.documentation.service.impl;

import com.feiting.feiapi.interfaceplatform.documentation.model.vo.InterfaceDocDetailVO;
import com.feiting.feiapi.interfaceplatform.documentation.service.api.InterfaceDocFacadeService;
import com.feiting.feiapi.interfaceplatform.documentation.service.api.InterfaceDocQueryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 接口文档查询服务实现。
 *
 * <p>当前通过兼容门面委托既有查询实现，先稳定跨层入口，再逐步下沉查询细节。</p>
 */
@Service
public class InterfaceDocQueryServiceImpl implements InterfaceDocQueryService {

    /**
     * 接口文档兼容门面。
     */
    private final InterfaceDocFacadeService facadeService;

    /**
     * 创建接口文档查询服务。
     *
     * @param facadeService 接口文档兼容门面
     */
    public InterfaceDocQueryServiceImpl(InterfaceDocFacadeService facadeService) {
        this.facadeService = facadeService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InterfaceDocDetailVO getDocDetail(Long interfaceInfoId, boolean admin) {
        return facadeService.getDocDetail(interfaceInfoId, admin);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Long, String> listDocStatusByInterfaceInfoIds(List<Long> interfaceInfoIds) {
        return facadeService.listDocStatusByInterfaceInfoIds(interfaceInfoIds);
    }
}
