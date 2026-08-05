package com.feiting.feiapi.interfaceplatform.documentation.service.api;

import com.feiting.feiapi.interfaceplatform.documentation.model.snapshot.InterfaceDocPublishSnapshot;
import com.feiting.feiapi.interfaceplatform.documentation.model.snapshot.InterfaceDocValidationIssue;

import java.util.List;

/**
 * 接口文档发布校验服务。
 *
 * <p>用于由文档域统一维护文档发布规则，发布域仅消费校验结果。</p>
 */
public interface InterfaceDocPublicationValidator {

    /**
     * 校验接口文档发布快照。
     *
     * @param snapshot 接口文档发布快照
     * @return 文档发布校验问题列表
     */
    List<InterfaceDocValidationIssue> validate(InterfaceDocPublishSnapshot snapshot);
}
