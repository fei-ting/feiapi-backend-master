package com.feiting.feiapi.service;

import com.feiting.feiapi.model.publish.InterfacePublishContext;

/**
 * 接口发布探测执行服务。
 */
public interface InterfacePublishProbeService {

    /**
     * 执行真实 SDK 发布探测并校验响应契约。
     *
     * @param publishContext 发布上下文
     */
    void probe(InterfacePublishContext publishContext);
}
