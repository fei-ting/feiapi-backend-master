package com.feiting.feiapi.service.impl;

import com.feiting.feiapi.interfaceplatform.definition.component.SdkMethodRegistry;
import com.feiting.feiapi.interfaceplatform.publishing.component.InterfaceProbeResponseValidator;
import com.feiting.feiapiclientsdk.client.FeiApiClient;

/**
 * 接口发布探测执行服务兼容实现。
 *
 * <p>实际 Spring Bean 已迁移至发布治理域，本类仅用于兼容旧测试和直接构造场景。</p>
 */
public class InterfacePublishProbeServiceImpl
        extends com.feiting.feiapi.interfaceplatform.publishing.service.impl.InterfacePublishProbeServiceImpl {

    /**
     * 创建接口发布探测兼容实现。
     *
     * @param sdkMethodRegistry SDK 方法注册器
     * @param feiApiClient      平台 SDK 客户端
     * @param responseValidator 探测响应契约校验器
     */
    public InterfacePublishProbeServiceImpl(SdkMethodRegistry sdkMethodRegistry,
                                            FeiApiClient feiApiClient,
                                            InterfaceProbeResponseValidator responseValidator) {
        super(sdkMethodRegistry, feiApiClient, responseValidator);
    }
}
