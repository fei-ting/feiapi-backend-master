package com.feiting.feiapi.service.impl;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.component.SdkMethodRegistry;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.service.InterfaceInfoLifecycleService;
import com.feiting.feiapi.service.InterfaceInfoPublishingService;
import com.feiting.feiapiclientsdk.client.FeiApiClient;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 接口发布编排服务实现。
 */
@Service
@Slf4j
public class InterfaceInfoPublishingServiceImpl implements InterfaceInfoPublishingService {

    /**
     * 接口生命周期服务。
     */
    private final InterfaceInfoLifecycleService interfaceInfoLifecycleService;

    /**
     * 平台 SDK 客户端。
     */
    private final FeiApiClient feiApiClient;

    /**
     * SDK 方法注册器。
     */
    private final SdkMethodRegistry sdkMethodRegistry;

    /**
     * 创建接口发布编排服务。
     *
     * @param interfaceInfoLifecycleService 接口生命周期服务
     * @param feiApiClient                  平台 SDK 客户端
     * @param sdkMethodRegistry             SDK 方法注册器
     */
    public InterfaceInfoPublishingServiceImpl(InterfaceInfoLifecycleService interfaceInfoLifecycleService,
                                              FeiApiClient feiApiClient,
                                              SdkMethodRegistry sdkMethodRegistry) {
        this.interfaceInfoLifecycleService = interfaceInfoLifecycleService;
        this.feiApiClient = feiApiClient;
        this.sdkMethodRegistry = sdkMethodRegistry;
    }

    /**
     * 校验接口、执行发布探测并完成状态切换。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 是否发布成功
     */
    @Override
    public boolean publish(Long interfaceInfoId) {
        InterfaceInfo publishingInterface = interfaceInfoLifecycleService.startPublishing(interfaceInfoId);
        try {
            feiApiClient.enableProbeMode();
            Object invokeResult = sdkMethodRegistry.invoke(
                    feiApiClient,
                    publishingInterface.getSdkMethodName(),
                    publishingInterface.getRequestParams());
            if (invokeResult == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "接口验证失败");
            }
            interfaceInfoLifecycleService.completePublishing(interfaceInfoId);
            return true;
        } catch (Exception e) {
            rollbackPublishingStatus(interfaceInfoId);
            if (e instanceof BusinessException) {
                throw (BusinessException) e;
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "接口验证失败：" + e.getMessage());
        } finally {
            feiApiClient.disableProbeMode();
        }
    }

    /**
     * 回滚发布状态，回滚异常只记录日志以保留原始发布异常。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    private void rollbackPublishingStatus(Long interfaceInfoId) {
        try {
            interfaceInfoLifecycleService.rollbackPublishing(interfaceInfoId);
        } catch (Exception rollbackException) {
            log.error("接口发布验证失败后回滚状态失败，interfaceInfoId={}", interfaceInfoId, rollbackException);
        }
    }
}
