package com.feiting.feiapi.interfaceplatform.publishing.service.api;

import com.feiting.feiapi.interfaceplatform.publishing.model.context.InterfacePublishContext;

/**
 * 接口发布生命周期协作服务。
 *
 * <p>用于发布域在分段事务中完成发布开始、完成和回滚。</p>
 */
public interface InterfacePublishingLifecycleService {

    /**
     * 校验发布条件并将下线接口切换为发布中状态。
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
