package com.feiting.feiapi.interfaceplatform.definition.model.snapshot;

import lombok.Builder;
import lombok.Value;

/**
 * 运行时请求参数模板快照。
 *
 * <p>该模型用于文档同步和发布检查读取运行时请求参数权威文本。</p>
 */
@Value
@Builder
public class RuntimeRequestTemplate {

    /**
     * 接口信息 ID。
     */
    Long interfaceInfoId;

    /**
     * 请求方法。
     */
    String method;

    /**
     * 运行时请求参数模板。
     */
    String requestParams;
}
