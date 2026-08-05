package com.feiting.feiapi.interfaceplatform.publishing.component.rule;

import com.feiting.feiapi.interfaceplatform.publishing.model.context.InterfacePublishContext;

/**
 * 接口发布静态检查规则。
 */
public interface InterfacePublishRule {

    /**
     * 执行发布静态检查。
     *
     * @param context   发布上下文
     * @param collector 问题收集器
     */
    void check(InterfacePublishContext context, InterfacePublishIssueCollector collector);
}
