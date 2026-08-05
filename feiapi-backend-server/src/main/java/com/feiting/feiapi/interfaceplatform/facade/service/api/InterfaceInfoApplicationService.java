package com.feiting.feiapi.interfaceplatform.facade.service.api;

import com.feiting.feiapicommon.model.entity.InterfaceInfo;

/**
 * 接口信息应用协调服务。
 *
 * <p>负责新增、更新、删除和下线等跨能力域用例编排，保持事务边界集中。</p>
 */
public interface InterfaceInfoApplicationService {

    /**
     * 新增接口信息并初始化结构化接口文档。
     *
     * @param interfaceInfo 接口信息
     * @return 新增接口 ID
     */
    Long addInterfaceInfoWithDoc(InterfaceInfo interfaceInfo);

    /**
     * 更新接口信息并按原规则同步或降级接口文档。
     *
     * @param interfaceInfo 接口信息
     * @return 是否更新成功
     */
    Boolean updateInterfaceInfoWithDoc(InterfaceInfo interfaceInfo);

    /**
     * 删除处于下线状态的接口信息。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 是否删除成功
     */
    Boolean deleteOfflineInterfaceInfo(Long interfaceInfoId);

    /**
     * 将上线接口下线。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 是否下线成功
     */
    Boolean offlineInterfaceInfo(Long interfaceInfoId);
}
