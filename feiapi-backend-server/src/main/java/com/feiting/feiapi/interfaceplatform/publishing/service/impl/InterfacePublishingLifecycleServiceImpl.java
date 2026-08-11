package com.feiting.feiapi.interfaceplatform.publishing.service.impl;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.interfaceplatform.lifecycle.model.snapshot.LockedInterfaceSnapshot;
import com.feiting.feiapi.interfaceplatform.lifecycle.service.api.InterfaceStateManager;
import com.feiting.feiapi.interfaceplatform.publishing.model.context.InterfacePublishContext;
import com.feiting.feiapi.interfaceplatform.publishing.service.api.InterfacePublishCheckService;
import com.feiting.feiapi.interfaceplatform.publishing.service.api.InterfacePublishingLifecycleService;
import com.feiting.feiapi.mapper.InterfaceInfoMapper;
import com.feiting.feiapi.model.enums.InterfaceChangeTypeEnum;
import com.feiting.feiapi.service.InterfaceChangeAuditService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.enums.InterfaceInfoStatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 接口发布生命周期协作服务实现。
 */
@Service
public class InterfacePublishingLifecycleServiceImpl implements InterfacePublishingLifecycleService {

    /**
     * 接口状态管理服务。
     */
    private final InterfaceStateManager stateManager;

    /**
     * 发布前静态检查服务。
     */
    private final InterfacePublishCheckService publishCheckService;

    /** 接口主记录 Mapper。 */
    private final InterfaceInfoMapper interfaceInfoMapper;

    /** 接口变更审计服务。 */
    private final InterfaceChangeAuditService interfaceChangeAuditService;

    /**
     * 创建接口发布生命周期协作服务实现。
     *
     * @param stateManager       接口状态管理服务
     * @param publishCheckService 发布前静态检查服务
     * @param interfaceInfoMapper 接口主记录 Mapper
     * @param interfaceChangeAuditService 接口变更审计服务
     */
    public InterfacePublishingLifecycleServiceImpl(InterfaceStateManager stateManager,
                                                   InterfacePublishCheckService publishCheckService,
                                                   InterfaceInfoMapper interfaceInfoMapper,
                                                   InterfaceChangeAuditService interfaceChangeAuditService) {
        this.stateManager = stateManager;
        this.publishCheckService = publishCheckService;
        this.interfaceInfoMapper = interfaceInfoMapper;
        this.interfaceChangeAuditService = interfaceChangeAuditService;
    }

    /**
     * 校验发布条件并将下线接口切换为发布中状态。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 发布上下文
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public InterfacePublishContext startPublishingWithContext(Long interfaceInfoId) {
        LockedInterfaceSnapshot lockedInterface = stateManager.lockForUpdate(interfaceInfoId);
        LockedInterfaceSnapshot recoveredInterface = stateManager.recoverExpiredPublishingStatus(lockedInterface);
        assertReadyToPublish(recoveredInterface);
        InterfacePublishContext publishContext = publishCheckService.buildContextForPublish(interfaceInfoId);
        stateManager.markPublishing(interfaceInfoId);
        InterfaceInfo interfaceInfo = publishContext.getInterfaceInfo();
        if (interfaceInfo != null) {
            interfaceInfo.setStatus(InterfaceInfoStatusEnum.PUBLISHING.getValue());
        }
        return publishContext;
    }

    /**
     * 将发布中的接口切换为上线状态。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completePublishing(Long interfaceInfoId) {
        stateManager.markOnline(interfaceInfoId);
        InterfaceInfo interfaceInfo = interfaceInfoMapper.selectById(interfaceInfoId);
        if (interfaceInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "接口上线后读取接口信息失败");
        }
        interfaceChangeAuditService.recordChange(
                interfaceInfoId, interfaceInfo.getName(), InterfaceChangeTypeEnum.ONLINE);
    }

    /**
     * 将发布中的接口恢复为下线状态。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollbackPublishing(Long interfaceInfoId) {
        stateManager.rollbackToOffline(interfaceInfoId);
    }

    /**
     * 断言接口允许开始发布。
     *
     * @param interfaceInfo 已锁定接口快照
     */
    private void assertReadyToPublish(LockedInterfaceSnapshot interfaceInfo) {
        if (Objects.equals(interfaceInfo.getStatus(), InterfaceInfoStatusEnum.OFFLINE.getValue())) {
            return;
        }
        if (Objects.equals(interfaceInfo.getStatus(), InterfaceInfoStatusEnum.PUBLISHING.getValue())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口正在发布验证中，请稍后重试");
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口仅支持从下线状态发布");
    }
}
