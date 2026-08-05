package com.feiting.feiapi.interfaceplatform.publishing.component.rule;

import com.feiting.feiapi.interfaceplatform.documentation.service.api.InterfaceDocPublicationValidator;
import com.feiting.feiapi.interfaceplatform.publishing.model.context.InterfacePublishContext;
import org.springframework.stereotype.Component;

/**
 * 接口文档发布规则。
 */
@Component
public class DocumentPublishRule implements InterfacePublishRule {

    /**
     * 文档域发布校验服务。
     */
    private final InterfaceDocPublicationValidator publicationValidator;

    /**
     * 创建接口文档发布规则。
     *
     * @param publicationValidator 文档域发布校验服务
     */
    public DocumentPublishRule(InterfaceDocPublicationValidator publicationValidator) {
        this.publicationValidator = publicationValidator;
    }

    /**
     * 执行接口文档发布检查。
     *
     * @param context   发布上下文
     * @param collector 问题收集器
     */
    @Override
    public void check(InterfacePublishContext context, InterfacePublishIssueCollector collector) {
        publicationValidator.validate(context.getInterfaceDoc()).forEach(collector::addDocIssue);
    }
}
