package com.feiting.feiapi.interfaceplatform.publishing.component.rule;

import com.feiting.feiapi.interfaceplatform.documentation.component.InterfaceDocCurlExampleGenerator;
import com.feiting.feiapi.interfaceplatform.documentation.component.InterfaceDocJavaSdkExampleGenerator;
import com.feiting.feiapi.interfaceplatform.documentation.model.snapshot.InterfaceDocParamSnapshot;
import com.feiting.feiapi.interfaceplatform.documentation.model.vo.InterfaceDocDetailVO;
import com.feiting.feiapi.interfaceplatform.documentation.model.vo.InterfaceDocParamVO;
import com.feiting.feiapi.interfaceplatform.documentation.model.vo.InterfaceDocVO;
import com.feiting.feiapi.interfaceplatform.publishing.model.context.InterfacePublishContext;
import com.feiting.feiapi.interfaceplatform.publishing.model.enums.InterfacePublishIssueCategoryEnum;
import com.feiting.feiapiclientsdk.FeiapiClientProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 调用示例发布规则。
 */
@Component
public class CallExamplePublishRule implements InterfacePublishRule {

    /**
     * Java SDK 示例生成器。
     */
    private final InterfaceDocJavaSdkExampleGenerator javaSdkExampleGenerator;

    /**
     * curl 示例生成器。
     */
    private final InterfaceDocCurlExampleGenerator curlExampleGenerator;

    /**
     * SDK 客户端配置。
     */
    private final FeiapiClientProperties clientProperties;

    /**
     * 创建调用示例发布规则。
     *
     * @param javaSdkExampleGenerator Java SDK 示例生成器
     * @param curlExampleGenerator    curl 示例生成器
     * @param clientProperties        SDK 客户端配置
     */
    public CallExamplePublishRule(InterfaceDocJavaSdkExampleGenerator javaSdkExampleGenerator,
                                  InterfaceDocCurlExampleGenerator curlExampleGenerator,
                                  FeiapiClientProperties clientProperties) {
        this.javaSdkExampleGenerator = javaSdkExampleGenerator;
        this.curlExampleGenerator = curlExampleGenerator;
        this.clientProperties = clientProperties;
    }

    /**
     * 执行调用示例发布检查。
     *
     * @param context   发布上下文
     * @param collector 问题收集器
     */
    @Override
    public void check(InterfacePublishContext context, InterfacePublishIssueCollector collector) {
        collector.captureRule(InterfacePublishIssueCategoryEnum.CALL_EXAMPLE, "JAVA_SDK_EXAMPLE_INVALID",
                "example.javaSdk", () -> javaSdkExampleGenerator.generate(context.getInterfaceInfo(),
                        toRequestParamVOs(context.getDocParams())));
        collector.captureRule(InterfacePublishIssueCategoryEnum.CALL_EXAMPLE, "CURL_EXAMPLE_INVALID",
                "example.curl", () -> curlExampleGenerator.generate(context.getInterfaceInfo(), toDetailVO(context)));
    }

    /**
     * 转换请求参数视图。
     *
     * @param docParams 文档参数
     * @return 请求参数视图列表
     */
    private List<InterfaceDocParamVO> toRequestParamVOs(List<InterfaceDocParamSnapshot> docParams) {
        return docParams.stream()
                .filter(param -> "QUERY".equals(param.getParamScene()) || "BODY".equals(param.getParamScene()))
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
        detailVO.setDoc(new InterfaceDocVO());
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
     * @param param 参数快照
     * @return 参数视图
     */
    private InterfaceDocParamVO toParamVO(InterfaceDocParamSnapshot param) {
        InterfaceDocParamVO paramVO = new InterfaceDocParamVO();
        BeanUtils.copyProperties(param, paramVO);
        paramVO.setRequired(Objects.equals(param.getRequired(), 1));
        paramVO.setNullable(Objects.equals(param.getNullable(), 1));
        return paramVO;
    }
}
