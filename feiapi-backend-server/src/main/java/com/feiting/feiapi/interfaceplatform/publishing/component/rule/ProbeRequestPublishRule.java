package com.feiting.feiapi.interfaceplatform.publishing.component.rule;

import com.feiting.feiapi.interfaceplatform.publishing.component.InterfaceProbeRequestBuilder;
import com.feiting.feiapi.interfaceplatform.publishing.model.context.InterfacePublishContext;
import com.feiting.feiapi.interfaceplatform.publishing.model.enums.InterfacePublishIssueCategoryEnum;
import com.feiting.feiapiclientsdk.annotation.SdkInvoke;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 发布探测请求构造规则。
 */
@Component
public class ProbeRequestPublishRule implements InterfacePublishRule {

    /**
     * 探测请求构造器。
     */
    private final InterfaceProbeRequestBuilder probeRequestBuilder;

    /**
     * 创建发布探测请求构造规则。
     *
     * @param probeRequestBuilder 探测请求构造器
     */
    public ProbeRequestPublishRule(InterfaceProbeRequestBuilder probeRequestBuilder) {
        this.probeRequestBuilder = probeRequestBuilder;
    }

    /**
     * 执行探测请求构造检查。
     *
     * @param context   发布上下文
     * @param collector 问题收集器
     */
    @Override
    public void check(InterfacePublishContext context, InterfacePublishIssueCollector collector) {
        Method method = context.getSdkMethod();
        if (method == null || method.getAnnotation(SdkInvoke.class) == null) {
            return;
        }
        collector.captureRule(InterfacePublishIssueCategoryEnum.RUNTIME_TEMPLATE, "PROBE_REQUEST_BUILD_FAILED",
                "interfaceInfo.requestParams", () -> probeRequestBuilder.build(context));
    }
}
