package com.feiting.feiapi.interfaceplatform.publishing.component.rule;

import com.feiting.feiapi.interfaceplatform.publishing.model.context.InterfacePublishContext;
import com.feiting.feiapi.interfaceplatform.publishing.model.enums.InterfacePublishIssueCategoryEnum;
import com.feiting.feiapi.service.UserService;
import com.feiting.feiapiclientsdk.FeiapiClientProperties;
import com.feiting.feiapiclientsdk.annotation.SdkInvoke;
import com.feiting.feiapiclientsdk.model.ProbeStrategy;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * SDK 契约发布规则。
 */
@Component
public class SdkContractPublishRule implements InterfacePublishRule {

    /**
     * SDK 客户端配置。
     */
    private final FeiapiClientProperties clientProperties;

    /**
     * 创建 SDK 契约发布规则。
     *
     * @param clientProperties SDK 客户端配置
     * @param userService      保留兼容旧构造调用的用户服务参数
     */
    public SdkContractPublishRule(FeiapiClientProperties clientProperties, UserService userService) {
        this.clientProperties = clientProperties;
    }

    /**
     * 执行 SDK 契约发布检查。
     *
     * @param context   发布上下文
     * @param collector 问题收集器
     */
    @Override
    public void check(InterfacePublishContext context, InterfacePublishIssueCollector collector) {
        InterfaceInfo info = context.getInterfaceInfo();
        Method method = context.getSdkMethod();
        if (StringUtils.isBlank(info.getSdkMethodName()) || method == null) {
            collector.addIssue(InterfacePublishIssueCategoryEnum.SDK, "SDK_METHOD_NOT_FOUND",
                    "interfaceInfo.sdkMethodName", "SDK 方法不存在或未注册");
        } else {
            validateSdkMethod(method, collector);
        }
        validateProbeCredentials(collector);
    }

    /**
     * 校验 SDK 方法声明。
     *
     * @param method    SDK 方法
     * @param collector 问题收集器
     */
    private void validateSdkMethod(Method method, InterfacePublishIssueCollector collector) {
        SdkInvoke sdkInvoke = method.getAnnotation(SdkInvoke.class);
        if (sdkInvoke == null) {
            collector.addIssue(InterfacePublishIssueCategoryEnum.SDK, "SDK_INVOKE_ANNOTATION_REQUIRED",
                    "interfaceInfo.sdkMethodName", "SDK 方法缺少调用注解");
            return;
        }
        boolean signatureValid = sdkInvoke.needParams()
                ? method.getParameterCount() == 1 && String.class.equals(method.getParameterTypes()[0])
                : method.getParameterCount() == 0;
        if (!signatureValid || !String.class.equals(method.getReturnType())) {
            collector.addIssue(InterfacePublishIssueCategoryEnum.SDK, "SDK_METHOD_SIGNATURE_INVALID",
                    "interfaceInfo.sdkMethodName", "SDK 方法签名必须与参数契约一致且返回 String");
        }
        if (ProbeStrategy.UNSPECIFIED.equals(sdkInvoke.probeStrategy())) {
            collector.addIssue(InterfacePublishIssueCategoryEnum.SDK, "SDK_PROBE_STRATEGY_REQUIRED",
                    "interfaceInfo.sdkMethodName", "SDK 方法必须显式声明安全探测策略");
        }
    }

    /**
     * 校验探测凭据配置。
     *
     * @param collector 问题收集器
     */
    public void validateProbeCredentials(InterfacePublishIssueCollector collector) {
        if (StringUtils.isBlank(clientProperties.getProbeSecret())) {
            collector.addIssue(InterfacePublishIssueCategoryEnum.SDK, "PROBE_SECRET_REQUIRED",
                    "config.feiapi.client.probeSecret", "内部发布探测密钥未配置");
        }
    }
}
