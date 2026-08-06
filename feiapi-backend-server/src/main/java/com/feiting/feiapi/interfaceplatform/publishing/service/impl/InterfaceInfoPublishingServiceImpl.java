package com.feiting.feiapi.interfaceplatform.publishing.service.impl;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.interfaceplatform.publishing.model.context.InterfacePublishContext;
import com.feiting.feiapi.interfaceplatform.publishing.service.api.InterfaceInfoPublishingService;
import com.feiting.feiapi.interfaceplatform.publishing.service.api.InterfacePublishProbeService;
import com.feiting.feiapi.interfaceplatform.publishing.service.api.InterfacePublishingLifecycleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 接口发布编排服务实现。
 */
@Service
@Slf4j
public class InterfaceInfoPublishingServiceImpl implements InterfaceInfoPublishingService {

    /**
     * 接口发布生命周期协作服务。
     */
    private final InterfacePublishingLifecycleService publishingLifecycleService;

    /**
     * 发布探测执行服务。
     */
    private final InterfacePublishProbeService interfacePublishProbeService;

    /**
     * 创建接口发布编排服务。
     *
     * @param publishingLifecycleService 接口发布生命周期协作服务
     * @param interfacePublishProbeService 发布探测执行服务
     */
    public InterfaceInfoPublishingServiceImpl(InterfacePublishingLifecycleService publishingLifecycleService,
                                              InterfacePublishProbeService interfacePublishProbeService) {
        this.publishingLifecycleService = publishingLifecycleService;
        this.interfacePublishProbeService = interfacePublishProbeService;
    }

    /**
     * 校验接口、执行发布探测并完成状态切换。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 是否发布成功
     */
    @Override
    public boolean publish(Long interfaceInfoId) {
        InterfacePublishContext publishContext = publishingLifecycleService.startPublishingWithContext(interfaceInfoId);
        try {
            interfacePublishProbeService.probe(publishContext);
            publishingLifecycleService.completePublishing(interfaceInfoId);
            return true;
        } catch (Exception e) {
            rollbackPublishingStatus(interfaceInfoId);
            if (e instanceof BusinessException) {
                throw (BusinessException) e;
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "接口验证失败：" + e.getMessage());
        }
    }

    /**
     * 回滚发布状态，回滚异常只记录日志以保留原始发布异常。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    private void rollbackPublishingStatus(Long interfaceInfoId) {
        try {
            publishingLifecycleService.rollbackPublishing(interfaceInfoId);
        } catch (Exception rollbackException) {
            log.error("接口发布验证失败后回滚状态失败，interfaceInfoId={}", interfaceInfoId, rollbackException);
        }
    }
}
