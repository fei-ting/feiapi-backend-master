package com.feiting.feiapi.interfaceplatform.documentation.service.impl;

import com.feiting.feiapi.interfaceplatform.documentation.model.entity.InterfaceDoc;
import com.feiting.feiapi.interfaceplatform.documentation.model.entity.InterfaceDocErrorCode;
import com.feiting.feiapi.interfaceplatform.documentation.model.entity.InterfaceDocParam;
import com.feiting.feiapi.interfaceplatform.documentation.model.snapshot.InterfaceDocErrorCodeSnapshot;
import com.feiting.feiapi.interfaceplatform.documentation.model.snapshot.InterfaceDocParamSnapshot;
import com.feiting.feiapi.interfaceplatform.documentation.model.snapshot.InterfaceDocPublishSnapshot;
import com.feiting.feiapi.interfaceplatform.documentation.service.api.InterfaceDocPublishReader;
import com.feiting.feiapi.service.InterfaceDocErrorCodeService;
import com.feiting.feiapi.service.InterfaceDocParamService;
import com.feiting.feiapi.service.InterfaceDocService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 接口文档发布快照读取服务实现。
 */
@Service
public class InterfaceDocPublishReaderImpl implements InterfaceDocPublishReader {

    /**
     * 文档主信息服务。
     */
    private final InterfaceDocService interfaceDocService;

    /**
     * 文档参数服务。
     */
    private final InterfaceDocParamService interfaceDocParamService;

    /**
     * 文档错误码服务。
     */
    private final InterfaceDocErrorCodeService interfaceDocErrorCodeService;

    /**
     * 创建接口文档发布快照读取服务。
     *
     * @param interfaceDocService          文档主信息服务
     * @param interfaceDocParamService     文档参数服务
     * @param interfaceDocErrorCodeService 文档错误码服务
     */
    public InterfaceDocPublishReaderImpl(InterfaceDocService interfaceDocService,
                                         InterfaceDocParamService interfaceDocParamService,
                                         InterfaceDocErrorCodeService interfaceDocErrorCodeService) {
        this.interfaceDocService = interfaceDocService;
        this.interfaceDocParamService = interfaceDocParamService;
        this.interfaceDocErrorCodeService = interfaceDocErrorCodeService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InterfaceDocPublishSnapshot getPublishSnapshot(Long interfaceInfoId) {
        InterfaceDoc doc = interfaceDocService.lambdaQuery()
                .eq(InterfaceDoc::getInterfaceInfoId, interfaceInfoId)
                .one();
        List<InterfaceDocParam> params = interfaceDocParamService.lambdaQuery()
                .eq(InterfaceDocParam::getInterfaceInfoId, interfaceInfoId)
                .orderByAsc(InterfaceDocParam::getSortOrder)
                .orderByAsc(InterfaceDocParam::getId)
                .list();
        List<InterfaceDocErrorCode> errorCodes = interfaceDocErrorCodeService.lambdaQuery()
                .eq(InterfaceDocErrorCode::getInterfaceInfoId, interfaceInfoId)
                .orderByAsc(InterfaceDocErrorCode::getSortOrder)
                .orderByAsc(InterfaceDocErrorCode::getId)
                .list();
        return InterfaceDocPublishSnapshot.builder()
                .interfaceInfoId(interfaceInfoId)
                .docId(doc == null ? null : doc.getId())
                .docStatus(doc == null ? null : doc.getDocStatus())
                .docVersion(doc == null ? null : doc.getDocVersion())
                .requestContentType(doc == null ? null : doc.getRequestContentType())
                .responseContentType(doc == null ? null : doc.getResponseContentType())
                .successExample(doc == null ? null : doc.getSuccessExample())
                .failExample(doc == null ? null : doc.getFailExample())
                .remark(doc == null ? null : doc.getRemark())
                .docParams((params == null ? Collections.<InterfaceDocParam>emptyList() : params).stream()
                        .map(this::toParamSnapshot)
                        .collect(Collectors.toList()))
                .errorCodes((errorCodes == null ? Collections.<InterfaceDocErrorCode>emptyList() : errorCodes).stream()
                        .map(this::toErrorCodeSnapshot)
                        .collect(Collectors.toList()))
                .build();
    }

    /**
     * 转换文档参数快照。
     *
     * @param param 文档参数实体
     * @return 文档参数快照
     */
    private InterfaceDocParamSnapshot toParamSnapshot(InterfaceDocParam param) {
        return InterfaceDocParamSnapshot.builder()
                .id(param.getId())
                .interfaceInfoId(param.getInterfaceInfoId())
                .paramScene(param.getParamScene())
                .parentId(param.getParentId())
                .name(param.getName())
                .type(param.getType())
                .required(param.getRequired())
                .nullable(param.getNullable())
                .exampleValue(param.getExampleValue())
                .description(param.getDescription())
                .validationRule(param.getValidationRule())
                .sortOrder(param.getSortOrder())
                .build();
    }

    /**
     * 转换错误码快照。
     *
     * @param errorCode 错误码实体
     * @return 错误码快照
     */
    private InterfaceDocErrorCodeSnapshot toErrorCodeSnapshot(InterfaceDocErrorCode errorCode) {
        return InterfaceDocErrorCodeSnapshot.builder()
                .id(errorCode.getId())
                .interfaceInfoId(errorCode.getInterfaceInfoId())
                .errorCode(errorCode.getErrorCode())
                .errorMessage(errorCode.getErrorMessage())
                .description(errorCode.getDescription())
                .solution(errorCode.getSolution())
                .sortOrder(errorCode.getSortOrder())
                .build();
    }
}
