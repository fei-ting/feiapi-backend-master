package com.feiting.feiapi.interfaceplatform.definition.service.api;

import com.feiting.feiapi.interfaceplatform.definition.model.snapshot.InterfaceDefinitionSnapshot;
import com.feiting.feiapi.interfaceplatform.definition.model.snapshot.RuntimeRequestTemplate;
import com.feiting.feiapi.interfaceplatform.definition.model.snapshot.SdkContractSnapshot;

/**
 * 接口定义域只读服务。
 *
 * <p>用于向文档域、发布域和协调层提供接口运行定义快照，不暴露实体、Mapper 或可写能力。</p>
 */
public interface InterfaceDefinitionReader {

    /**
     * 获取必然存在的接口定义快照。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 接口定义快照
     */
    InterfaceDefinitionSnapshot getRequiredSnapshot(Long interfaceInfoId);

    /**
     * 获取运行时请求参数模板快照。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 运行时请求参数模板快照
     */
    RuntimeRequestTemplate getRuntimeRequestTemplate(Long interfaceInfoId);

    /**
     * 获取 SDK 方法契约快照。
     *
     * @param sdkMethodName SDK 方法名
     * @return SDK 方法契约快照
     */
    SdkContractSnapshot getSdkContract(String sdkMethodName);
}
