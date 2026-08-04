package com.feiting.feiapi.model.vo;

import lombok.Data;

/**
 * 接口发布前检查问题视图对象。
 */
@Data
public class InterfacePublishIssueVO {

    /**
     * 问题分类。
     */
    private String category;

    /**
     * 稳定规则编码。
     */
    private String ruleCode;

    /**
     * 公开字段路径。
     */
    private String field;

    /**
     * 中文问题说明。
     */
    private String message;
}
