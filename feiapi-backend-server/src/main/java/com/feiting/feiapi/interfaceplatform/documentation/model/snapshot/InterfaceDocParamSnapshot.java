package com.feiting.feiapi.interfaceplatform.documentation.model.snapshot;

import lombok.Builder;
import lombok.Value;

/**
 * 接口文档参数快照。
 *
 * <p>用于跨域只读描述请求参数、响应字段和系统 Header 文档。</p>
 */
@Value
@Builder
public class InterfaceDocParamSnapshot {

    /**
     * 文档参数 ID。
     */
    Long id;

    /**
     * 接口信息 ID。
     */
    Long interfaceInfoId;

    /**
     * 参数场景。
     */
    String paramScene;

    /**
     * 父级参数 ID。
     */
    Long parentId;

    /**
     * 参数名称。
     */
    String name;

    /**
     * 参数类型。
     */
    String type;

    /**
     * 是否必填。
     */
    Integer required;

    /**
     * 是否允许为空。
     */
    Integer nullable;

    /**
     * 默认值。
     */
    String defaultValue;

    /**
     * 示例值。
     */
    String exampleValue;

    /**
     * 参数说明。
     */
    String description;

    /**
     * 校验规则展示说明。
     */
    String validationRule;

    /**
     * 排序值。
     */
    Integer sortOrder;
}
