package com.feiting.feiapi.service.impl;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.interfaceplatform.documentation.component.InterfaceDocBoundaryValidator;
import com.feiting.feiapi.interfaceplatform.documentation.component.InterfaceDocContentSecurityValidator;
import com.feiting.feiapi.interfaceplatform.documentation.component.InterfaceDocCurlExampleGenerator;
import com.feiting.feiapi.interfaceplatform.documentation.component.InterfaceDocJavaSdkExampleGenerator;
import com.feiting.feiapi.component.InterfaceProbeRequestBuilder;
import com.feiting.feiapi.config.InterfaceTargetHostProperties;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.exception.InterfacePublishCheckException;
import com.feiting.feiapi.interfaceplatform.definition.component.RuntimeRequestParamTemplateValidator;
import com.feiting.feiapi.interfaceplatform.definition.component.SdkMethodRegistry;
import com.feiting.feiapi.interfaceplatform.documentation.model.snapshot.InterfaceDocErrorCodeSnapshot;
import com.feiting.feiapi.interfaceplatform.documentation.model.snapshot.InterfaceDocParamSnapshot;
import com.feiting.feiapi.interfaceplatform.documentation.model.snapshot.InterfaceDocPublishSnapshot;
import com.feiting.feiapi.interfaceplatform.documentation.service.api.InterfaceDocPublishReader;
import com.feiting.feiapi.mapper.InterfaceInfoMapper;
import com.feiting.feiapi.interfaceplatform.documentation.model.entity.InterfaceDoc;
import com.feiting.feiapi.interfaceplatform.documentation.model.entity.InterfaceDocErrorCode;
import com.feiting.feiapi.interfaceplatform.documentation.model.entity.InterfaceDocParam;
import com.feiting.feiapi.interfaceplatform.documentation.model.enums.InterfaceDocParamSceneEnum;
import com.feiting.feiapi.interfaceplatform.documentation.model.enums.InterfaceDocStatusEnum;
import com.feiting.feiapi.model.enums.InterfacePublishIssueCategoryEnum;
import com.feiting.feiapi.model.publish.InterfacePublishContext;
import com.feiting.feiapi.interfaceplatform.documentation.model.vo.InterfaceDocDetailVO;
import com.feiting.feiapi.interfaceplatform.documentation.model.vo.InterfaceDocParamVO;
import com.feiting.feiapi.model.vo.InterfacePublishCheckVO;
import com.feiting.feiapi.model.vo.InterfacePublishIssueVO;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapi.service.InterfacePublishCheckService;
import com.feiting.feiapi.service.InterfaceQuotaConfigService;
import com.feiting.feiapi.service.UserService;
import com.feiting.feiapi.utils.TextSizeUtils;
import com.feiting.feiapiclientsdk.FeiapiClientProperties;
import com.feiting.feiapiclientsdk.annotation.SdkInvoke;
import com.feiting.feiapiclientsdk.model.ProbeStrategy;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.entity.InterfaceQuotaConfig;
import com.feiting.feiapicommon.model.entity.User;
import com.feiting.feiapicommon.model.enums.InterfaceInfoMethodEnum;
import com.feiting.feiapicommon.model.enums.InterfaceQuotaTypeEnum;
import com.feiting.feiapicommon.utils.InterfaceTargetHostValidator;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 接口发布前静态检查服务实现。
 */
@Service
public class InterfacePublishCheckServiceImpl implements InterfacePublishCheckService {

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
     * 接口名称最大长度。
     */
    private static final int MAX_INTERFACE_NAME_LENGTH = 50;

    /**
     * SDK 方法名最大长度。
     */
    private static final int MAX_SDK_METHOD_NAME_LENGTH = 128;

    /**
     * 接口描述、展示地址、路径和目标地址最大长度。
     */
    private static final int MAX_INTERFACE_TEXT_LENGTH = 512;

    /**
     * 请求参数、请求头和响应头最大长度。
     */
    private static final int MAX_INTERFACE_PAYLOAD_LENGTH = 65535;

    /**
     * 请求方法最大长度。
     */
    private static final int MAX_INTERFACE_METHOD_LENGTH = 16;

    /**
     * 配额类型最大长度。
     */
    private static final int MAX_QUOTA_TYPE_LENGTH = 32;

    /**
     * 接口信息服务。
     */
    private final InterfaceInfoService interfaceInfoService;

    /**
     * 接口信息数据访问对象。
     */
    private final InterfaceInfoMapper interfaceInfoMapper;

    /**
     * 接口文档服务。
     */
    private final InterfaceDocPublishReader interfaceDocPublishReader;

    /**
     * 接口配额配置服务。
     */
    private final InterfaceQuotaConfigService interfaceQuotaConfigService;

    /**
     * 用户服务。
     */
    private final UserService userService;

    /**
     * SDK 方法注册器。
     */
    private final SdkMethodRegistry sdkMethodRegistry;

    /**
     * SDK 客户端配置。
     */
    private final FeiapiClientProperties clientProperties;

    /**
     * 真实目标地址白名单配置。
     */
    private final InterfaceTargetHostProperties targetHostProperties;

    /**
     * 运行时参数模板校验器。
     */
    private final RuntimeRequestParamTemplateValidator runtimeTemplateValidator;

    /**
     * 文档边界校验器。
     */
    private final InterfaceDocBoundaryValidator boundaryValidator;

    /**
     * 文档内容安全校验器。
     */
    private final InterfaceDocContentSecurityValidator contentSecurityValidator;

    /**
     * Java SDK 示例生成器。
     */
    private final InterfaceDocJavaSdkExampleGenerator javaSdkExampleGenerator;

    /**
     * curl 示例生成器。
     */
    private final InterfaceDocCurlExampleGenerator curlExampleGenerator;

    /**
     * 探测请求构造器。
     */
    private final InterfaceProbeRequestBuilder probeRequestBuilder;

    /**
     * 创建接口发布前静态检查服务实现。
     */
    public InterfacePublishCheckServiceImpl(InterfaceInfoService interfaceInfoService,
                                            InterfaceInfoMapper interfaceInfoMapper,
                                            InterfaceDocPublishReader interfaceDocPublishReader,
                                            InterfaceQuotaConfigService interfaceQuotaConfigService,
                                            UserService userService,
                                            SdkMethodRegistry sdkMethodRegistry,
                                            FeiapiClientProperties clientProperties,
                                            InterfaceTargetHostProperties targetHostProperties,
                                            RuntimeRequestParamTemplateValidator runtimeTemplateValidator,
                                            InterfaceDocBoundaryValidator boundaryValidator,
                                            InterfaceDocContentSecurityValidator contentSecurityValidator,
                                            InterfaceDocJavaSdkExampleGenerator javaSdkExampleGenerator,
                                            InterfaceDocCurlExampleGenerator curlExampleGenerator,
                                            InterfaceProbeRequestBuilder probeRequestBuilder) {
        this.interfaceInfoService = interfaceInfoService;
        this.interfaceInfoMapper = interfaceInfoMapper;
        this.interfaceDocPublishReader = interfaceDocPublishReader;
        this.interfaceQuotaConfigService = interfaceQuotaConfigService;
        this.userService = userService;
        this.sdkMethodRegistry = sdkMethodRegistry;
        this.clientProperties = clientProperties;
        this.targetHostProperties = targetHostProperties;
        this.runtimeTemplateValidator = runtimeTemplateValidator;
        this.boundaryValidator = boundaryValidator;
        this.contentSecurityValidator = contentSecurityValidator;
        this.javaSdkExampleGenerator = javaSdkExampleGenerator;
        this.curlExampleGenerator = curlExampleGenerator;
        this.probeRequestBuilder = probeRequestBuilder;
    }

    /**
     * 执行管理员只读发布前检查。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 发布前检查结果
     */
    @Override
    @Transactional(readOnly = true)
    public InterfacePublishCheckVO check(Long interfaceInfoId) {
        validateInterfaceInfoId(interfaceInfoId);
        InterfaceInfo interfaceInfo = interfaceInfoMapper.selectById(interfaceInfoId);
        if (interfaceInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        InterfacePublishContext context = buildSnapshot(interfaceInfo);
        return doCheck(context);
    }

    /**
     * 基于已锁定的接口快照构造发布上下文并校验静态门禁。
     *
     * @param lockedInterfaceInfo 已在事务中锁定的接口主记录
     * @return 发布上下文
     */
    @Override
    public InterfacePublishContext buildContextForPublish(InterfaceInfo lockedInterfaceInfo) {
        if (lockedInterfaceInfo == null || lockedInterfaceInfo.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        InterfacePublishContext context = buildSnapshot(lockedInterfaceInfo);
        InterfacePublishCheckVO checkVO = doCheck(context);
        if (!checkVO.isPassed()) {
            throw new InterfacePublishCheckException(checkVO.getIssues());
        }
        context.setProbeRequestParams(probeRequestBuilder.build(context));
        return context;
    }

    /**
     * 构造数据库发布快照。
     *
     * @param interfaceInfo 接口主记录
     * @return 发布上下文
     */
    private InterfacePublishContext buildSnapshot(InterfaceInfo interfaceInfo) {
        InterfacePublishContext context = new InterfacePublishContext();
        context.setInterfaceInfo(interfaceInfo);
        InterfaceDocPublishSnapshot docSnapshot = interfaceDocPublishReader.getPublishSnapshot(interfaceInfo.getId());
        context.setInterfaceDoc(toInterfaceDoc(docSnapshot));
        context.setDocParams(toInterfaceDocParams(docSnapshot));
        context.setErrorCodes(toInterfaceDocErrorCodes(docSnapshot));
        context.setSdkMethod(sdkMethodRegistry.getMethodMap().get(StringUtils.trimToEmpty(interfaceInfo.getSdkMethodName())));
        return context;
    }

    /**
     * 将文档发布快照转换为既有发布上下文文档实体。
     *
     * @param snapshot 文档发布快照
     * @return 文档实体
     */
    private InterfaceDoc toInterfaceDoc(InterfaceDocPublishSnapshot snapshot) {
        if (snapshot == null || snapshot.getDocId() == null) {
            return null;
        }
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
     * 将文档参数快照转换为既有发布上下文参数实体。
     *
     * @param snapshot 文档发布快照
     * @return 文档参数实体列表
     */
    private List<InterfaceDocParam> toInterfaceDocParams(InterfaceDocPublishSnapshot snapshot) {
        if (snapshot == null || snapshot.getDocParams() == null) {
            return new ArrayList<>();
        }
        return snapshot.getDocParams().stream()
                .map(this::toInterfaceDocParam)
                .collect(Collectors.toList());
    }

    /**
     * 将文档错误码快照转换为既有发布上下文错误码实体。
     *
     * @param snapshot 文档发布快照
     * @return 文档错误码实体列表
     */
    private List<InterfaceDocErrorCode> toInterfaceDocErrorCodes(InterfaceDocPublishSnapshot snapshot) {
        if (snapshot == null || snapshot.getErrorCodes() == null) {
            return new ArrayList<>();
        }
        return snapshot.getErrorCodes().stream()
                .map(this::toInterfaceDocErrorCode)
                .collect(Collectors.toList());
    }

    /**
     * 转换文档参数快照。
     *
     * @param snapshot 文档参数快照
     * @return 文档参数实体
     */
    private InterfaceDocParam toInterfaceDocParam(InterfaceDocParamSnapshot snapshot) {
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
     * 转换错误码快照。
     *
     * @param snapshot 错误码快照
     * @return 错误码实体
     */
    private InterfaceDocErrorCode toInterfaceDocErrorCode(InterfaceDocErrorCodeSnapshot snapshot) {
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

    /**
     * 执行全部静态规则并返回聚合结果。
     *
     * @param context 发布上下文
     * @return 检查结果
     */
    private InterfacePublishCheckVO doCheck(InterfacePublishContext context) {
        List<InterfacePublishIssueVO> issues = new ArrayList<>();
        checkInterfaceConfig(context, issues);
        checkSdkContract(context, issues);
        checkRuntimeTemplate(context, issues);
        checkDocument(context, issues);
        checkCallExample(context, issues);
        checkProbeRequest(context, issues);

        List<InterfacePublishIssueVO> sortedIssues = issues.stream()
                .collect(Collectors.toMap(this::issueKey, issue -> issue, (first, second) -> first, LinkedHashMap::new))
                .values()
                .stream()
                .sorted(Comparator.comparing(InterfacePublishIssueVO::getCategory)
                        .thenComparing(issue -> StringUtils.defaultString(issue.getField()))
                        .thenComparing(InterfacePublishIssueVO::getRuleCode))
                .collect(Collectors.toList());
        InterfacePublishCheckVO checkVO = new InterfacePublishCheckVO();
        checkVO.setIssues(sortedIssues);
        checkVO.setPassed(sortedIssues.isEmpty());
        return checkVO;
    }

    /**
     * 检查接口运行时配置。
     *
     * @param context 发布上下文
     * @param issues  问题列表
     */
    private void checkInterfaceConfig(InterfacePublishContext context, List<InterfacePublishIssueVO> issues) {
        InterfaceInfo info = context.getInterfaceInfo();
        requireText(issues, InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "INTERFACE_NAME_REQUIRED",
                "interfaceInfo.name", info.getName(), "接口名称不能为空");
        requireText(issues, InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "INTERFACE_DESCRIPTION_REQUIRED",
                "interfaceInfo.description", info.getDescription(), "接口描述不能为空");
        requireText(issues, InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "INTERFACE_METHOD_REQUIRED",
                "interfaceInfo.method", info.getMethod(), "请求方法不能为空");
        requireText(issues, InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "INTERFACE_PATH_REQUIRED",
                "interfaceInfo.path", info.getPath(), "网关路径不能为空");
        requireText(issues, InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "INTERFACE_TARGET_HOST_REQUIRED",
                "interfaceInfo.targetHost", info.getTargetHost(), "真实后端地址不能为空");
        requireText(issues, InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "SDK_METHOD_REQUIRED",
                "interfaceInfo.sdkMethodName", info.getSdkMethodName(), "SDK 方法名不能为空");
        requireText(issues, InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "QUOTA_TYPE_REQUIRED",
                "interfaceInfo.quotaType", info.getQuotaType(), "配额类型不能为空");
        if (StringUtils.isNotBlank(info.getMethod()) && !InterfaceInfoMethodEnum.isValid(info.getMethod())) {
            addIssue(issues, InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "INTERFACE_METHOD_UNSUPPORTED",
                    "interfaceInfo.method", "请求方法不在平台白名单内");
        }
        validateInterfaceTextBoundary(info, issues);
        validatePath(info, issues);
        validateTargetHost(info, issues);
        validateQuotaType(info, issues);
        validateDisplayUrl(info, issues);
        validateUniquePathAndMethod(info, issues);
        Stream.of(info.getName(), info.getDescription(), info.getPath(), info.getUrl(), info.getTargetHost(),
                        info.getSdkMethodName(), info.getQuotaType(), info.getRequestParams(),
                        info.getRequestHeader(), info.getResponseHeader(), info.getMethod())
                .forEach(text -> captureRule(issues, InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG,
                        "INTERFACE_TEXT_UNSAFE", "interfaceInfo", () -> contentSecurityValidator.validateText(text)));
    }

    /**
     * 检查 SDK 契约和探测凭据。
     *
     * @param context 发布上下文
     * @param issues  问题列表
     */
    private void checkSdkContract(InterfacePublishContext context, List<InterfacePublishIssueVO> issues) {
        InterfaceInfo info = context.getInterfaceInfo();
        Method method = context.getSdkMethod();
        if (StringUtils.isBlank(info.getSdkMethodName()) || method == null) {
            addIssue(issues, InterfacePublishIssueCategoryEnum.SDK, "SDK_METHOD_NOT_FOUND",
                    "interfaceInfo.sdkMethodName", "SDK 方法不存在或未注册");
        } else {
            SdkInvoke sdkInvoke = method.getAnnotation(SdkInvoke.class);
            if (sdkInvoke == null) {
                addIssue(issues, InterfacePublishIssueCategoryEnum.SDK, "SDK_INVOKE_ANNOTATION_REQUIRED",
                        "interfaceInfo.sdkMethodName", "SDK 方法缺少调用注解");
            } else {
                boolean signatureValid = sdkInvoke.needParams()
                        ? method.getParameterCount() == 1 && String.class.equals(method.getParameterTypes()[0])
                        : method.getParameterCount() == 0;
                if (!signatureValid || !String.class.equals(method.getReturnType())) {
                    addIssue(issues, InterfacePublishIssueCategoryEnum.SDK, "SDK_METHOD_SIGNATURE_INVALID",
                            "interfaceInfo.sdkMethodName", "SDK 方法签名必须与参数契约一致且返回 String");
                }
                if (ProbeStrategy.UNSPECIFIED.equals(sdkInvoke.probeStrategy())) {
                    addIssue(issues, InterfacePublishIssueCategoryEnum.SDK, "SDK_PROBE_STRATEGY_REQUIRED",
                            "interfaceInfo.sdkMethodName", "SDK 方法必须显式声明安全探测策略");
                }
            }
        }
        validateProbeCredentials(issues);
    }

    /**
     * 检查运行时请求参数模板。
     *
     * @param context 发布上下文
     * @param issues  问题列表
     */
    private void checkRuntimeTemplate(InterfacePublishContext context, List<InterfacePublishIssueVO> issues) {
        InterfaceInfo info = context.getInterfaceInfo();
        Method method = context.getSdkMethod();
        SdkInvoke sdkInvoke = method == null ? null : method.getAnnotation(SdkInvoke.class);
        boolean needParams = sdkInvoke != null && sdkInvoke.needParams();
        if (needParams && StringUtils.isBlank(info.getRequestParams())) {
            addIssue(issues, InterfacePublishIssueCategoryEnum.RUNTIME_TEMPLATE, "RUNTIME_TEMPLATE_REQUIRED",
                    "interfaceInfo.requestParams", "SDK 方法需要参数时运行时模板不能为空");
        }
        if (!needParams && StringUtils.isNotBlank(info.getRequestParams())) {
            addIssue(issues, InterfacePublishIssueCategoryEnum.RUNTIME_TEMPLATE, "RUNTIME_TEMPLATE_MUST_BE_EMPTY",
                    "interfaceInfo.requestParams", "SDK 方法不需要参数时运行时模板必须为空");
        }
        captureRule(issues, InterfacePublishIssueCategoryEnum.RUNTIME_TEMPLATE, "RUNTIME_TEMPLATE_INVALID",
                "interfaceInfo.requestParams", () -> runtimeTemplateValidator.validate(info.getRequestParams()));
        validateRuntimeAndDocRequestParamConsistency(context, issues);
    }

    /**
     * 检查结构化文档。
     *
     * @param context 发布上下文
     * @param issues  问题列表
     */
    private void checkDocument(InterfacePublishContext context, List<InterfacePublishIssueVO> issues) {
        InterfaceDoc doc = context.getInterfaceDoc();
        if (doc == null) {
            addIssue(issues, InterfacePublishIssueCategoryEnum.DOCUMENT, "DOCUMENT_REQUIRED",
                    "doc", "接口文档主记录不存在");
            return;
        }
        if (!InterfaceDocStatusEnum.READY.getValue().equals(doc.getDocStatus())) {
            addIssue(issues, InterfacePublishIssueCategoryEnum.DOCUMENT, "DOCUMENT_READY_REQUIRED",
                    "doc.docStatus", "接口文档必须完成维护");
        }
        if (StringUtils.isBlank(doc.getDocVersion())) {
            addIssue(issues, InterfacePublishIssueCategoryEnum.DOCUMENT, "DOC_VERSION_REQUIRED",
                    "doc.docVersion", "文档版本不能为空");
        } else {
            validateDocVersion(doc, issues);
        }
        validateContentType(doc.getRequestContentType(), "doc.requestContentType",
                "REQUEST_CONTENT_TYPE_INVALID", issues);
        validateContentType(doc.getResponseContentType(), "doc.responseContentType",
                "RESPONSE_CONTENT_TYPE_INVALID", issues);
        captureRule(issues, InterfacePublishIssueCategoryEnum.DOCUMENT, "DOCUMENT_BOUNDARY_INVALID",
                "doc", () -> boundaryValidator.validatePersistedDoc(doc, context.getDocParams(), context.getErrorCodes()));
        validateDocPublicText(context, issues);
        validateResponseTree(context.getDocParams(), issues);
        validateSuccessExample(doc, issues);
        validateErrorCodes(context.getErrorCodes(), issues);
    }

    /**
     * 检查调用示例。
     *
     * @param context 发布上下文
     * @param issues  问题列表
     */
    private void checkCallExample(InterfacePublishContext context, List<InterfacePublishIssueVO> issues) {
        captureRule(issues, InterfacePublishIssueCategoryEnum.CALL_EXAMPLE, "JAVA_SDK_EXAMPLE_INVALID",
                "example.javaSdk", () -> javaSdkExampleGenerator.generate(context.getInterfaceInfo(),
                        toRequestParamVOs(context.getDocParams())));
        captureRule(issues, InterfacePublishIssueCategoryEnum.CALL_EXAMPLE, "CURL_EXAMPLE_INVALID",
                "example.curl", () -> curlExampleGenerator.generate(context.getInterfaceInfo(), toDetailVO(context)));
    }

    /**
     * 检查探测请求能否构造。
     *
     * @param context 发布上下文
     * @param issues  问题列表
     */
    private void checkProbeRequest(InterfacePublishContext context, List<InterfacePublishIssueVO> issues) {
        Method method = context.getSdkMethod();
        if (method == null || method.getAnnotation(SdkInvoke.class) == null) {
            return;
        }
        captureRule(issues, InterfacePublishIssueCategoryEnum.RUNTIME_TEMPLATE, "PROBE_REQUEST_BUILD_FAILED",
                "interfaceInfo.requestParams", () -> probeRequestBuilder.build(context));
    }

    /**
     * 校验探测凭据配置。
     *
     * @param issues 问题列表
     */
    private void validateProbeCredentials(List<InterfacePublishIssueVO> issues) {
        boolean accessKeyBlank = StringUtils.isBlank(clientProperties.getAccessKey());
        boolean secretKeyBlank = StringUtils.isBlank(clientProperties.getSecretKey());
        if (accessKeyBlank) {
            addIssue(issues, InterfacePublishIssueCategoryEnum.SDK, "PROBE_ACCESS_KEY_REQUIRED",
                    "config.feiapi.client.accessKey", "服务端管理员 AccessKey 未配置");
        }
        if (secretKeyBlank) {
            addIssue(issues, InterfacePublishIssueCategoryEnum.SDK, "PROBE_SECRET_KEY_REQUIRED",
                    "config.feiapi.client.secretKey", "服务端管理员 SecretKey 未配置");
        }
        if (StringUtils.isBlank(clientProperties.getProbeSecret())) {
            addIssue(issues, InterfacePublishIssueCategoryEnum.SDK, "PROBE_SECRET_REQUIRED",
                    "config.feiapi.client.probeSecret", "内部发布探测密钥未配置");
        }
        if (accessKeyBlank || secretKeyBlank) {
            return;
        }
        User user = userService.lambdaQuery()
                .eq(User::getAccessKey, clientProperties.getAccessKey())
                .one();
        if (user == null || !userService.isAdmin(user)) {
            addIssue(issues, InterfacePublishIssueCategoryEnum.SDK, "PROBE_ADMIN_ACCESS_KEY_INVALID",
                    "config.feiapi.client.accessKey", "配置的发布探测 AccessKey 未绑定有效管理员");
        }
        if (user == null) {
            return;
        }
        if (!Objects.equals(user.getSecretKey(), clientProperties.getSecretKey())) {
            addIssue(issues, InterfacePublishIssueCategoryEnum.SDK, "PROBE_ADMIN_SECRET_KEY_MISMATCH",
                    "config.feiapi.client.secretKey", "配置的发布探测 SecretKey 与管理员记录不匹配");
        }
    }

    /**
     * 校验路径格式。
     *
     * @param info   接口信息
     * @param issues 问题列表
     */
    private void validatePath(InterfaceInfo info, List<InterfacePublishIssueVO> issues) {
        String path = info.getPath();
        if (StringUtils.isBlank(path)) {
            return;
        }
        if (!path.startsWith("/") || path.contains("\\") || path.contains("?") || path.contains("#")
                || path.codePoints().anyMatch(Character::isISOControl)
                || Stream.of(path.split("/")).anyMatch(segment -> ".".equals(segment) || "..".equals(segment))
                || path.chars().anyMatch(Character::isWhitespace)) {
            addIssue(issues, InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "INTERFACE_PATH_INVALID",
                    "interfaceInfo.path", "网关路径格式非法");
        }
    }

    /**
     * 校验目标地址。
     *
     * @param info   接口信息
     * @param issues 问题列表
     */
    private void validateTargetHost(InterfaceInfo info, List<InterfacePublishIssueVO> issues) {
        if (StringUtils.isBlank(info.getTargetHost())) {
            return;
        }
        if (!InterfaceTargetHostValidator.isSafeTargetHost(info.getTargetHost(), targetHostProperties.getAllowedHostnames())) {
            addIssue(issues, InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "INTERFACE_TARGET_HOST_INVALID",
                    "interfaceInfo.targetHost", "真实后端地址不在允许范围内或存在安全风险");
        }
    }

    /**
     * 校验接口运行时配置文本边界。
     *
     * @param info   接口信息
     * @param issues 问题列表
     */
    private void validateInterfaceTextBoundary(InterfaceInfo info, List<InterfacePublishIssueVO> issues) {
        if (StringUtils.isNotBlank(info.getName())
                && TextSizeUtils.unicodeLengthAfterStrip(info.getName()) > MAX_INTERFACE_NAME_LENGTH) {
            addIssue(issues, InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "INTERFACE_NAME_TOO_LONG",
                    "interfaceInfo.name", "接口名称过长");
        }
        if (info.getSdkMethodName() != null && info.getSdkMethodName().trim().isEmpty()) {
            addIssue(issues, InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "SDK_METHOD_BLANK",
                    "interfaceInfo.sdkMethodName", "SDK 方法名不能为空白");
        }
        if (StringUtils.isNotBlank(info.getSdkMethodName())
                && TextSizeUtils.unicodeLengthAfterStrip(info.getSdkMethodName()) > MAX_SDK_METHOD_NAME_LENGTH) {
            addIssue(issues, InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "SDK_METHOD_TOO_LONG",
                    "interfaceInfo.sdkMethodName", "SDK 方法名过长");
        }
        validateInterfaceTextLength(issues, info.getDescription(), MAX_INTERFACE_TEXT_LENGTH,
                "INTERFACE_DESCRIPTION_TOO_LONG", "interfaceInfo.description", "接口描述过长");
        validateInterfaceTextLength(issues, info.getUrl(), MAX_INTERFACE_TEXT_LENGTH,
                "INTERFACE_URL_TOO_LONG", "interfaceInfo.url", "接口展示地址过长");
        validateInterfaceTextLength(issues, info.getPath(), MAX_INTERFACE_TEXT_LENGTH,
                "INTERFACE_PATH_TOO_LONG", "interfaceInfo.path", "接口路径过长");
        validateInterfaceTextLength(issues, info.getTargetHost(), MAX_INTERFACE_TEXT_LENGTH,
                "INTERFACE_TARGET_HOST_TOO_LONG", "interfaceInfo.targetHost", "真实后端服务地址过长");
        validateInterfaceTextLength(issues, info.getRequestParams(), MAX_INTERFACE_PAYLOAD_LENGTH,
                "INTERFACE_REQUEST_PARAMS_TOO_LONG", "interfaceInfo.requestParams", "请求参数过长");
        validateInterfaceTextLength(issues, info.getRequestHeader(), MAX_INTERFACE_PAYLOAD_LENGTH,
                "INTERFACE_REQUEST_HEADER_TOO_LONG", "interfaceInfo.requestHeader", "请求头文档过长");
        validateInterfaceTextLength(issues, info.getResponseHeader(), MAX_INTERFACE_PAYLOAD_LENGTH,
                "INTERFACE_RESPONSE_HEADER_TOO_LONG", "interfaceInfo.responseHeader", "响应头文档过长");
        validateInterfaceTextLength(issues, info.getMethod(), MAX_INTERFACE_METHOD_LENGTH,
                "INTERFACE_METHOD_TOO_LONG", "interfaceInfo.method", "请求方法过长");
        validateInterfaceTextLength(issues, info.getQuotaType(), MAX_QUOTA_TYPE_LENGTH,
                "INTERFACE_QUOTA_TYPE_TOO_LONG", "interfaceInfo.quotaType", "配额类型过长");
    }

    /**
     * 校验单个接口配置文本的 Unicode 字符长度。
     *
     * @param issues       问题列表
     * @param value        待校验文本
     * @param maxLength    最大字符数
     * @param ruleCode     规则编码
     * @param fieldPath    字段路径
     * @param message      问题说明
     */
    private void validateInterfaceTextLength(List<InterfacePublishIssueVO> issues,
                                             String value,
                                             int maxLength,
                                             String ruleCode,
                                             String fieldPath,
                                             String message) {
        if (value != null && TextSizeUtils.unicodeLengthAfterStrip(value) > maxLength) {
            addIssue(issues, InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG,
                    ruleCode, fieldPath, message);
        }
    }

    /**
     * 校验配额类型。
     *
     * @param info   接口信息
     * @param issues 问题列表
     */
    private void validateQuotaType(InterfaceInfo info, List<InterfacePublishIssueVO> issues) {
        InterfaceQuotaTypeEnum quotaTypeEnum = InterfaceQuotaTypeEnum.getEnumByValue(info.getQuotaType());
        if (quotaTypeEnum == null) {
            addIssue(issues, InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "QUOTA_TYPE_INVALID",
                    "interfaceInfo.quotaType", "配额类型不合法");
            return;
        }
        long configCount = interfaceQuotaConfigService.lambdaQuery()
                .eq(InterfaceQuotaConfig::getQuotaType, quotaTypeEnum.getValue())
                .count();
        if (configCount <= 0) {
            addIssue(issues, InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "QUOTA_CONFIG_REQUIRED",
                    "interfaceInfo.quotaType", "配额类型缺少当前有效数据库配置");
        }
    }

    /**
     * 校验文档版本格式。
     *
     * @param doc    文档主记录
     * @param issues 问题列表
     */
    private void validateDocVersion(InterfaceDoc doc, List<InterfacePublishIssueVO> issues) {
        String docVersion = TextSizeUtils.stripUnicodeWhitespace(doc.getDocVersion());
        if (!DOC_VERSION_PATTERN.matcher(docVersion).matches()) {
            addIssue(issues, InterfacePublishIssueCategoryEnum.DOCUMENT, "DOC_VERSION_INVALID",
                    "doc.docVersion", "文档版本号格式非法");
        }
    }

    /**
     * 校验展示地址和派生规则一致。
     *
     * @param info   接口信息
     * @param issues 问题列表
     */
    private void validateDisplayUrl(InterfaceInfo info, List<InterfacePublishIssueVO> issues) {
        if (StringUtils.isAnyBlank(info.getUrl(), info.getTargetHost(), info.getPath())) {
            return;
        }
        String expected = info.getTargetHost().trim().replaceAll("/+$", "") + (info.getPath().startsWith("/")
                ? info.getPath().trim() : "/" + info.getPath().trim());
        if (!Objects.equals(info.getUrl().trim(), expected)) {
            addIssue(issues, InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "DISPLAY_URL_MISMATCH",
                    "interfaceInfo.url", "展示地址必须与真实后端地址和网关路径派生结果一致");
        }
    }

    /**
     * 校验同一路径和方法唯一。
     *
     * @param info   接口信息
     * @param issues 问题列表
     */
    private void validateUniquePathAndMethod(InterfaceInfo info, List<InterfacePublishIssueVO> issues) {
        if (StringUtils.isAnyBlank(info.getPath(), info.getMethod()) || info.getId() == null) {
            return;
        }
        long count = interfaceInfoService.lambdaQuery()
                .eq(InterfaceInfo::getPath, info.getPath())
                .eq(InterfaceInfo::getMethod, info.getMethod())
                .ne(InterfaceInfo::getId, info.getId())
                .count();
        if (count > 0) {
            addIssue(issues, InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "INTERFACE_PATH_METHOD_DUPLICATED",
                    "interfaceInfo.path", "同一路径和请求方法已存在其他有效接口");
        }
    }

    /**
     * 校验运行时模板和结构化请求参数一致。
     *
     * @param context 发布上下文
     * @param issues  问题列表
     */
    private void validateRuntimeAndDocRequestParamConsistency(InterfacePublishContext context,
                                                              List<InterfacePublishIssueVO> issues) {
        JsonObject runtimeObject;
        try {
            if (StringUtils.isBlank(context.getInterfaceInfo().getRequestParams())) {
                runtimeObject = new JsonObject();
            } else {
                JsonElement element = JsonParser.parseString(context.getInterfaceInfo().getRequestParams());
                if (!element.isJsonObject()) {
                    return;
                }
                runtimeObject = element.getAsJsonObject();
            }
        } catch (JsonSyntaxException exception) {
            return;
        }
        Map<String, InterfaceDocParam> requestParamMap = context.getDocParams().stream()
                .filter(param -> InterfaceDocParamSceneEnum.QUERY.getValue().equals(param.getParamScene())
                        || InterfaceDocParamSceneEnum.BODY.getValue().equals(param.getParamScene()))
                .collect(Collectors.toMap(InterfaceDocParam::getName, param -> param, (first, second) -> first));
        String expectedScene = resolveExpectedRequestParamScene(context.getInterfaceInfo().getMethod());
        runtimeObject.entrySet().forEach(entry -> {
            InterfaceDocParam docParam = requestParamMap.get(entry.getKey());
            if (docParam == null) {
                addIssue(issues, InterfacePublishIssueCategoryEnum.RUNTIME_TEMPLATE, "REQUEST_PARAM_DOC_MISSING",
                        "params[" + entry.getKey() + "]", "运行时模板参数缺少结构化文档");
                return;
            }
            if (!Objects.equals(expectedScene, docParam.getParamScene())) {
                addIssue(issues, InterfacePublishIssueCategoryEnum.RUNTIME_TEMPLATE, "REQUEST_PARAM_SCENE_MISMATCH",
                        "params[" + entry.getKey() + "].paramScene", "运行时模板参数位置与结构化文档不一致");
            }
            if (!Objects.equals(docParam.getRequired(), 1)) {
                addIssue(issues, InterfacePublishIssueCategoryEnum.RUNTIME_TEMPLATE, "REQUEST_PARAM_REQUIRED_MISMATCH",
                        "params[" + entry.getKey() + "].required", "运行时模板参数必须在结构化文档中声明为必填");
            }
            String expectedType = resolveRuntimeType(entry.getValue());
            if (!Objects.equals(expectedType, StringUtils.lowerCase(docParam.getType(), Locale.ROOT))) {
                addIssue(issues, InterfacePublishIssueCategoryEnum.RUNTIME_TEMPLATE, "REQUEST_PARAM_TYPE_MISMATCH",
                        "params[" + entry.getKey() + "].type", "运行时模板参数类型与结构化文档不一致");
            }
        });
        requestParamMap.keySet().stream()
                .filter(name -> !runtimeObject.has(name))
                .forEach(name -> addIssue(issues, InterfacePublishIssueCategoryEnum.RUNTIME_TEMPLATE,
                        "REQUEST_PARAM_NOT_IN_TEMPLATE", "params[" + name + "]", "结构化请求参数不存在于运行时模板"));
    }

    /**
     * 根据接口请求方法推导结构化请求参数位置。
     *
     * @param method 接口请求方法
     * @return 结构化请求参数位置
     */
    private String resolveExpectedRequestParamScene(String method) {
        return InterfaceInfoMethodEnum.GET.getValue().equalsIgnoreCase(StringUtils.trimToEmpty(method))
                ? InterfaceDocParamSceneEnum.QUERY.getValue()
                : InterfaceDocParamSceneEnum.BODY.getValue();
    }

    /**
     * 根据模板值识别运行时类型。
     *
     * @param value 模板值
     * @return 参数类型
     */
    private String resolveRuntimeType(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "string";
        }
        if (value.isJsonObject()) {
            return "object";
        }
        if (value.isJsonArray()) {
            return "array";
        }
        if (value.isJsonPrimitive()) {
            if (value.getAsJsonPrimitive().isNumber()) {
                return "number";
            }
            if (value.getAsJsonPrimitive().isBoolean()) {
                return "boolean";
            }
            String marker = value.getAsString().trim().toLowerCase(Locale.ROOT);
            return SUPPORTED_PARAM_TYPES.contains(marker) ? marker : "string";
        }
        return "string";
    }

    /**
     * 校验内容类型。
     *
     * @param value    内容类型
     * @param field    字段路径
     * @param ruleCode 规则编码
     * @param issues   问题列表
     */
    private void validateContentType(String value, String field, String ruleCode, List<InterfacePublishIssueVO> issues) {
        String contentType = StringUtils.lowerCase(StringUtils.trimToEmpty(value), Locale.ROOT);
        if (!SUPPORTED_CONTENT_TYPES.contains(contentType)) {
            addIssue(issues, InterfacePublishIssueCategoryEnum.DOCUMENT, ruleCode, field, "文档内容类型不受支持");
        }
    }

    /**
     * 校验文档公开文本和描述完整性。
     *
     * @param context 发布上下文
     * @param issues  问题列表
     */
    private void validateDocPublicText(InterfacePublishContext context, List<InterfacePublishIssueVO> issues) {
        InterfaceDoc doc = context.getInterfaceDoc();
        captureRule(issues, InterfacePublishIssueCategoryEnum.DOCUMENT, "DOC_SUCCESS_EXAMPLE_UNSAFE",
                "doc.successExample", () -> contentSecurityValidator.validateJsonExample(doc.getSuccessExample(), "成功响应示例必须是合法 JSON"));
        captureRule(issues, InterfacePublishIssueCategoryEnum.DOCUMENT, "DOC_FAIL_EXAMPLE_UNSAFE",
                "doc.failExample", () -> contentSecurityValidator.validateJsonExample(doc.getFailExample(), "失败响应示例必须是合法 JSON"));
        captureRule(issues, InterfacePublishIssueCategoryEnum.DOCUMENT, "DOC_REMARK_UNSAFE",
                "doc.remark", () -> contentSecurityValidator.validateText(doc.getRemark()));
        context.getDocParams().stream().filter(Objects::nonNull).forEach(param -> {
            if ("HEADER".equals(param.getParamScene())) {
                addIssue(issues, InterfacePublishIssueCategoryEnum.DOCUMENT, "HEADER_PARAM_NOT_ALLOWED",
                        "params[" + param.getName() + "]", "结构化文档不允许自定义 Header 参数");
            }
            String description = StringUtils.trimToEmpty(param.getDescription());
            if (StringUtils.isBlank(description) || GENERATED_PARAM_DESCRIPTION.equals(description)) {
                addIssue(issues, InterfacePublishIssueCategoryEnum.DOCUMENT, "PARAM_DESCRIPTION_REQUIRED",
                        "params[" + param.getName() + "].description", "请求参数和响应字段必须填写有效公开说明");
            }
            captureRule(issues, InterfacePublishIssueCategoryEnum.DOCUMENT, "PARAM_TEXT_UNSAFE",
                    "params[" + param.getName() + "]", () -> {
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
    private void validateResponseTree(List<InterfaceDocParam> docParams, List<InterfacePublishIssueVO> issues) {
        List<InterfaceDocParam> responseParams = docParams.stream()
                .filter(param -> InterfaceDocParamSceneEnum.RESPONSE.getValue().equals(param.getParamScene()))
                .collect(Collectors.toList());
        Map<Long, InterfaceDocParam> byId = responseParams.stream()
                .filter(param -> param.getId() != null)
                .collect(Collectors.toMap(InterfaceDocParam::getId, param -> param, (first, second) -> first));
        Map<Long, List<InterfaceDocParam>> childrenMap = responseParams.stream()
                .filter(param -> param.getParentId() != null && param.getParentId() > 0)
                .collect(Collectors.groupingBy(InterfaceDocParam::getParentId));
        responseParams.forEach(param -> {
            if (StringUtils.isBlank(param.getName())) {
                addIssue(issues, InterfacePublishIssueCategoryEnum.DOCUMENT, "RESPONSE_FIELD_NAME_REQUIRED",
                        "responseParams", "响应字段名称不能为空");
            }
            String type = StringUtils.lowerCase(StringUtils.trimToEmpty(param.getType()), Locale.ROOT);
            if (!SUPPORTED_PARAM_TYPES.contains(type)) {
                addIssue(issues, InterfacePublishIssueCategoryEnum.DOCUMENT, "RESPONSE_FIELD_TYPE_INVALID",
                        "responseParams[" + param.getName() + "].type", "响应字段类型不受支持");
            }
            if (param.getParentId() != null && param.getParentId() > 0 && !byId.containsKey(param.getParentId())) {
                addIssue(issues, InterfacePublishIssueCategoryEnum.DOCUMENT, "RESPONSE_FIELD_ORPHAN",
                        "responseParams[" + param.getName() + "].parentId", "响应字段父级不存在");
            }
            if (!childrenMap.getOrDefault(param.getId(), List.of()).isEmpty()
                    && !Set.of("object", "array").contains(type)) {
                addIssue(issues, InterfacePublishIssueCategoryEnum.DOCUMENT, "RESPONSE_FIELD_PARENT_TYPE_INVALID",
                        "responseParams[" + param.getName() + "].type", "只有 object 或 array 响应字段可以拥有子字段");
            }
            if (depthOf(param, byId, new HashSet<>()) > MAX_RESPONSE_FIELD_DEPTH) {
                addIssue(issues, InterfacePublishIssueCategoryEnum.DOCUMENT, "RESPONSE_FIELD_DEPTH_EXCEEDED",
                        "responseParams[" + param.getName() + "]", "响应字段嵌套深度不能超过 8 层");
            }
        });
        detectSiblingDuplicate(responseParams, issues);
    }

    /**
     * 计算响应字段深度。
     *
     * @param param   当前字段
     * @param byId    字段映射
     * @param visited 已访问 ID
     * @return 深度
     */
    private int depthOf(InterfaceDocParam param, Map<Long, InterfaceDocParam> byId, Set<Long> visited) {
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
    private void detectSiblingDuplicate(List<InterfaceDocParam> responseParams, List<InterfacePublishIssueVO> issues) {
        responseParams.stream()
                .collect(Collectors.groupingBy(param -> Objects.toString(param.getParentId(), "root")))
                .values()
                .forEach(siblings -> {
                    Set<String> names = new HashSet<>();
                    siblings.stream()
                            .map(InterfaceDocParam::getName)
                            .filter(StringUtils::isNotBlank)
                            .filter(name -> !names.add(name))
                            .forEach(name -> addIssue(issues, InterfacePublishIssueCategoryEnum.DOCUMENT,
                                    "RESPONSE_FIELD_SIBLING_DUPLICATED", "responseParams[" + name + "]",
                                    "同级响应字段名称不能重复"));
                });
    }

    /**
     * 校验成功示例。
     *
     * @param doc    文档主记录
     * @param issues 问题列表
     */
    private void validateSuccessExample(InterfaceDoc doc, List<InterfacePublishIssueVO> issues) {
        if (StringUtils.containsIgnoreCase(doc.getResponseContentType(), "json") && StringUtils.isBlank(doc.getSuccessExample())) {
            addIssue(issues, InterfacePublishIssueCategoryEnum.DOCUMENT, "SUCCESS_EXAMPLE_REQUIRED",
                    "doc.successExample", "JSON 响应必须维护成功示例");
        }
    }

    /**
     * 校验错误码。
     *
     * @param errorCodes 错误码
     * @param issues     问题列表
     */
    private void validateErrorCodes(List<InterfaceDocErrorCode> errorCodes, List<InterfacePublishIssueVO> issues) {
        Set<String> normalizedCodes = new HashSet<>();
        errorCodes.forEach(errorCode -> {
            if (StringUtils.isBlank(errorCode.getErrorCode()) || StringUtils.isBlank(errorCode.getErrorMessage())) {
                addIssue(issues, InterfacePublishIssueCategoryEnum.DOCUMENT, "ERROR_CODE_REQUIRED",
                        "errorCodes", "错误码和错误信息不能为空");
            }
            String normalizedCode = StringUtils.lowerCase(StringUtils.trimToEmpty(errorCode.getErrorCode()), Locale.ROOT);
            if (StringUtils.isNotBlank(normalizedCode) && !normalizedCodes.add(normalizedCode)) {
                addIssue(issues, InterfacePublishIssueCategoryEnum.DOCUMENT, "ERROR_CODE_DUPLICATED",
                        "errorCodes[" + errorCode.getErrorCode() + "]", "错误码不能重复");
            }
            captureRule(issues, InterfacePublishIssueCategoryEnum.DOCUMENT, "ERROR_CODE_TEXT_UNSAFE",
                    "errorCodes[" + errorCode.getErrorCode() + "]", () -> {
                        contentSecurityValidator.validateText(errorCode.getErrorCode());
                        contentSecurityValidator.validateText(errorCode.getErrorMessage());
                        contentSecurityValidator.validateText(errorCode.getDescription());
                        contentSecurityValidator.validateSolution(errorCode.getSolution());
                    });
        });
    }

    /**
     * 转换请求参数视图。
     *
     * @param docParams 文档参数
     * @return 请求参数视图列表
     */
    private List<InterfaceDocParamVO> toRequestParamVOs(List<InterfaceDocParam> docParams) {
        return docParams.stream()
                .filter(param -> InterfaceDocParamSceneEnum.QUERY.getValue().equals(param.getParamScene())
                        || InterfaceDocParamSceneEnum.BODY.getValue().equals(param.getParamScene()))
                .map(this::toParamVO)
                .collect(Collectors.toList());
    }

    /**
     * 转换完整文档详情视图。
     *
     * @param context 发布上下文
     * @return 文档详情视图
     */
    private InterfaceDocDetailVO toDetailVO(InterfacePublishContext context) {
        InterfaceDocDetailVO detailVO = new InterfaceDocDetailVO();
        detailVO.setRequestParams(toRequestParamVOs(context.getDocParams()));
        detailVO.setDoc(new com.feiting.feiapi.interfaceplatform.documentation.model.vo.InterfaceDocVO());
        if (context.getInterfaceDoc() != null) {
            detailVO.getDoc().setRequestContentType(context.getInterfaceDoc().getRequestContentType());
            detailVO.getDoc().setResponseContentType(context.getInterfaceDoc().getResponseContentType());
        }
        String gatewayHost = Optional.ofNullable(clientProperties.getGatewayHost()).orElse("").replaceAll("/+$", "");
        String path = StringUtils.defaultString(context.getInterfaceInfo().getPath());
        detailVO.setGatewayUrl(gatewayHost + (path.startsWith("/") ? path : "/" + path));
        return detailVO;
    }

    /**
     * 转换参数视图。
     *
     * @param param 参数实体
     * @return 参数视图
     */
    private InterfaceDocParamVO toParamVO(InterfaceDocParam param) {
        InterfaceDocParamVO paramVO = new InterfaceDocParamVO();
        BeanUtils.copyProperties(param, paramVO);
        paramVO.setRequired(Objects.equals(param.getRequired(), 1));
        paramVO.setNullable(Objects.equals(param.getNullable(), 1));
        return paramVO;
    }

    /**
     * 要求文本非空。
     */
    private void requireText(List<InterfacePublishIssueVO> issues,
                             InterfacePublishIssueCategoryEnum category,
                             String ruleCode,
                             String field,
                             String value,
                             String message) {
        if (StringUtils.isBlank(value)) {
            addIssue(issues, category, ruleCode, field, message);
        }
    }

    /**
     * 捕获业务规则异常并转换为检查问题。
     */
    private void captureRule(List<InterfacePublishIssueVO> issues,
                             InterfacePublishIssueCategoryEnum category,
                             String ruleCode,
                             String field,
                             Runnable rule) {
        try {
            rule.run();
        } catch (BusinessException | IllegalArgumentException exception) {
            addIssue(issues, category, ruleCode, field, safeMessage(exception));
        }
    }

    /**
     * 添加检查问题。
     */
    private void addIssue(List<InterfacePublishIssueVO> issues,
                          InterfacePublishIssueCategoryEnum category,
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
     * 构建问题去重键。
     */
    private String issueKey(InterfacePublishIssueVO issue) {
        return issue.getCategory() + "|" + issue.getField() + "|" + issue.getRuleCode();
    }

    /**
     * 获取安全错误消息。
     */
    private String safeMessage(Exception exception) {
        return StringUtils.defaultIfBlank(exception.getMessage(), "规则校验失败");
    }

    /**
     * 校验接口 ID。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    private void validateInterfaceInfoId(Long interfaceInfoId) {
        if (interfaceInfoId == null || interfaceInfoId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
    }
}
