package com.feiting.feiapi.service;

import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapi.model.publish.InterfacePublishContext;

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

    /**
     * 删除处于下线状态的接口信息。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 是否删除成功
     */
    Boolean deleteOfflineInterfaceInfo(Long interfaceInfoId);

    /**
     * 校验发布条件并将下线接口切换为发布中状态。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 发布中的接口快照
     */
    InterfaceInfo startPublishing(Long interfaceInfoId);

    /**
     * 校验发布条件并将下线接口切换为发布中状态，返回完整发布上下文。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 发布上下文
     */
    InterfacePublishContext startPublishingWithContext(Long interfaceInfoId);

    /**
     * 将发布中的接口切换为上线状态。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    void completePublishing(Long interfaceInfoId);

    /**
     * 将发布中的接口恢复为下线状态。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    void rollbackPublishing(Long interfaceInfoId);
}
