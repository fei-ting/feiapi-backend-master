package com.feiting.feiapi.exception;

import com.feiting.feiapi.interfaceplatform.publishing.model.enums.PublishProbeFailureStageEnum;

/**
 * 接口发布探测失败异常兼容类型。
 *
 * <p>实际异常已迁移至发布治理域，本类型保留给历史测试和调用方直接引用。</p>
 */
public class InterfacePublishProbeException
        extends com.feiting.feiapi.interfaceplatform.publishing.exception.InterfacePublishProbeException {

    /**
     * 创建发布探测失败异常。
     *
     * @param stage  探测失败阶段
     * @param reason 安全公开原因
     */
    public InterfacePublishProbeException(PublishProbeFailureStageEnum stage, String reason) {
        super(stage, reason);
    }
}
