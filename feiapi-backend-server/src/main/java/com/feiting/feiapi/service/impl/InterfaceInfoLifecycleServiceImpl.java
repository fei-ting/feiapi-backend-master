package com.feiting.feiapi.service.impl;

import com.feiting.feiapi.interfaceplatform.facade.service.api.InterfaceInfoApplicationService;
import com.feiting.feiapi.interfaceplatform.publishing.model.context.InterfacePublishContext;
import com.feiting.feiapi.interfaceplatform.publishing.service.api.InterfacePublishingLifecycleService;
import com.feiting.feiapi.service.InterfaceInfoLifecycleService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import org.springframework.stereotype.Service;

/**
 * 接口信息生命周期服务兼容实现。
 *
 * <p>阶段 5 起，新增、更新、删除由协调层承接，发布状态分段事务由发布域承接。</p>
 */
@Service
public class InterfaceInfoLifecycleServiceImpl implements InterfaceInfoLifecycleService {

    /**
     * 接口信息应用协调服务。
     */
    private final InterfaceInfoApplicationService applicationService;

    /**
     * 接口发布生命周期协作服务。
     */
    private final InterfacePublishingLifecycleService publishingLifecycleService;

    /**
     * 创建接口信息生命周期服务兼容实现。
     *
     * @param applicationService          接口信息应用协调服务
     * @param publishingLifecycleService 接口发布生命周期协作服务
     */
    public InterfaceInfoLifecycleServiceImpl(InterfaceInfoApplicationService applicationService,
                                             InterfacePublishingLifecycleService publishingLifecycleService) {
        this.applicationService = applicationService;
        this.publishingLifecycleService = publishingLifecycleService;
    }

    /**
     * 新增接口信息并同步结构化接口文档。
     *
     * @param interfaceInfo 接口信息
     * @return 新增接口 ID
     */
    @Override
    public Long addInterfaceInfoWithDoc(InterfaceInfo interfaceInfo) {
        return applicationService.addInterfaceInfoWithDoc(interfaceInfo);
    }

    /**
     * 更新接口信息并同步结构化接口文档。
     *
     * @param interfaceInfo 接口信息
     * @return 是否更新成功
     */
    @Override
    public Boolean updateInterfaceInfoWithDoc(InterfaceInfo interfaceInfo) {
        return applicationService.updateInterfaceInfoWithDoc(interfaceInfo);
    }

    /**
     * 删除处于下线状态的接口信息。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 是否删除成功
     */
    @Override
    public Boolean deleteOfflineInterfaceInfo(Long interfaceInfoId) {
        return applicationService.deleteOfflineInterfaceInfo(interfaceInfoId);
    }

    /**
     * 校验发布条件并将下线接口切换为发布中状态。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 发布中的接口快照
     */
    @Override
    public InterfaceInfo startPublishing(Long interfaceInfoId) {
        return startPublishingWithContext(interfaceInfoId).getInterfaceInfo();
    }

    /**
     * 校验发布条件并将下线接口切换为发布中状态，返回完整发布上下文。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 发布上下文
     */
    @Override
    public InterfacePublishContext startPublishingWithContext(Long interfaceInfoId) {
        return publishingLifecycleService.startPublishingWithContext(interfaceInfoId);
    }

    /**
     * 将发布中的接口切换为上线状态。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    @Override
    public void completePublishing(Long interfaceInfoId) {
        publishingLifecycleService.completePublishing(interfaceInfoId);
    }

    /**
     * 将发布中的接口恢复为下线状态。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    @Override
    public void rollbackPublishing(Long interfaceInfoId) {
        publishingLifecycleService.rollbackPublishing(interfaceInfoId);
    }
}
