package com.feiting.feiapi.unit.service;

import com.feiting.feiapi.component.InterfaceDocBoundaryValidator;
import com.feiting.feiapi.component.InterfaceDocContentSecurityValidator;
import com.feiting.feiapi.component.RuntimeRequestParamTemplateValidator;
import com.feiting.feiapi.model.entity.InterfaceDoc;
import com.feiting.feiapi.model.entity.InterfaceDocParam;
import com.feiting.feiapi.model.enums.InterfaceDocParamSceneEnum;
import com.feiting.feiapi.model.enums.InterfaceDocStatusEnum;
import com.feiting.feiapi.model.publish.InterfacePublishContext;
import com.feiting.feiapi.model.vo.InterfacePublishIssueVO;
import com.feiting.feiapi.service.impl.InterfacePublishCheckServiceImpl;
import com.feiting.feiapi.service.UserService;
import com.feiting.feiapiclientsdk.FeiapiClientProperties;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.entity.User;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 接口发布前静态检查服务单元测试。
 */
@DisplayName("接口发布前静态检查服务单元测试")
class InterfacePublishCheckServiceImplTest {

    /**
     * 被测发布检查服务。
     */
    private final InterfacePublishCheckServiceImpl checkService = new InterfacePublishCheckServiceImpl(
            null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null);

    /**
     * 带真实边界、内容安全和运行时模板校验器的发布检查服务。
     */
    private final InterfacePublishCheckServiceImpl ruleCheckService = buildRuleCheckService();

    /**
     * GET 接口的运行时模板参数必须对应 QUERY 结构化参数。
     */
    @Test
    @DisplayName("GET 运行时模板参数位置必须为 QUERY")
    void shouldRequireQuerySceneForGetRuntimeParams() throws Exception {
        InterfacePublishContext context = buildContext("GET", "{\"keyword\":\"string\"}",
                List.of(buildRequestParam("keyword", InterfaceDocParamSceneEnum.BODY.getValue(), "string", 1)));
        List<InterfacePublishIssueVO> issues = invokeRuntimeAndDocConsistency(context);

        assertThat(ruleCodes(issues)).contains("REQUEST_PARAM_SCENE_MISMATCH");
    }

    /**
     * 非 GET 接口的运行时模板参数必须对应 BODY 结构化参数。
     */
    @Test
    @DisplayName("POST 运行时模板参数位置必须为 BODY")
    void shouldRequireBodySceneForPostRuntimeParams() throws Exception {
        InterfacePublishContext context = buildContext("POST", "{\"username\":\"string\"}",
                List.of(buildRequestParam("username", InterfaceDocParamSceneEnum.QUERY.getValue(), "string", 1)));
        List<InterfacePublishIssueVO> issues = invokeRuntimeAndDocConsistency(context);

        assertThat(ruleCodes(issues)).contains("REQUEST_PARAM_SCENE_MISMATCH");
    }

    /**
     * 运行时模板参数在结构化文档中必须声明为必填。
     */
    @Test
    @DisplayName("运行时模板参数必须为必填")
    void shouldRequireRuntimeParamsToBeRequiredInDoc() throws Exception {
        InterfacePublishContext context = buildContext("POST", "{\"username\":\"string\"}",
                List.of(buildRequestParam("username", InterfaceDocParamSceneEnum.BODY.getValue(), "string", 0)));
        List<InterfacePublishIssueVO> issues = invokeRuntimeAndDocConsistency(context);

        assertThat(ruleCodes(issues)).contains("REQUEST_PARAM_REQUIRED_MISMATCH");
    }

    /**
     * 名称、位置、类型和必填性一致时不产生问题。
     */
    @Test
    @DisplayName("运行时模板和结构化请求参数完全一致时通过")
    void shouldPassWhenRuntimeAndDocRequestParamsAreConsistent() throws Exception {
        InterfacePublishContext context = buildContext("GET", "{\"keyword\":\"string\"}",
                List.of(buildRequestParam("keyword", InterfaceDocParamSceneEnum.QUERY.getValue(), "string", 1)));
        List<InterfacePublishIssueVO> issues = invokeRuntimeAndDocConsistency(context);

        assertThat(issues).isEmpty();
    }

    /**
     * 公开接口路径不能被内部文件系统路径规则误判。
     */
    @Test
    @DisplayName("发布检查允许公开接口路径使用 data 前缀")
    void shouldAllowPublicDataPathDuringPublishCheck() throws Exception {
        InterfacePublishContext context = buildInterfaceConfigContext("getLoveWords", "{}");
        context.getInterfaceInfo().setPath("/data/users");
        context.getInterfaceInfo().setUrl("http://feiapi-interface/data/users");

        List<InterfacePublishIssueVO> issues = invokeRule("checkInterfaceConfig", context, ruleCheckService);

        assertThat(ruleCodes(issues)).doesNotContain("INTERFACE_TEXT_UNSAFE");
    }

    /**
     * 发布检查必须重新校验文档版本格式。
     */
    @Test
    @DisplayName("发布检查拒绝非法文档版本")
    void shouldRejectInvalidDocVersionDuringPublishCheck() throws Exception {
        InterfacePublishContext context = new InterfacePublishContext();
        context.setInterfaceDoc(buildDoc("v 1"));
        context.setDocParams(List.of());
        context.setErrorCodes(List.of());

        List<InterfacePublishIssueVO> issues = invokeRule("checkDocument", context, ruleCheckService);

        assertThat(ruleCodes(issues)).contains("DOC_VERSION_INVALID");
    }

    /**
     * 发布检查必须重新校验 SDK 方法名持久化边界。
     */
    @Test
    @DisplayName("发布检查拒绝过长 SDK 方法名")
    void shouldRejectTooLongSdkMethodNameDuringPublishCheck() throws Exception {
        InterfacePublishContext context = buildInterfaceConfigContext("get" + "A".repeat(128), "{}");

        List<InterfacePublishIssueVO> issues = invokeInterfaceBoundaryRule(context.getInterfaceInfo());

        assertThat(ruleCodes(issues)).contains("SDK_METHOD_TOO_LONG");
    }

    /**
     * 发布检查必须重新校验全部接口持久化文本边界。
     */
    @Test
    @DisplayName("发布检查聚合全部接口文本边界问题")
    void shouldAggregateAllInterfaceTextBoundaryIssues() throws Exception {
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setDescription("描".repeat(513));
        interfaceInfo.setUrl("u".repeat(513));
        interfaceInfo.setPath("p".repeat(513));
        interfaceInfo.setTargetHost("h".repeat(513));
        interfaceInfo.setRequestParams("q".repeat(65536));
        interfaceInfo.setRequestHeader("r".repeat(65536));
        interfaceInfo.setResponseHeader("s".repeat(65536));
        interfaceInfo.setMethod("M".repeat(17));
        interfaceInfo.setQuotaType("Q".repeat(33));

        List<InterfacePublishIssueVO> issues = invokeInterfaceBoundaryRule(interfaceInfo);

        assertThat(ruleCodes(issues)).containsExactlyInAnyOrder(
                "INTERFACE_DESCRIPTION_TOO_LONG",
                "INTERFACE_URL_TOO_LONG",
                "INTERFACE_PATH_TOO_LONG",
                "INTERFACE_TARGET_HOST_TOO_LONG",
                "INTERFACE_REQUEST_PARAMS_TOO_LONG",
                "INTERFACE_REQUEST_HEADER_TOO_LONG",
                "INTERFACE_RESPONSE_HEADER_TOO_LONG",
                "INTERFACE_METHOD_TOO_LONG",
                "INTERFACE_QUOTA_TYPE_TOO_LONG");
    }

    /**
     * 发布检查必须重新执行运行时模板校验。
     */
    @Test
    @DisplayName("发布检查拒绝非法运行时模板")
    void shouldRejectInvalidRuntimeTemplateDuringPublishCheck() throws Exception {
        InterfacePublishContext context = buildInterfaceConfigContext("getLoveWords", "{\" userId \":1}");

        List<InterfacePublishIssueVO> issues = invokeRule("checkRuntimeTemplate", context, ruleCheckService);

        assertThat(ruleCodes(issues)).contains("RUNTIME_TEMPLATE_INVALID");
    }

    /**
     * 发布检查内容安全扫描必须覆盖运行时模板文本。
     */
    @Test
    @DisplayName("发布检查扫描运行时模板敏感文本")
    void shouldScanRuntimeTemplateSecurityDuringPublishCheck() throws Exception {
        InterfacePublishContext context = buildInterfaceConfigContext("getLoveWords",
                "{\"accessKey\":\"1234567890\"}");

        List<InterfacePublishIssueVO> issues = invokeRule("checkInterfaceConfig", context, ruleCheckService);

        assertThat(ruleCodes(issues)).contains("INTERFACE_TEXT_UNSAFE");
    }

    /**
     * 发布检查内容安全扫描必须覆盖遗留请求头、响应头和请求方法字段。
     */
    @Test
    @DisplayName("发布检查扫描全部遗留接口文本字段")
    void shouldScanLegacyInterfaceTextFieldsDuringPublishCheck() throws Exception {
        InterfacePublishContext requestHeaderContext = buildInterfaceConfigContext("getLoveWords", "{}");
        requestHeaderContext.getInterfaceInfo().setRequestHeader("Authorization: Bearer private-token-value");
        InterfacePublishContext responseHeaderContext = buildInterfaceConfigContext("getLoveWords", "{}");
        responseHeaderContext.getInterfaceInfo().setResponseHeader("password=private-password-value");
        InterfacePublishContext methodContext = buildInterfaceConfigContext("getLoveWords", "{}");
        methodContext.getInterfaceInfo().setMethod("token=private-token-value");

        List<List<InterfacePublishIssueVO>> issueGroups = List.of(
                invokeRule("checkInterfaceConfig", requestHeaderContext, ruleCheckService),
                invokeRule("checkInterfaceConfig", responseHeaderContext, ruleCheckService),
                invokeRule("checkInterfaceConfig", methodContext, ruleCheckService));

        assertThat(issueGroups)
                .allSatisfy(issues -> assertThat(ruleCodes(issues)).contains("INTERFACE_TEXT_UNSAFE"));
    }

    /**
     * 网关路径必须拒绝 DEL 与 C1 控制字符。
     */
    @Test
    @DisplayName("发布检查拒绝路径中的完整 ISO 控制字符范围")
    void shouldRejectDelAndC1ControlCharactersInPath() throws Exception {
        List<String> paths = List.of("/api/user\u007Fprofile", "/api/user\u0085profile");

        List<List<InterfacePublishIssueVO>> issueGroups = paths.stream()
                .map(path -> invokePathRuleUnchecked(buildPathInterfaceInfo(path)))
                .toList();

        assertThat(issueGroups)
                .allSatisfy(issues -> assertThat(ruleCodes(issues)).contains("INTERFACE_PATH_INVALID"));
    }

    /**
     * SDK 方法不存在时仍需继续累计探测凭据缺失问题。
     */
    @Test
    @DisplayName("SDK 方法不存在时继续累计探测凭据问题")
    void shouldAggregateCredentialIssuesWhenSdkMethodMissing() throws Exception {
        InterfacePublishContext context = buildInterfaceConfigContext("missingSdkMethod", "{}");
        InterfacePublishCheckServiceImpl service = buildCredentialCheckService(new FeiapiClientProperties());

        List<InterfacePublishIssueVO> issues = invokeRule("checkSdkContract", context, service);

        assertThat(ruleCodes(issues)).contains(
                "SDK_METHOD_NOT_FOUND",
                "PROBE_ACCESS_KEY_REQUIRED",
                "PROBE_SECRET_KEY_REQUIRED",
                "PROBE_SECRET_REQUIRED");
    }

    /**
     * SDK 方法缺少调用注解时仍需继续累计探测凭据缺失问题。
     */
    @Test
    @DisplayName("SDK 方法缺少注解时继续累计探测凭据问题")
    void shouldAggregateCredentialIssuesWhenSdkInvokeAnnotationMissing() throws Exception {
        InterfacePublishContext context = buildInterfaceConfigContext("plainSdkMethod", "{}");
        context.setSdkMethod(InterfacePublishCheckServiceImplTest.class.getDeclaredMethod("plainSdkMethod"));
        InterfacePublishCheckServiceImpl service = buildCredentialCheckService(new FeiapiClientProperties());

        List<InterfacePublishIssueVO> issues = invokeRule("checkSdkContract", context, service);

        assertThat(ruleCodes(issues)).contains(
                "SDK_INVOKE_ANNOTATION_REQUIRED",
                "PROBE_ACCESS_KEY_REQUIRED",
                "PROBE_SECRET_KEY_REQUIRED",
                "PROBE_SECRET_REQUIRED");
    }

    /**
     * AccessKey 对应非管理员时仍需继续检查 SecretKey 是否匹配。
     */
    @Test
    @DisplayName("非管理员 AccessKey 继续累计 SecretKey 不匹配问题")
    void shouldAggregateSecretKeyMismatchForNonAdminAccessKey() throws Exception {
        FeiapiClientProperties properties = new FeiapiClientProperties();
        properties.setAccessKey("user-ak");
        properties.setSecretKey("configured-sk");
        properties.setProbeSecret("probe-secret");
        User user = new User();
        user.setAccessKey("user-ak");
        user.setSecretKey("stored-sk");
        UserService userService = mock(UserService.class);
        @SuppressWarnings("unchecked")
        LambdaQueryChainWrapper<User> query = mock(LambdaQueryChainWrapper.class);
        when(userService.lambdaQuery()).thenReturn(query);
        when(query.eq(any(), any())).thenReturn(query);
        when(query.one()).thenReturn(user);
        when(userService.isAdmin(user)).thenReturn(false);
        InterfacePublishCheckServiceImpl service = buildCredentialCheckService(properties, userService);

        List<InterfacePublishIssueVO> issues = new ArrayList<>();
        Method method = InterfacePublishCheckServiceImpl.class.getDeclaredMethod(
                "validateProbeCredentials", List.class);
        method.setAccessible(true);
        method.invoke(service, issues);

        assertThat(ruleCodes(issues)).containsExactlyInAnyOrder(
                "PROBE_ADMIN_ACCESS_KEY_INVALID",
                "PROBE_ADMIN_SECRET_KEY_MISMATCH");
    }

    /**
     * 调用运行时模板与结构化请求参数一致性校验私有方法。
     *
     * @param context 发布上下文
     * @return 问题列表
     * @throws Exception 反射调用失败时抛出
     */
    @SuppressWarnings("unchecked")
    private List<InterfacePublishIssueVO> invokeRuntimeAndDocConsistency(InterfacePublishContext context) throws Exception {
        List<InterfacePublishIssueVO> issues = new ArrayList<>();
        Method method = InterfacePublishCheckServiceImpl.class.getDeclaredMethod(
                "validateRuntimeAndDocRequestParamConsistency", InterfacePublishContext.class, List.class);
        method.setAccessible(true);
        try {
            method.invoke(checkService, context, issues);
        } catch (InvocationTargetException exception) {
            throw (Exception) exception.getCause();
        }
        return issues;
    }

    /**
     * 调用指定发布检查私有规则方法。
     *
     * @param methodName   方法名
     * @param context      发布上下文
     * @param targetService 被测服务实例
     * @return 问题列表
     * @throws Exception 反射调用失败时抛出
     */
    private List<InterfacePublishIssueVO> invokeRule(String methodName,
                                                     InterfacePublishContext context,
                                                     InterfacePublishCheckServiceImpl targetService) throws Exception {
        List<InterfacePublishIssueVO> issues = new ArrayList<>();
        Method method = InterfacePublishCheckServiceImpl.class.getDeclaredMethod(
                methodName, InterfacePublishContext.class, List.class);
        method.setAccessible(true);
        try {
            method.invoke(targetService, context, issues);
        } catch (InvocationTargetException exception) {
            throw (Exception) exception.getCause();
        }
        return issues;
    }

    /**
     * 调用接口配置文本边界规则。
     *
     * @param interfaceInfo 接口信息
     * @return 问题列表
     * @throws Exception 反射调用失败时抛出
     */
    private List<InterfacePublishIssueVO> invokeInterfaceBoundaryRule(InterfaceInfo interfaceInfo) throws Exception {
        List<InterfacePublishIssueVO> issues = new ArrayList<>();
        Method method = InterfacePublishCheckServiceImpl.class.getDeclaredMethod(
                "validateInterfaceTextBoundary", InterfaceInfo.class, List.class);
        method.setAccessible(true);
        try {
            method.invoke(ruleCheckService, interfaceInfo, issues);
        } catch (InvocationTargetException exception) {
            throw (Exception) exception.getCause();
        }
        return issues;
    }

    /**
     * 调用路径格式校验私有方法。
     *
     * @param interfaceInfo 接口信息
     * @return 问题列表
     */
    private List<InterfacePublishIssueVO> invokePathRuleUnchecked(InterfaceInfo interfaceInfo) {
        try {
            List<InterfacePublishIssueVO> issues = new ArrayList<>();
            Method method = InterfacePublishCheckServiceImpl.class.getDeclaredMethod(
                    "validatePath", InterfaceInfo.class, List.class);
            method.setAccessible(true);
            method.invoke(ruleCheckService, interfaceInfo, issues);
            return issues;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("调用路径发布检查规则失败", exception);
        }
    }

    /**
     * 构造仅包含网关路径的接口信息。
     *
     * @param path 网关路径
     * @return 接口信息
     */
    private InterfaceInfo buildPathInterfaceInfo(String path) {
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setPath(path);
        return interfaceInfo;
    }

    /**
     * 构造带真实规则依赖的发布检查服务。
     *
     * @return 发布检查服务
     */
    private InterfacePublishCheckServiceImpl buildRuleCheckService() {
        InterfaceDocBoundaryValidator boundaryValidator = new InterfaceDocBoundaryValidator();
        InterfaceDocContentSecurityValidator contentSecurityValidator = new InterfaceDocContentSecurityValidator();
        RuntimeRequestParamTemplateValidator runtimeValidator =
                new RuntimeRequestParamTemplateValidator(boundaryValidator);
        return new InterfacePublishCheckServiceImpl(
                null, null, null, null, null, null, null, null,
                null, new com.feiting.feiapi.config.InterfaceTargetHostProperties(),
                runtimeValidator, boundaryValidator, contentSecurityValidator,
                null, null, null);
    }

    /**
     * 构造带探测凭据配置的发布检查服务。
     *
     * @param clientProperties SDK 客户端配置
     * @return 发布检查服务
     */
    private InterfacePublishCheckServiceImpl buildCredentialCheckService(FeiapiClientProperties clientProperties) {
        return buildCredentialCheckService(clientProperties, null);
    }

    /**
     * 构造带探测凭据配置和用户服务的发布检查服务。
     *
     * @param clientProperties SDK 客户端配置
     * @param userService      用户服务
     * @return 发布检查服务
     */
    private InterfacePublishCheckServiceImpl buildCredentialCheckService(FeiapiClientProperties clientProperties,
                                                                          UserService userService) {
        return new InterfacePublishCheckServiceImpl(
                null, null, null, null, null, null, userService, null,
                clientProperties, null, null, null, null, null, null, null);
    }

    /**
     * 构造发布上下文。
     *
     * @param method        请求方法
     * @param requestParams 运行时请求参数模板
     * @param docParams     结构化文档参数
     * @return 发布上下文
     */
    private InterfacePublishContext buildContext(String method, String requestParams, List<InterfaceDocParam> docParams) {
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setMethod(method);
        interfaceInfo.setRequestParams(requestParams);
        InterfacePublishContext context = new InterfacePublishContext();
        context.setInterfaceInfo(interfaceInfo);
        context.setDocParams(docParams);
        return context;
    }

    /**
     * 构造接口配置发布上下文。
     *
     * @param sdkMethodName SDK 方法名
     * @param requestParams 运行时请求参数模板
     * @return 发布上下文
     */
    private InterfacePublishContext buildInterfaceConfigContext(String sdkMethodName, String requestParams) {
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setName("用户接口");
        interfaceInfo.setDescription("用户接口说明");
        interfaceInfo.setMethod("POST");
        interfaceInfo.setPath("/api/user");
        interfaceInfo.setTargetHost("http://feiapi-interface");
        interfaceInfo.setUrl("http://feiapi-interface/api/user");
        interfaceInfo.setSdkMethodName(sdkMethodName);
        interfaceInfo.setQuotaType("");
        interfaceInfo.setRequestParams(requestParams);
        InterfacePublishContext context = new InterfacePublishContext();
        context.setInterfaceInfo(interfaceInfo);
        context.setDocParams(List.of());
        return context;
    }

    /**
     * 构造文档主记录。
     *
     * @param docVersion 文档版本
     * @return 文档主记录
     */
    private InterfaceDoc buildDoc(String docVersion) {
        InterfaceDoc doc = new InterfaceDoc();
        doc.setDocStatus(InterfaceDocStatusEnum.READY.getValue());
        doc.setDocVersion(docVersion);
        doc.setRequestContentType("application/json");
        doc.setResponseContentType("application/json");
        doc.setSuccessExample("{}");
        doc.setFailExample("{}");
        return doc;
    }

    /**
     * 构造结构化请求参数。
     *
     * @param name       参数名称
     * @param paramScene 参数位置
     * @param type       参数类型
     * @param required   是否必填，1 表示必填
     * @return 结构化请求参数
     */
    private InterfaceDocParam buildRequestParam(String name, String paramScene, String type, int required) {
        InterfaceDocParam param = new InterfaceDocParam();
        param.setName(name);
        param.setParamScene(paramScene);
        param.setType(type);
        param.setRequired(required);
        return param;
    }

    /**
     * 提取问题规则编码。
     *
     * @param issues 问题列表
     * @return 规则编码列表
     */
    private List<String> ruleCodes(List<InterfacePublishIssueVO> issues) {
        return issues.stream()
                .map(InterfacePublishIssueVO::getRuleCode)
                .toList();
    }

    /**
     * 无 SDK 调用注解的普通方法，用于验证注解缺失分支不会阻断凭据检查。
     *
     * @return 普通响应文本
     */
    private String plainSdkMethod() {
        return "{}";
    }
}
