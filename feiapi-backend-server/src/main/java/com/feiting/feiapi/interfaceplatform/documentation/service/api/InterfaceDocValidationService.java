package com.feiting.feiapi.interfaceplatform.documentation.service.api;

/**
 * 接口文档业务校验服务。
 */
public interface InterfaceDocValidationService {

    /**
     * 校验接口文档是否满足发布条件。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    void validateReadyForPublish(Long interfaceInfoId);
}
