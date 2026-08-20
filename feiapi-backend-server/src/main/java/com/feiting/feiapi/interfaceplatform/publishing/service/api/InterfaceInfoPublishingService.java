package com.feiting.feiapi.interfaceplatform.publishing.service.api;

/**
 * 接口发布编排服务。
 */
public interface InterfaceInfoPublishingService {

    /**
     * 校验接口、执行发布探测并完成状态切换。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 是否发布成功
     */
    boolean publish(Long interfaceInfoId);

    /**
     * 使用指定管理员凭证校验接口、执行发布探测并完成状态切换。
     *
     * @param interfaceInfoId 接口信息 ID
     * @param operatorId      当前登录管理员 ID
     * @return 是否发布成功
     */
    boolean publish(Long interfaceInfoId, Long operatorId);
}
