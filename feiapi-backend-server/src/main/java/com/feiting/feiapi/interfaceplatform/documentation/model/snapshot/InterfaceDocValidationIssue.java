package com.feiting.feiapi.interfaceplatform.documentation.model.snapshot;

import lombok.Builder;
import lombok.Value;

/**
 * 接口文档发布校验问题。
 *
 * <p>该模型由文档域产生，发布域后续可转换为既有发布检查问题视图对象。</p>
 */
@Value
@Builder
public class InterfaceDocValidationIssue {

    /**
     * 问题分类。
     */
    String category;

    /**
     * 稳定规则编码。
     */
    String ruleCode;

    /**
     * 公开字段路径。
     */
    String field;

    /**
     * 中文问题说明。
     */
    String message;
}
