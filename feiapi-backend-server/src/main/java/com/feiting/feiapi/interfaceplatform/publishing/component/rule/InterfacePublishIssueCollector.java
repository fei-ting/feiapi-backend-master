package com.feiting.feiapi.interfaceplatform.publishing.component.rule;

import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.interfaceplatform.documentation.model.snapshot.InterfaceDocValidationIssue;
import com.feiting.feiapi.interfaceplatform.publishing.model.enums.InterfacePublishIssueCategoryEnum;
import com.feiting.feiapi.interfaceplatform.publishing.model.vo.InterfacePublishIssueVO;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * 接口发布检查问题收集器。
 */
public class InterfacePublishIssueCollector {

    /**
     * 发布检查问题列表。
     */
    private final List<InterfacePublishIssueVO> issues;

    /**
     * 创建发布检查问题收集器。
     *
     * @param issues 发布检查问题列表
     */
    public InterfacePublishIssueCollector(List<InterfacePublishIssueVO> issues) {
        this.issues = issues;
    }

    /**
     * 要求文本非空。
     *
     * @param category 问题分类
     * @param ruleCode 规则编码
     * @param field    字段路径
     * @param value    字段值
     * @param message  问题说明
     */
    public void requireText(InterfacePublishIssueCategoryEnum category,
                            String ruleCode,
                            String field,
                            String value,
                            String message) {
        if (StringUtils.isBlank(value)) {
            addIssue(category, ruleCode, field, message);
        }
    }

    /**
     * 捕获业务规则异常并转换为检查问题。
     *
     * @param category 问题分类
     * @param ruleCode 规则编码
     * @param field    字段路径
     * @param rule     校验规则
     */
    public void captureRule(InterfacePublishIssueCategoryEnum category,
                            String ruleCode,
                            String field,
                            Runnable rule) {
        try {
            rule.run();
        } catch (BusinessException | IllegalArgumentException exception) {
            addIssue(category, ruleCode, field, safeMessage(exception));
        }
    }

    /**
     * 添加文档域发布问题。
     *
     * @param issue 文档域发布问题
     */
    public void addDocIssue(InterfaceDocValidationIssue issue) {
        if (issue == null) {
            return;
        }
        InterfacePublishIssueVO publishIssue = new InterfacePublishIssueVO();
        publishIssue.setCategory(StringUtils.defaultIfBlank(issue.getCategory(),
                InterfacePublishIssueCategoryEnum.DOCUMENT.name()));
        publishIssue.setRuleCode(issue.getRuleCode());
        publishIssue.setField(issue.getField());
        publishIssue.setMessage(issue.getMessage());
        issues.add(publishIssue);
    }

    /**
     * 添加检查问题。
     *
     * @param category 问题分类
     * @param ruleCode 规则编码
     * @param field    字段路径
     * @param message  问题说明
     */
    public void addIssue(InterfacePublishIssueCategoryEnum category,
                         String ruleCode,
                         String field,
                         String message) {
        InterfacePublishIssueVO issue = new InterfacePublishIssueVO();
        issue.setCategory(category.name());
        issue.setRuleCode(ruleCode);
        issue.setField(field);
        issue.setMessage(message);
        issues.add(issue);
    }

    /**
     * 获取安全错误消息。
     *
     * @param exception 异常
     * @return 安全错误消息
     */
    private String safeMessage(Exception exception) {
        return StringUtils.defaultIfBlank(exception.getMessage(), "规则校验失败");
    }
}
