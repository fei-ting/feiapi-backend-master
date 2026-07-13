package com.feiting.feiapi.service;

import com.feiting.feiapicommon.model.entity.InterfaceInfo;

/**
 * 接口信息生命周期服务。
 */
public interface InterfaceInfoLifecycleService {

    /**
     * 新增接口信息并同步结构化接口文档。
     *
     * @param interfaceInfo 接口信息
     * @return 新增接口 ID
     */
    Long addInterfaceInfoWithDoc(InterfaceInfo interfaceInfo);

    /**
     * 更新接口信息并同步结构化接口文档。
     *
     * @param interfaceInfo 接口信息
     * @return 是否更新成功
     */
    Boolean updateInterfaceInfoWithDoc(InterfaceInfo interfaceInfo);
}
