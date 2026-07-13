package com.feiting.feiapi.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 接口文档参数视图。
 */
@Data
public class InterfaceDocParamVO implements Serializable {

    /**
     * 主键。
     */
    private Long id;

    /**
     * 关联的接口信息 ID。
     */
    private Long interfaceInfoId;

    /**
     * 参数场景，取值包括 HEADER、QUERY、BODY、RESPONSE。
     */
    private String paramScene;

    /**
     * 父级参数 ID。
     */
    private Long parentId;

    /**
     * 参数名称。
     */
    private String name;

    /**
     * 参数类型。
     */
    private String type;

    /**
     * 是否必填。
     */
    private Boolean required;

    /**
     * 是否允许为空。
     */
    private Boolean nullable;

    /**
     * 默认值。
     */
    private String defaultValue;

    /**
     * 示例值。
     */
    private String exampleValue;

    /**
     * 参数说明。
     */
    private String description;

    /**
     * 校验规则展示说明。
     */
    private String validationRule;

    /**
     * 排序值。
     */
    private Integer sortOrder;

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;
}
