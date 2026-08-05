package com.feiting.feiapi.interfaceplatform.documentation.service.api;

/**
 * 接口文档持久化服务。
 */
public interface InterfaceDocPersistenceService {

    /**
     * 删除指定接口关联的全部文档数据。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    void deleteAllByInterfaceInfoId(Long interfaceInfoId);
}
