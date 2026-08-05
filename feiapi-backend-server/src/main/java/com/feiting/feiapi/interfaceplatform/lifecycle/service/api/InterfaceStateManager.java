package com.feiting.feiapi.interfaceplatform.lifecycle.service.api;

import com.feiting.feiapi.interfaceplatform.lifecycle.model.snapshot.LockedInterfaceSnapshot;

/**
 * 接口生命周期状态管理服务。
 *
 * <p>用于发布域和协调层通过公开接口完成锁定与状态迁移，不直接暴露 Mapper。</p>
 */
public interface InterfaceStateManager {

    /**
     * 锁定接口主记录并返回只读快照。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 已锁定接口快照
     */
    LockedInterfaceSnapshot lockForUpdate(Long interfaceInfoId);

    /**
     * 断言接口处于下线状态。
     *
     * @param interfaceInfo 已锁定接口快照
     */
    void assertOffline(LockedInterfaceSnapshot interfaceInfo);

    /**
     * 将接口标记为发布中状态。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    void markPublishing(Long interfaceInfoId);

    /**
     * 将接口标记为上线状态。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    void markOnline(Long interfaceInfoId);

    /**
     * 将接口从发布中回滚为下线状态。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    void rollbackToOffline(Long interfaceInfoId);
}
