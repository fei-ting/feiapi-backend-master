package com.feiting.feiapi.interfaceplatform.publishing.service.impl;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.interfaceplatform.definition.component.RuntimeRequestParamTemplateValidator;
import com.feiting.feiapi.interfaceplatform.documentation.model.snapshot.InterfaceDocPublishSnapshot;
import com.feiting.feiapi.interfaceplatform.documentation.service.api.InterfaceDocPublishReader;
import com.feiting.feiapi.interfaceplatform.documentation.component.InterfaceDocContentSecurityValidator;
import com.feiting.feiapi.interfaceplatform.documentation.component.InterfaceDocCurlExampleGenerator;
import com.feiting.feiapi.interfaceplatform.documentation.component.InterfaceDocJavaSdkExampleGenerator;
import com.feiting.feiapi.interfaceplatform.documentation.service.api.InterfaceDocPublicationValidator;
import com.feiting.feiapi.interfaceplatform.publishing.component.InterfaceProbeRequestBuilder;
import com.feiting.feiapi.interfaceplatform.publishing.component.rule.CallExamplePublishRule;
import com.feiting.feiapi.interfaceplatform.publishing.component.rule.DocumentPublishRule;
import com.feiting.feiapi.interfaceplatform.publishing.component.rule.InterfaceConfigPublishRule;
import com.feiting.feiapi.interfaceplatform.publishing.component.rule.InterfacePublishIssueCollector;
import com.feiting.feiapi.interfaceplatform.publishing.component.rule.InterfacePublishRule;
import com.feiting.feiapi.interfaceplatform.publishing.component.rule.ProbeRequestPublishRule;
import com.feiting.feiapi.interfaceplatform.publishing.component.rule.RuntimeTemplatePublishRule;
import com.feiting.feiapi.interfaceplatform.publishing.component.rule.SdkContractPublishRule;
import com.feiting.feiapi.interfaceplatform.publishing.exception.InterfacePublishCheckException;
import com.feiting.feiapi.interfaceplatform.publishing.model.context.InterfacePublishContext;
import com.feiting.feiapi.interfaceplatform.publishing.model.vo.InterfacePublishCheckVO;
import com.feiting.feiapi.interfaceplatform.publishing.model.vo.InterfacePublishIssueVO;
import com.feiting.feiapi.interfaceplatform.publishing.service.api.InterfacePublishCheckService;
import com.feiting.feiapi.interfaceplatform.definition.component.SdkMethodRegistry;
import com.feiting.feiapi.mapper.InterfaceInfoMapper;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapi.service.InterfaceQuotaConfigService;
import com.feiting.feiapi.service.UserService;
import com.feiting.feiapi.config.InterfaceTargetHostProperties;
import com.feiting.feiapiclientsdk.FeiapiClientProperties;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 接口发布前静态检查服务实现。
 */
@Service
public class InterfacePublishCheckServiceImpl implements InterfacePublishCheckService {

    /**
     * 接口信息数据访问对象。
     */
    private final InterfaceInfoMapper interfaceInfoMapper;

    /**
     * 接口文档发布快照读取服务。
     */
    private final InterfaceDocPublishReader interfaceDocPublishReader;

    /**
     * SDK 方法注册器。
     */
    private final SdkMethodRegistry sdkMethodRegistry;

    /**
     * 探测请求构造器。
     */
    private final InterfaceProbeRequestBuilder probeRequestBuilder;

    /**
     * 接口配置发布规则。
     */
    private final InterfaceConfigPublishRule interfaceConfigRule;

    /**
     * SDK 契约发布规则。
     */
    private final SdkContractPublishRule sdkContractRule;

    /**
     * 运行时模板发布规则。
     */
    private final RuntimeTemplatePublishRule runtimeTemplateRule;

    /**
     * 文档发布规则。
     */
    private final DocumentPublishRule documentRule;

    /**
     * 发布静态检查规则列表。
     */
    private final List<InterfacePublishRule> publishRules;

    /**
     * 创建接口发布前静态检查服务实现。
     *
     * @param interfaceInfoMapper     接口信息数据访问对象
     * @param interfaceDocPublishReader 接口文档发布快照读取服务
     * @param sdkMethodRegistry       SDK 方法注册器
     * @param probeRequestBuilder     探测请求构造器
     * @param interfaceConfigRule     接口配置发布规则
     * @param sdkContractRule         SDK 契约发布规则
     * @param runtimeTemplateRule     运行时模板发布规则
     * @param documentRule            文档发布规则
     * @param callExampleRule         调用示例发布规则
     * @param probeRequestRule        探测请求发布规则
     */
    @Autowired
    public InterfacePublishCheckServiceImpl(InterfaceInfoMapper interfaceInfoMapper,
                                            InterfaceDocPublishReader interfaceDocPublishReader,
                                            SdkMethodRegistry sdkMethodRegistry,
                                            InterfaceProbeRequestBuilder probeRequestBuilder,
                                            InterfaceConfigPublishRule interfaceConfigRule,
                                            SdkContractPublishRule sdkContractRule,
                                            RuntimeTemplatePublishRule runtimeTemplateRule,
                                            DocumentPublishRule documentRule,
                                            CallExamplePublishRule callExampleRule,
                                            ProbeRequestPublishRule probeRequestRule) {
        this.interfaceInfoMapper = interfaceInfoMapper;
        this.interfaceDocPublishReader = interfaceDocPublishReader;
        this.sdkMethodRegistry = sdkMethodRegistry;
        this.probeRequestBuilder = probeRequestBuilder;
        this.interfaceConfigRule = interfaceConfigRule;
        this.sdkContractRule = sdkContractRule;
        this.runtimeTemplateRule = runtimeTemplateRule;
        this.documentRule = documentRule;
        this.publishRules = List.of(interfaceConfigRule, sdkContractRule, runtimeTemplateRule,
                documentRule, callExampleRule, probeRequestRule);
    }

    /**
     * 创建兼容旧单元测试直接构造方式的发布检查服务。
     *
     * @param interfaceInfoService       接口信息服务
     * @param interfaceInfoMapper        接口信息数据访问对象
     * @param interfaceDocPublishReader  接口文档发布快照读取服务
     * @param interfaceQuotaConfigService 接口配额配置服务
     * @param userService                用户服务
     * @param sdkMethodRegistry          SDK 方法注册器
     * @param clientProperties           SDK 客户端配置
     * @param targetHostProperties       真实目标地址白名单配置
     * @param runtimeTemplateValidator   运行时参数模板校验器
     * @param contentSecurityValidator   文档内容安全校验器
     * @param publicationValidator       文档发布校验服务
     * @param javaSdkExampleGenerator    Java SDK 示例生成器
     * @param curlExampleGenerator       curl 示例生成器
     * @param probeRequestBuilder        探测请求构造器
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
                                            InterfaceDocContentSecurityValidator contentSecurityValidator,
                                            InterfaceDocPublicationValidator publicationValidator,
                                            InterfaceDocJavaSdkExampleGenerator javaSdkExampleGenerator,
                                            InterfaceDocCurlExampleGenerator curlExampleGenerator,
                                            InterfaceProbeRequestBuilder probeRequestBuilder) {
        this(interfaceInfoMapper,
                interfaceDocPublishReader,
                sdkMethodRegistry,
                probeRequestBuilder,
                new InterfaceConfigPublishRule(interfaceInfoService, interfaceQuotaConfigService,
                        targetHostProperties, contentSecurityValidator),
                new SdkContractPublishRule(clientProperties, userService),
                new RuntimeTemplatePublishRule(runtimeTemplateValidator),
                new DocumentPublishRule(publicationValidator),
                new CallExamplePublishRule(javaSdkExampleGenerator, curlExampleGenerator, clientProperties),
                new ProbeRequestPublishRule(probeRequestBuilder));
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
        context.setInterfaceDoc(docSnapshot);
        context.setDocParams(docSnapshot.getDocParams());
        context.setErrorCodes(docSnapshot.getErrorCodes());
        context.setSdkMethod(sdkMethodRegistry.getMethodMap().get(StringUtils.trimToEmpty(interfaceInfo.getSdkMethodName())));
        return context;
    }

    /**
     * 执行全部静态规则并返回聚合结果。
     *
     * @param context 发布上下文
     * @return 检查结果
     */
    private InterfacePublishCheckVO doCheck(InterfacePublishContext context) {
        List<InterfacePublishIssueVO> issues = new ArrayList<>();
        InterfacePublishIssueCollector collector = new InterfacePublishIssueCollector(issues);
        publishRules.forEach(rule -> rule.check(context, collector));

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
     * 构建问题去重键。
     *
     * @param issue 发布检查问题
     * @return 去重键
     */
    private String issueKey(InterfacePublishIssueVO issue) {
        return issue.getCategory() + "|" + issue.getField() + "|" + issue.getRuleCode();
    }

    /**
     * 兼容旧单元测试的接口配置规则入口。
     *
     * @param context 发布上下文
     * @param issues  问题列表
     */
    private void checkInterfaceConfig(InterfacePublishContext context, List<InterfacePublishIssueVO> issues) {
        interfaceConfigRule.check(context, new InterfacePublishIssueCollector(issues));
    }

    /**
     * 兼容旧单元测试的 SDK 契约规则入口。
     *
     * @param context 发布上下文
     * @param issues  问题列表
     */
    private void checkSdkContract(InterfacePublishContext context, List<InterfacePublishIssueVO> issues) {
        sdkContractRule.check(context, new InterfacePublishIssueCollector(issues));
    }

    /**
     * 兼容旧单元测试的运行时模板规则入口。
     *
     * @param context 发布上下文
     * @param issues  问题列表
     */
    private void checkRuntimeTemplate(InterfacePublishContext context, List<InterfacePublishIssueVO> issues) {
        runtimeTemplateRule.check(context, new InterfacePublishIssueCollector(issues));
    }

    /**
     * 兼容旧单元测试的文档规则入口。
     *
     * @param context 发布上下文
     * @param issues  问题列表
     */
    private void checkDocument(InterfacePublishContext context, List<InterfacePublishIssueVO> issues) {
        documentRule.check(context, new InterfacePublishIssueCollector(issues));
    }

    /**
     * 兼容旧单元测试的运行时模板与结构化参数一致性规则入口。
     *
     * @param context 发布上下文
     * @param issues  问题列表
     */
    private void validateRuntimeAndDocRequestParamConsistency(InterfacePublishContext context,
                                                              List<InterfacePublishIssueVO> issues) {
        runtimeTemplateRule.validateRuntimeAndDocRequestParamConsistency(context, new InterfacePublishIssueCollector(issues));
    }

    /**
     * 兼容旧单元测试的接口文本边界规则入口。
     *
     * @param interfaceInfo 接口信息
     * @param issues        问题列表
     */
    private void validateInterfaceTextBoundary(InterfaceInfo interfaceInfo, List<InterfacePublishIssueVO> issues) {
        interfaceConfigRule.validateInterfaceTextBoundary(interfaceInfo, new InterfacePublishIssueCollector(issues));
    }

    /**
     * 兼容旧单元测试的路径规则入口。
     *
     * @param interfaceInfo 接口信息
     * @param issues        问题列表
     */
    private void validatePath(InterfaceInfo interfaceInfo, List<InterfacePublishIssueVO> issues) {
        interfaceConfigRule.validatePath(interfaceInfo, new InterfacePublishIssueCollector(issues));
    }

    /**
     * 兼容旧单元测试的探测凭据规则入口。
     *
     * @param issues 问题列表
     */
    private void validateProbeCredentials(List<InterfacePublishIssueVO> issues) {
        sdkContractRule.validateProbeCredentials(new InterfacePublishIssueCollector(issues));
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
