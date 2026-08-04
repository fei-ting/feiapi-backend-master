package com.feiting.feiapi.exception;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.model.enums.PublishProbeFailureStageEnum;

/**
 * 接口发布探测失败异常。
 */
public class InterfacePublishProbeException extends BusinessException {

    /**
     * 探测失败阶段。
     */
    private final PublishProbeFailureStageEnum stage;

    /**
     * 安全公开失败原因。
     */
    private final String reason;

    /**
     * 创建发布探测失败异常。
     *
     * @param stage  探测失败阶段
     * @param reason 安全公开原因
     */
    public InterfacePublishProbeException(PublishProbeFailureStageEnum stage, String reason) {
        super(ErrorCode.PUBLISH_PROBE_FAILED, "发布探测失败[" + stage.name() + "]：" + reason);
        this.stage = stage;
        this.reason = reason;
    }

    /**
     * 获取探测失败阶段。
     *
     * @return 探测失败阶段
     */
    public PublishProbeFailureStageEnum getStage() {
        return stage;
    }

    /**
     * 获取安全公开失败原因。
     *
     * @return 安全公开失败原因
     */
    public String getReason() {
        return reason;
    }
}
