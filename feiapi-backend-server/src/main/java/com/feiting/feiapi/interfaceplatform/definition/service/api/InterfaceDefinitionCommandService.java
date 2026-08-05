package com.feiting.feiapi.interfaceplatform.definition.service.api;

import com.feiting.feiapicommon.model.entity.InterfaceInfo;

/**
 * 接口定义命令服务。
 *
 * <p>用于协调层保存和更新接口运行定义，不向调用方暴露 Mapper。</p>
 */
public interface InterfaceDefinitionCommandService {

    /**
     * 保存接口运行定义。
     *
     * @param interfaceInfo 接口信息
     * @return 新增接口 ID
     */
    Long save(InterfaceInfo interfaceInfo);

    /**
     * 在接口处于下线状态时更新运行定义。
     *
     * @param interfaceInfo 接口信息
     */
    void updateOffline(InterfaceInfo interfaceInfo);
}
