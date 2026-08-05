package com.feiting.feiapi.interfaceplatform.documentation.service.api;

import com.feiting.feiapi.interfaceplatform.definition.model.snapshot.InterfaceDefinitionSnapshot;

/**
 * 接口文档生命周期协作服务。
 *
 * <p>用于新增、更新和删除接口时由协调层调用文档域专用写入能力。</p>
 */
public interface InterfaceDocLifecycleService {

    /**
     * 根据接口定义初始化草稿文档。
     *
     * @param definition 接口定义快照
     */
    void initializeFromDefinition(InterfaceDefinitionSnapshot definition);

    /**
     * 根据接口定义同步请求参数文档。
     *
     * @param definition 接口定义快照
     */
    void synchronizeRequestParams(InterfaceDefinitionSnapshot definition);

    /**
     * 将接口文档降级为草稿状态。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    void downgradeToDraft(Long interfaceInfoId);

    /**
     * 删除指定接口关联的全部文档数据。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    void deleteAllByInterfaceInfoId(Long interfaceInfoId);
}
