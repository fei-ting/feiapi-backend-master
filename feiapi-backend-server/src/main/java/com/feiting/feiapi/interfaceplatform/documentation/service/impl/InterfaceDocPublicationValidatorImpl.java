package com.feiting.feiapi.interfaceplatform.documentation.service.impl;

import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.interfaceplatform.documentation.component.InterfaceDocBoundaryValidator;
import com.feiting.feiapi.interfaceplatform.documentation.model.entity.InterfaceDoc;
import com.feiting.feiapi.interfaceplatform.documentation.model.entity.InterfaceDocErrorCode;
import com.feiting.feiapi.interfaceplatform.documentation.model.entity.InterfaceDocParam;
import com.feiting.feiapi.interfaceplatform.documentation.model.snapshot.InterfaceDocErrorCodeSnapshot;
import com.feiting.feiapi.interfaceplatform.documentation.model.snapshot.InterfaceDocParamSnapshot;
import com.feiting.feiapi.interfaceplatform.documentation.model.snapshot.InterfaceDocPublishSnapshot;
import com.feiting.feiapi.interfaceplatform.documentation.model.snapshot.InterfaceDocValidationIssue;
import com.feiting.feiapi.interfaceplatform.documentation.service.api.InterfaceDocPublicationValidator;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 接口文档发布校验服务实现。
 *
 * <p>当前复用文档域边界校验器，保留既有校验规则和异常消息。</p>
 */
@Service
public class InterfaceDocPublicationValidatorImpl implements InterfaceDocPublicationValidator {

    /**
     * 文档边界校验器。
     */
    private final InterfaceDocBoundaryValidator boundaryValidator;

    /**
     * 创建接口文档发布校验服务。
     *
     * @param boundaryValidator 文档边界校验器
     */
    public InterfaceDocPublicationValidatorImpl(InterfaceDocBoundaryValidator boundaryValidator) {
        this.boundaryValidator = boundaryValidator;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<InterfaceDocValidationIssue> validate(InterfaceDocPublishSnapshot snapshot) {
        if (snapshot == null) {
            return Collections.singletonList(issue("文档快照不能为空"));
        }
        try {
            boundaryValidator.validatePersistedDoc(
                    toDoc(snapshot),
                    snapshot.getDocParams().stream().map(this::toParam).collect(Collectors.toList()),
                    snapshot.getErrorCodes().stream().map(this::toErrorCode).collect(Collectors.toList()));
            return Collections.emptyList();
        } catch (BusinessException exception) {
            return Collections.singletonList(issue(exception.getMessage()));
        }
    }

    /**
     * 构造文档校验问题。
     *
     * @param message 问题说明
     * @return 文档校验问题
     */
    private InterfaceDocValidationIssue issue(String message) {
        return InterfaceDocValidationIssue.builder()
                .category("DOCUMENT")
                .ruleCode("DOCUMENT_BOUNDARY")
                .field("document")
                .message(message)
                .build();
    }

    /**
     * 转换文档主记录。
     *
     * @param snapshot 文档发布快照
     * @return 文档实体
     */
    private InterfaceDoc toDoc(InterfaceDocPublishSnapshot snapshot) {
        InterfaceDoc doc = new InterfaceDoc();
        doc.setId(snapshot.getDocId());
        doc.setInterfaceInfoId(snapshot.getInterfaceInfoId());
        doc.setDocStatus(snapshot.getDocStatus());
        doc.setDocVersion(snapshot.getDocVersion());
        doc.setRequestContentType(snapshot.getRequestContentType());
        doc.setResponseContentType(snapshot.getResponseContentType());
        doc.setSuccessExample(snapshot.getSuccessExample());
        doc.setFailExample(snapshot.getFailExample());
        doc.setRemark(snapshot.getRemark());
        return doc;
    }

    /**
     * 转换文档参数实体。
     *
     * @param snapshot 文档参数快照
     * @return 文档参数实体
     */
    private InterfaceDocParam toParam(InterfaceDocParamSnapshot snapshot) {
        InterfaceDocParam param = new InterfaceDocParam();
        param.setId(snapshot.getId());
        param.setInterfaceInfoId(snapshot.getInterfaceInfoId());
        param.setParamScene(snapshot.getParamScene());
        param.setParentId(snapshot.getParentId());
        param.setName(snapshot.getName());
        param.setType(snapshot.getType());
        param.setRequired(snapshot.getRequired());
        param.setNullable(snapshot.getNullable());
        param.setDefaultValue(snapshot.getDefaultValue());
        param.setExampleValue(snapshot.getExampleValue());
        param.setDescription(snapshot.getDescription());
        param.setValidationRule(snapshot.getValidationRule());
        param.setSortOrder(snapshot.getSortOrder());
        return param;
    }

    /**
     * 转换错误码实体。
     *
     * @param snapshot 错误码快照
     * @return 错误码实体
     */
    private InterfaceDocErrorCode toErrorCode(InterfaceDocErrorCodeSnapshot snapshot) {
        InterfaceDocErrorCode errorCode = new InterfaceDocErrorCode();
        errorCode.setId(snapshot.getId());
        errorCode.setInterfaceInfoId(snapshot.getInterfaceInfoId());
        errorCode.setErrorCode(snapshot.getErrorCode());
        errorCode.setErrorMessage(snapshot.getErrorMessage());
        errorCode.setDescription(snapshot.getDescription());
        errorCode.setSolution(snapshot.getSolution());
        errorCode.setSortOrder(snapshot.getSortOrder());
        return errorCode;
    }
}
