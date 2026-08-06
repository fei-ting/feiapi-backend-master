package com.feiting.feiapi.interfaceplatform.documentation.model.snapshot;

import lombok.Builder;
import lombok.Value;

/**
 * 接口文档错误码快照。
 *
 * <p>用于跨域只读描述接口级公开错误码。</p>
 */
@Value
@Builder
public class InterfaceDocErrorCodeSnapshot {

    /**
     * 文档错误码 ID。
     */
    Long id;

    /**
     * 接口信息 ID。
     */
    Long interfaceInfoId;

    /**
     * 错误码。
     */
    String errorCode;

    /**
     * 错误信息。
     */
    String errorMessage;

    /**
     * 错误说明。
     */
    String description;

    /**
     * 解决建议。
     */
    String solution;

    /**
     * 排序值。
     */
    Integer sortOrder;
}
