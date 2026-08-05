package com.feiting.feiapi.interfaceplatform.definition.model.snapshot;

import lombok.Builder;
import lombok.Value;

/**
 * SDK 方法契约快照。
 *
 * <p>该模型描述 SDK 方法是否可用于平台接口发布与探测，不暴露反射调用细节。</p>
 */
@Value
@Builder
public class SdkContractSnapshot {

    /**
     * SDK 方法名。
     */
    String sdkMethodName;

    /**
     * 是否存在已注册的 SDK 方法。
     */
    boolean supported;

    /**
     * SDK 方法是否需要请求参数。
     */
    boolean needParams;

    /**
     * SDK 方法参数数量。
     */
    int parameterCount;

    /**
     * SDK 方法返回类型名称。
     */
    String returnTypeName;
}
