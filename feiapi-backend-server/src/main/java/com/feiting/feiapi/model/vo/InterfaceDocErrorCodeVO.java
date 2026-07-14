package com.feiting.feiapi.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;

/**
 * 接口文档错误码视图。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InterfaceDocErrorCodeVO implements Serializable {

    /**
     * 主键。
     */
    private Long id;

    /**
     * 关联的接口信息 ID。
     */
    private Long interfaceInfoId;

    /**
     * 错误码。
     */
    private String errorCode;

    /**
     * 错误信息。
     */
    private String errorMessage;

    /**
     * 错误说明。
     */
    private String description;

    /**
     * 解决建议。
     */
    private String solution;

    /**
     * 排序值。
     */
    private Integer sortOrder;

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;
}
