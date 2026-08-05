package com.feiting.feiapi.interfaceplatform.publishing.component.rule;

import com.feiting.feiapi.config.InterfaceTargetHostProperties;
import com.feiting.feiapi.interfaceplatform.documentation.component.InterfaceDocContentSecurityValidator;
import com.feiting.feiapi.interfaceplatform.publishing.model.context.InterfacePublishContext;
import com.feiting.feiapi.interfaceplatform.publishing.model.enums.InterfacePublishIssueCategoryEnum;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapi.service.InterfaceQuotaConfigService;
import com.feiting.feiapi.utils.TextSizeUtils;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.entity.InterfaceQuotaConfig;
import com.feiting.feiapicommon.model.enums.InterfaceInfoMethodEnum;
import com.feiting.feiapicommon.model.enums.InterfaceQuotaTypeEnum;
import com.feiting.feiapicommon.utils.InterfaceTargetHostValidator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * 接口运行配置发布规则。
 */
@Component
public class InterfaceConfigPublishRule implements InterfacePublishRule {

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
     * 接口配额配置服务。
     */
    private final InterfaceQuotaConfigService interfaceQuotaConfigService;

    /**
     * 真实目标地址白名单配置。
     */
    private final InterfaceTargetHostProperties targetHostProperties;

    /**
     * 文档内容安全校验器。
     */
    private final InterfaceDocContentSecurityValidator contentSecurityValidator;

    /**
     * 创建接口运行配置发布规则。
     *
     * @param interfaceInfoService     接口信息服务
     * @param interfaceQuotaConfigService 接口配额配置服务
     * @param targetHostProperties     真实目标地址白名单配置
     * @param contentSecurityValidator 文档内容安全校验器
     */
    public InterfaceConfigPublishRule(InterfaceInfoService interfaceInfoService,
                                      InterfaceQuotaConfigService interfaceQuotaConfigService,
                                      InterfaceTargetHostProperties targetHostProperties,
                                      InterfaceDocContentSecurityValidator contentSecurityValidator) {
        this.interfaceInfoService = interfaceInfoService;
        this.interfaceQuotaConfigService = interfaceQuotaConfigService;
        this.targetHostProperties = targetHostProperties;
        this.contentSecurityValidator = contentSecurityValidator;
    }

    /**
     * 执行接口运行配置发布检查。
     *
     * @param context   发布上下文
     * @param collector 问题收集器
     */
    @Override
    public void check(InterfacePublishContext context, InterfacePublishIssueCollector collector) {
        InterfaceInfo info = context.getInterfaceInfo();
        collector.requireText(InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "INTERFACE_NAME_REQUIRED",
                "interfaceInfo.name", info.getName(), "接口名称不能为空");
        collector.requireText(InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "INTERFACE_DESCRIPTION_REQUIRED",
                "interfaceInfo.description", info.getDescription(), "接口描述不能为空");
        collector.requireText(InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "INTERFACE_METHOD_REQUIRED",
                "interfaceInfo.method", info.getMethod(), "请求方法不能为空");
        collector.requireText(InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "INTERFACE_PATH_REQUIRED",
                "interfaceInfo.path", info.getPath(), "网关路径不能为空");
        collector.requireText(InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "INTERFACE_TARGET_HOST_REQUIRED",
                "interfaceInfo.targetHost", info.getTargetHost(), "真实后端地址不能为空");
        collector.requireText(InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "SDK_METHOD_REQUIRED",
                "interfaceInfo.sdkMethodName", info.getSdkMethodName(), "SDK 方法名不能为空");
        collector.requireText(InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "QUOTA_TYPE_REQUIRED",
                "interfaceInfo.quotaType", info.getQuotaType(), "配额类型不能为空");
        if (StringUtils.isNotBlank(info.getMethod()) && !InterfaceInfoMethodEnum.isValid(info.getMethod())) {
            collector.addIssue(InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "INTERFACE_METHOD_UNSUPPORTED",
                    "interfaceInfo.method", "请求方法不在平台白名单内");
        }
        validateInterfaceTextBoundary(info, collector);
        validatePath(info, collector);
        validateTargetHost(info, collector);
        validateQuotaType(info, collector);
        validateDisplayUrl(info, collector);
        validateUniquePathAndMethod(info, collector);
        Stream.of(info.getName(), info.getDescription(), info.getPath(), info.getUrl(), info.getTargetHost(),
                        info.getSdkMethodName(), info.getQuotaType(), info.getRequestParams(),
                        info.getRequestHeader(), info.getResponseHeader(), info.getMethod())
                .forEach(text -> collector.captureRule(InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG,
                        "INTERFACE_TEXT_UNSAFE", "interfaceInfo", () -> contentSecurityValidator.validateText(text)));
    }

    /**
     * 校验路径格式。
     *
     * @param info      接口信息
     * @param collector 问题收集器
     */
    public void validatePath(InterfaceInfo info, InterfacePublishIssueCollector collector) {
        String path = info.getPath();
        if (StringUtils.isBlank(path)) {
            return;
        }
        if (!path.startsWith("/") || path.contains("\\") || path.contains("?") || path.contains("#")
                || path.codePoints().anyMatch(Character::isISOControl)
                || Stream.of(path.split("/")).anyMatch(segment -> ".".equals(segment) || "..".equals(segment))
                || path.chars().anyMatch(Character::isWhitespace)) {
            collector.addIssue(InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "INTERFACE_PATH_INVALID",
                    "interfaceInfo.path", "网关路径格式非法");
        }
    }

    /**
     * 校验接口运行时配置文本边界。
     *
     * @param info      接口信息
     * @param collector 问题收集器
     */
    public void validateInterfaceTextBoundary(InterfaceInfo info, InterfacePublishIssueCollector collector) {
        if (StringUtils.isNotBlank(info.getName())
                && TextSizeUtils.unicodeLengthAfterStrip(info.getName()) > MAX_INTERFACE_NAME_LENGTH) {
            collector.addIssue(InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "INTERFACE_NAME_TOO_LONG",
                    "interfaceInfo.name", "接口名称过长");
        }
        if (info.getSdkMethodName() != null && info.getSdkMethodName().trim().isEmpty()) {
            collector.addIssue(InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "SDK_METHOD_BLANK",
                    "interfaceInfo.sdkMethodName", "SDK 方法名不能为空白");
        }
        if (StringUtils.isNotBlank(info.getSdkMethodName())
                && TextSizeUtils.unicodeLengthAfterStrip(info.getSdkMethodName()) > MAX_SDK_METHOD_NAME_LENGTH) {
            collector.addIssue(InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "SDK_METHOD_TOO_LONG",
                    "interfaceInfo.sdkMethodName", "SDK 方法名过长");
        }
        validateInterfaceTextLength(collector, info.getDescription(), MAX_INTERFACE_TEXT_LENGTH,
                "INTERFACE_DESCRIPTION_TOO_LONG", "interfaceInfo.description", "接口描述过长");
        validateInterfaceTextLength(collector, info.getUrl(), MAX_INTERFACE_TEXT_LENGTH,
                "INTERFACE_URL_TOO_LONG", "interfaceInfo.url", "接口展示地址过长");
        validateInterfaceTextLength(collector, info.getPath(), MAX_INTERFACE_TEXT_LENGTH,
                "INTERFACE_PATH_TOO_LONG", "interfaceInfo.path", "接口路径过长");
        validateInterfaceTextLength(collector, info.getTargetHost(), MAX_INTERFACE_TEXT_LENGTH,
                "INTERFACE_TARGET_HOST_TOO_LONG", "interfaceInfo.targetHost", "真实后端服务地址过长");
        validateInterfaceTextLength(collector, info.getRequestParams(), MAX_INTERFACE_PAYLOAD_LENGTH,
                "INTERFACE_REQUEST_PARAMS_TOO_LONG", "interfaceInfo.requestParams", "请求参数过长");
        validateInterfaceTextLength(collector, info.getRequestHeader(), MAX_INTERFACE_PAYLOAD_LENGTH,
                "INTERFACE_REQUEST_HEADER_TOO_LONG", "interfaceInfo.requestHeader", "请求头文档过长");
        validateInterfaceTextLength(collector, info.getResponseHeader(), MAX_INTERFACE_PAYLOAD_LENGTH,
                "INTERFACE_RESPONSE_HEADER_TOO_LONG", "interfaceInfo.responseHeader", "响应头文档过长");
        validateInterfaceTextLength(collector, info.getMethod(), MAX_INTERFACE_METHOD_LENGTH,
                "INTERFACE_METHOD_TOO_LONG", "interfaceInfo.method", "请求方法过长");
        validateInterfaceTextLength(collector, info.getQuotaType(), MAX_QUOTA_TYPE_LENGTH,
                "INTERFACE_QUOTA_TYPE_TOO_LONG", "interfaceInfo.quotaType", "配额类型过长");
    }

    /**
     * 校验单个接口配置文本的 Unicode 字符长度。
     *
     * @param collector 问题收集器
     * @param value     待校验文本
     * @param maxLength 最大字符数
     * @param ruleCode  规则编码
     * @param fieldPath 字段路径
     * @param message   问题说明
     */
    private void validateInterfaceTextLength(InterfacePublishIssueCollector collector,
                                             String value,
                                             int maxLength,
                                             String ruleCode,
                                             String fieldPath,
                                             String message) {
        if (value != null && TextSizeUtils.unicodeLengthAfterStrip(value) > maxLength) {
            collector.addIssue(InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, ruleCode, fieldPath, message);
        }
    }

    /**
     * 校验目标地址。
     *
     * @param info      接口信息
     * @param collector 问题收集器
     */
    private void validateTargetHost(InterfaceInfo info, InterfacePublishIssueCollector collector) {
        if (StringUtils.isBlank(info.getTargetHost())) {
            return;
        }
        if (!InterfaceTargetHostValidator.isSafeTargetHost(info.getTargetHost(), targetHostProperties.getAllowedHostnames())) {
            collector.addIssue(InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "INTERFACE_TARGET_HOST_INVALID",
                    "interfaceInfo.targetHost", "真实后端地址不在允许范围内或存在安全风险");
        }
    }

    /**
     * 校验配额类型。
     *
     * @param info      接口信息
     * @param collector 问题收集器
     */
    private void validateQuotaType(InterfaceInfo info, InterfacePublishIssueCollector collector) {
        InterfaceQuotaTypeEnum quotaTypeEnum = InterfaceQuotaTypeEnum.getEnumByValue(info.getQuotaType());
        if (quotaTypeEnum == null) {
            collector.addIssue(InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "QUOTA_TYPE_INVALID",
                    "interfaceInfo.quotaType", "配额类型不合法");
            return;
        }
        long configCount = interfaceQuotaConfigService.lambdaQuery()
                .eq(InterfaceQuotaConfig::getQuotaType, quotaTypeEnum.getValue())
                .count();
        if (configCount <= 0) {
            collector.addIssue(InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "QUOTA_CONFIG_REQUIRED",
                    "interfaceInfo.quotaType", "配额类型缺少当前有效数据库配置");
        }
    }

    /**
     * 校验展示地址和派生规则一致。
     *
     * @param info      接口信息
     * @param collector 问题收集器
     */
    private void validateDisplayUrl(InterfaceInfo info, InterfacePublishIssueCollector collector) {
        if (StringUtils.isAnyBlank(info.getUrl(), info.getTargetHost(), info.getPath())) {
            return;
        }
        String expected = info.getTargetHost().trim().replaceAll("/+$", "") + (info.getPath().startsWith("/")
                ? info.getPath().trim() : "/" + info.getPath().trim());
        if (!Objects.equals(info.getUrl().trim(), expected)) {
            collector.addIssue(InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "DISPLAY_URL_MISMATCH",
                    "interfaceInfo.url", "展示地址必须与真实后端地址和网关路径派生结果一致");
        }
    }

    /**
     * 校验同一路径和方法唯一。
     *
     * @param info      接口信息
     * @param collector 问题收集器
     */
    private void validateUniquePathAndMethod(InterfaceInfo info, InterfacePublishIssueCollector collector) {
        if (StringUtils.isAnyBlank(info.getPath(), info.getMethod()) || info.getId() == null) {
            return;
        }
        long count = interfaceInfoService.lambdaQuery()
                .eq(InterfaceInfo::getPath, info.getPath())
                .eq(InterfaceInfo::getMethod, info.getMethod())
                .ne(InterfaceInfo::getId, info.getId())
                .count();
        if (count > 0) {
            collector.addIssue(InterfacePublishIssueCategoryEnum.INTERFACE_CONFIG, "INTERFACE_PATH_METHOD_DUPLICATED",
                    "interfaceInfo.path", "同一路径和请求方法已存在其他有效接口");
        }
    }
}
