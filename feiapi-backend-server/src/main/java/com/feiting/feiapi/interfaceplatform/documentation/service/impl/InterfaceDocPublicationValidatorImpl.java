package com.feiting.feiapi.interfaceplatform.documentation.service.impl;

import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.interfaceplatform.documentation.component.InterfaceDocBoundaryValidator;
import com.feiting.feiapi.interfaceplatform.documentation.component.InterfaceDocContentSecurityValidator;
import com.feiting.feiapi.interfaceplatform.documentation.model.entity.InterfaceDoc;
import com.feiting.feiapi.interfaceplatform.documentation.model.entity.InterfaceDocErrorCode;
import com.feiting.feiapi.interfaceplatform.documentation.model.entity.InterfaceDocParam;
import com.feiting.feiapi.interfaceplatform.documentation.model.enums.InterfaceDocParamSceneEnum;
import com.feiting.feiapi.interfaceplatform.documentation.model.enums.InterfaceDocStatusEnum;
import com.feiting.feiapi.interfaceplatform.documentation.model.snapshot.InterfaceDocErrorCodeSnapshot;
import com.feiting.feiapi.interfaceplatform.documentation.model.snapshot.InterfaceDocParamSnapshot;
import com.feiting.feiapi.interfaceplatform.documentation.model.snapshot.InterfaceDocPublishSnapshot;
import com.feiting.feiapi.interfaceplatform.documentation.model.snapshot.InterfaceDocValidationIssue;
import com.feiting.feiapi.interfaceplatform.documentation.service.api.InterfaceDocPublicationValidator;
import com.feiting.feiapi.utils.TextSizeUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 接口文档发布校验服务实现。
 *
 * <p>文档域统一维护发布前文档规则，发布域只消费这里产生的稳定问题编码。</p>
 */
@Service
public class InterfaceDocPublicationValidatorImpl implements InterfaceDocPublicationValidator {

    /**
     * 自动同步请求参数使用的待完善说明。
     */
    private static final String GENERATED_PARAM_DESCRIPTION = "由接口运行时参数模板自动生成";

    /**
     * 响应字段最大嵌套深度。
     */
    private static final int MAX_RESPONSE_FIELD_DEPTH = 8;

    /**
     * 支持的文档内容类型。
     */
    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            "application/json", "application/xml", "text/plain", "text/html",
            "application/x-www-form-urlencoded", "multipart/form-data", "application/octet-stream");

    /**
     * 支持的参数类型。
     */
    private static final Set<String> SUPPORTED_PARAM_TYPES = Set.of("string", "number", "boolean", "object", "array");

    /**
     * 文档版本白名单。
     */
    private static final Pattern DOC_VERSION_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    /**
     * 文档边界校验器。
     */
    private final InterfaceDocBoundaryValidator boundaryValidator;

    /**
     * 文档内容安全校验器。
     */
    private final InterfaceDocContentSecurityValidator contentSecurityValidator;

    /**
     * 创建接口文档发布校验服务。
     *
     * @param boundaryValidator       文档边界校验器
     * @param contentSecurityValidator 文档内容安全校验器
     */
    public InterfaceDocPublicationValidatorImpl(InterfaceDocBoundaryValidator boundaryValidator,
                                                InterfaceDocContentSecurityValidator contentSecurityValidator) {
        this.boundaryValidator = boundaryValidator;
        this.contentSecurityValidator = contentSecurityValidator;
    }

    /**
     * 校验接口文档发布快照。
     *
     * @param snapshot 接口文档发布快照
     * @return 文档发布校验问题列表
     */
    @Override
    public List<InterfaceDocValidationIssue> validate(InterfaceDocPublishSnapshot snapshot) {
        List<InterfaceDocValidationIssue> issues = new ArrayList<>();
        if (snapshot == null || snapshot.getDocId() == null) {
            addIssue(issues, "DOCUMENT_REQUIRED", "doc", "接口文档主记录不存在");
            return issues;
        }
        validateDocStatus(snapshot, issues);
        validateDocVersion(snapshot, issues);
        validateContentType(snapshot.getRequestContentType(), "doc.requestContentType",
                "REQUEST_CONTENT_TYPE_INVALID", issues);
        validateContentType(snapshot.getResponseContentType(), "doc.responseContentType",
                "RESPONSE_CONTENT_TYPE_INVALID", issues);
        captureRule(issues, "DOCUMENT_BOUNDARY_INVALID", "doc", () -> boundaryValidator.validatePersistedDoc(
                toDoc(snapshot),
                snapshot.getDocParams().stream().map(this::toParam).collect(Collectors.toList()),
                snapshot.getErrorCodes().stream().map(this::toErrorCode).collect(Collectors.toList())));
        validateDocPublicText(snapshot, issues);
        validateResponseTree(snapshot.getDocParams(), issues);
        validateSuccessExample(snapshot, issues);
        validateErrorCodes(snapshot.getErrorCodes(), issues);
        return issues;
    }

    /**
     * 校验文档状态。
     *
     * @param snapshot 文档快照
     * @param issues   问题列表
     */
    private void validateDocStatus(InterfaceDocPublishSnapshot snapshot, List<InterfaceDocValidationIssue> issues) {
        if (!InterfaceDocStatusEnum.READY.getValue().equals(snapshot.getDocStatus())) {
            addIssue(issues, "DOCUMENT_READY_REQUIRED", "doc.docStatus", "接口文档必须完成维护");
        }
    }

    /**
     * 校验文档版本。
     *
     * @param snapshot 文档快照
     * @param issues   问题列表
     */
    private void validateDocVersion(InterfaceDocPublishSnapshot snapshot, List<InterfaceDocValidationIssue> issues) {
        if (StringUtils.isBlank(snapshot.getDocVersion())) {
            addIssue(issues, "DOC_VERSION_REQUIRED", "doc.docVersion", "文档版本不能为空");
            return;
        }
        String docVersion = TextSizeUtils.stripUnicodeWhitespace(snapshot.getDocVersion());
        if (!DOC_VERSION_PATTERN.matcher(docVersion).matches()) {
            addIssue(issues, "DOC_VERSION_INVALID", "doc.docVersion", "文档版本号格式非法");
        }
    }

    /**
     * 校验内容类型。
     *
     * @param value    内容类型
     * @param field    字段路径
     * @param ruleCode 规则编码
     * @param issues   问题列表
     */
    private void validateContentType(String value,
                                     String field,
                                     String ruleCode,
                                     List<InterfaceDocValidationIssue> issues) {
        String contentType = StringUtils.lowerCase(StringUtils.trimToEmpty(value), Locale.ROOT);
        if (!SUPPORTED_CONTENT_TYPES.contains(contentType)) {
            addIssue(issues, ruleCode, field, "文档内容类型不受支持");
        }
    }

    /**
     * 校验文档公开文本。
     *
     * @param snapshot 文档快照
     * @param issues   问题列表
     */
    private void validateDocPublicText(InterfaceDocPublishSnapshot snapshot, List<InterfaceDocValidationIssue> issues) {
        captureRule(issues, "DOC_SUCCESS_EXAMPLE_UNSAFE", "doc.successExample",
                () -> contentSecurityValidator.validateJsonExample(snapshot.getSuccessExample(), "成功响应示例必须是合法 JSON"));
        captureRule(issues, "DOC_FAIL_EXAMPLE_UNSAFE", "doc.failExample",
                () -> contentSecurityValidator.validateJsonExample(snapshot.getFailExample(), "失败响应示例必须是合法 JSON"));
        captureRule(issues, "DOC_REMARK_UNSAFE", "doc.remark",
                () -> contentSecurityValidator.validateText(snapshot.getRemark()));
        snapshot.getDocParams().stream().filter(Objects::nonNull).forEach(param -> {
            if ("HEADER".equals(param.getParamScene())) {
                addIssue(issues, "HEADER_PARAM_NOT_ALLOWED",
                        "params[" + param.getName() + "]", "结构化文档不允许自定义 Header 参数");
            }
            String description = StringUtils.trimToEmpty(param.getDescription());
            if (StringUtils.isBlank(description) || GENERATED_PARAM_DESCRIPTION.equals(description)) {
                addIssue(issues, "PARAM_DESCRIPTION_REQUIRED",
                        "params[" + param.getName() + "].description", "请求参数和响应字段必须填写有效公开说明");
            }
            captureRule(issues, "PARAM_TEXT_UNSAFE", "params[" + param.getName() + "]", () -> {
                contentSecurityValidator.validateText(param.getName());
                contentSecurityValidator.validateText(param.getDefaultValue());
                contentSecurityValidator.validateText(param.getExampleValue());
                contentSecurityValidator.validateText(param.getDescription());
                contentSecurityValidator.validateValidationRule(param.getValidationRule());
            });
        });
    }

    /**
     * 校验响应字段树。
     *
     * @param docParams 文档参数
     * @param issues    问题列表
     */
    private void validateResponseTree(List<InterfaceDocParamSnapshot> docParams,
                                      List<InterfaceDocValidationIssue> issues) {
        List<InterfaceDocParamSnapshot> responseParams = docParams.stream()
                .filter(param -> InterfaceDocParamSceneEnum.RESPONSE.getValue().equals(param.getParamScene()))
                .collect(Collectors.toList());
        Map<Long, InterfaceDocParamSnapshot> byId = responseParams.stream()
                .filter(param -> param.getId() != null)
                .collect(Collectors.toMap(InterfaceDocParamSnapshot::getId, param -> param, (first, second) -> first));
        Map<Long, List<InterfaceDocParamSnapshot>> childrenMap = responseParams.stream()
                .filter(param -> param.getParentId() != null && param.getParentId() > 0)
                .collect(Collectors.groupingBy(InterfaceDocParamSnapshot::getParentId));
        responseParams.forEach(param -> validateResponseParam(param, byId, childrenMap, issues));
        detectSiblingDuplicate(responseParams, issues);
    }

    /**
     * 校验单个响应字段。
     *
     * @param param       响应字段
     * @param byId        字段映射
     * @param childrenMap 子字段映射
     * @param issues      问题列表
     */
    private void validateResponseParam(InterfaceDocParamSnapshot param,
                                       Map<Long, InterfaceDocParamSnapshot> byId,
                                       Map<Long, List<InterfaceDocParamSnapshot>> childrenMap,
                                       List<InterfaceDocValidationIssue> issues) {
        if (StringUtils.isBlank(param.getName())) {
            addIssue(issues, "RESPONSE_FIELD_NAME_REQUIRED", "responseParams", "响应字段名称不能为空");
        }
        String type = StringUtils.lowerCase(StringUtils.trimToEmpty(param.getType()), Locale.ROOT);
        if (!SUPPORTED_PARAM_TYPES.contains(type)) {
            addIssue(issues, "RESPONSE_FIELD_TYPE_INVALID",
                    "responseParams[" + param.getName() + "].type", "响应字段类型不受支持");
        }
        if (param.getParentId() != null && param.getParentId() > 0 && !byId.containsKey(param.getParentId())) {
            addIssue(issues, "RESPONSE_FIELD_ORPHAN",
                    "responseParams[" + param.getName() + "].parentId", "响应字段父级不存在");
        }
        if (!childrenMap.getOrDefault(param.getId(), List.of()).isEmpty()
                && !Set.of("object", "array").contains(type)) {
            addIssue(issues, "RESPONSE_FIELD_PARENT_TYPE_INVALID",
                    "responseParams[" + param.getName() + "].type", "只有 object 或 array 响应字段可以拥有子字段");
        }
        if (depthOf(param, byId, new HashSet<>()) > MAX_RESPONSE_FIELD_DEPTH) {
            addIssue(issues, "RESPONSE_FIELD_DEPTH_EXCEEDED",
                    "responseParams[" + param.getName() + "]", "响应字段嵌套深度不能超过 8 层");
        }
    }

    /**
     * 计算响应字段深度。
     *
     * @param param   当前字段
     * @param byId    字段映射
     * @param visited 已访问 ID
     * @return 深度
     */
    private int depthOf(InterfaceDocParamSnapshot param,
                        Map<Long, InterfaceDocParamSnapshot> byId,
                        Set<Long> visited) {
        Long id = param.getId();
        if (id != null && !visited.add(id)) {
            return MAX_RESPONSE_FIELD_DEPTH + 1;
        }
        Long parentId = param.getParentId();
        if (parentId == null || parentId <= 0 || !byId.containsKey(parentId)) {
            return 1;
        }
        return 1 + depthOf(byId.get(parentId), byId, visited);
    }

    /**
     * 检测同级响应字段重名。
     *
     * @param responseParams 响应字段
     * @param issues         问题列表
     */
    private void detectSiblingDuplicate(List<InterfaceDocParamSnapshot> responseParams,
                                        List<InterfaceDocValidationIssue> issues) {
        responseParams.stream()
                .collect(Collectors.groupingBy(param -> Objects.toString(param.getParentId(), "root")))
                .values()
                .forEach(siblings -> {
                    Set<String> names = new HashSet<>();
                    siblings.stream()
                            .map(InterfaceDocParamSnapshot::getName)
                            .filter(StringUtils::isNotBlank)
                            .filter(name -> !names.add(name))
                            .forEach(name -> addIssue(issues, "RESPONSE_FIELD_SIBLING_DUPLICATED",
                                    "responseParams[" + name + "]", "同级响应字段名称不能重复"));
                });
    }

    /**
     * 校验成功示例。
     *
     * @param snapshot 文档快照
     * @param issues   问题列表
     */
    private void validateSuccessExample(InterfaceDocPublishSnapshot snapshot, List<InterfaceDocValidationIssue> issues) {
        if (StringUtils.containsIgnoreCase(snapshot.getResponseContentType(), "json")
                && StringUtils.isBlank(snapshot.getSuccessExample())) {
            addIssue(issues, "SUCCESS_EXAMPLE_REQUIRED", "doc.successExample", "JSON 响应必须维护成功示例");
        }
    }

    /**
     * 校验错误码。
     *
     * @param errorCodes 错误码
     * @param issues     问题列表
     */
    private void validateErrorCodes(List<InterfaceDocErrorCodeSnapshot> errorCodes,
                                    List<InterfaceDocValidationIssue> issues) {
        Set<String> normalizedCodes = new HashSet<>();
        errorCodes.forEach(errorCode -> {
            if (StringUtils.isBlank(errorCode.getErrorCode()) || StringUtils.isBlank(errorCode.getErrorMessage())) {
                addIssue(issues, "ERROR_CODE_REQUIRED", "errorCodes", "错误码和错误信息不能为空");
            }
            String normalizedCode = StringUtils.lowerCase(StringUtils.trimToEmpty(errorCode.getErrorCode()), Locale.ROOT);
            if (StringUtils.isNotBlank(normalizedCode) && !normalizedCodes.add(normalizedCode)) {
                addIssue(issues, "ERROR_CODE_DUPLICATED",
                        "errorCodes[" + errorCode.getErrorCode() + "]", "错误码不能重复");
            }
            captureRule(issues, "ERROR_CODE_TEXT_UNSAFE", "errorCodes[" + errorCode.getErrorCode() + "]", () -> {
                contentSecurityValidator.validateText(errorCode.getErrorCode());
                contentSecurityValidator.validateText(errorCode.getErrorMessage());
                contentSecurityValidator.validateText(errorCode.getDescription());
                contentSecurityValidator.validateSolution(errorCode.getSolution());
            });
        });
    }

    /**
     * 捕获业务规则异常并转换为检查问题。
     *
     * @param issues   问题列表
     * @param ruleCode 规则编码
     * @param field    字段路径
     * @param rule     校验规则
     */
    private void captureRule(List<InterfaceDocValidationIssue> issues,
                             String ruleCode,
                             String field,
                             Runnable rule) {
        try {
            rule.run();
        } catch (BusinessException | IllegalArgumentException exception) {
            addIssue(issues, ruleCode, field, StringUtils.defaultIfBlank(exception.getMessage(), "规则校验失败"));
        }
    }

    /**
     * 添加文档发布问题。
     *
     * @param issues   问题列表
     * @param ruleCode 规则编码
     * @param field    字段路径
     * @param message  问题说明
     */
    private void addIssue(List<InterfaceDocValidationIssue> issues,
                          String ruleCode,
                          String field,
                          String message) {
        issues.add(InterfaceDocValidationIssue.builder()
                .category("DOCUMENT")
                .ruleCode(ruleCode)
                .field(field)
                .message(message)
                .build());
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
