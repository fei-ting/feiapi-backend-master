package com.feiting.feiapi.interfaceplatform.publishing.model.vo;

import lombok.Data;

/**
 * 接口发布探测失败视图对象。
 */
@Data
public class InterfacePublishProbeFailureVO {

    /**
     * 探测失败阶段。
     */
    private String stage;

    /**
     * 安全公开失败原因。
     */
    private String reason;
}
