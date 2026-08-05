package com.feiting.feiapi.interfaceplatform.publishing.component.rule;

import com.feiting.feiapi.interfaceplatform.definition.component.RuntimeRequestParamTemplateValidator;
import com.feiting.feiapi.interfaceplatform.documentation.model.enums.InterfaceDocParamSceneEnum;
import com.feiting.feiapi.interfaceplatform.documentation.model.snapshot.InterfaceDocParamSnapshot;
import com.feiting.feiapi.interfaceplatform.publishing.model.context.InterfacePublishContext;
import com.feiting.feiapi.interfaceplatform.publishing.model.enums.InterfacePublishIssueCategoryEnum;
import com.feiting.feiapiclientsdk.annotation.SdkInvoke;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.enums.InterfaceInfoMethodEnum;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 运行时请求参数模板发布规则。
 */
@Component
public class RuntimeTemplatePublishRule implements InterfacePublishRule {

    /**
     * 支持的参数类型。
     */
    private static final Set<String> SUPPORTED_PARAM_TYPES = Set.of("string", "number", "boolean", "object", "array");

    /**
     * 运行时参数模板校验器。
     */
    private final RuntimeRequestParamTemplateValidator runtimeTemplateValidator;

    /**
     * 创建运行时请求参数模板发布规则。
     *
     * @param runtimeTemplateValidator 运行时参数模板校验器
     */
    public RuntimeTemplatePublishRule(RuntimeRequestParamTemplateValidator runtimeTemplateValidator) {
        this.runtimeTemplateValidator = runtimeTemplateValidator;
    }

    /**
     * 执行运行时模板发布检查。
     *
     * @param context   发布上下文
     * @param collector 问题收集器
     */
    @Override
    public void check(InterfacePublishContext context, InterfacePublishIssueCollector collector) {
        InterfaceInfo info = context.getInterfaceInfo();
        Method method = context.getSdkMethod();
        SdkInvoke sdkInvoke = method == null ? null : method.getAnnotation(SdkInvoke.class);
        boolean needParams = sdkInvoke != null && sdkInvoke.needParams();
        if (needParams && StringUtils.isBlank(info.getRequestParams())) {
            collector.addIssue(InterfacePublishIssueCategoryEnum.RUNTIME_TEMPLATE, "RUNTIME_TEMPLATE_REQUIRED",
                    "interfaceInfo.requestParams", "SDK 方法需要参数时运行时模板不能为空");
        }
        if (!needParams && StringUtils.isNotBlank(info.getRequestParams())) {
            collector.addIssue(InterfacePublishIssueCategoryEnum.RUNTIME_TEMPLATE, "RUNTIME_TEMPLATE_MUST_BE_EMPTY",
                    "interfaceInfo.requestParams", "SDK 方法不需要参数时运行时模板必须为空");
        }
        collector.captureRule(InterfacePublishIssueCategoryEnum.RUNTIME_TEMPLATE, "RUNTIME_TEMPLATE_INVALID",
                "interfaceInfo.requestParams", () -> runtimeTemplateValidator.validate(info.getRequestParams()));
        validateRuntimeAndDocRequestParamConsistency(context, collector);
    }

    /**
     * 校验运行时模板和结构化请求参数一致。
     *
     * @param context   发布上下文
     * @param collector 问题收集器
     */
    public void validateRuntimeAndDocRequestParamConsistency(InterfacePublishContext context,
                                                             InterfacePublishIssueCollector collector) {
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
        Map<String, InterfaceDocParamSnapshot> requestParamMap = context.getDocParams().stream()
                .filter(param -> InterfaceDocParamSceneEnum.QUERY.getValue().equals(param.getParamScene())
                        || InterfaceDocParamSceneEnum.BODY.getValue().equals(param.getParamScene()))
                .collect(Collectors.toMap(InterfaceDocParamSnapshot::getName, param -> param, (first, second) -> first));
        String expectedScene = resolveExpectedRequestParamScene(context.getInterfaceInfo().getMethod());
        runtimeObject.entrySet().forEach(entry -> validateRuntimeParam(
                entry.getKey(), entry.getValue(), requestParamMap.get(entry.getKey()), expectedScene, collector));
        requestParamMap.keySet().stream()
                .filter(name -> !runtimeObject.has(name))
                .forEach(name -> collector.addIssue(InterfacePublishIssueCategoryEnum.RUNTIME_TEMPLATE,
                        "REQUEST_PARAM_NOT_IN_TEMPLATE", "params[" + name + "]", "结构化请求参数不存在于运行时模板"));
    }

    /**
     * 校验单个运行时参数。
     *
     * @param paramName     参数名称
     * @param templateValue 模板值
     * @param docParam      文档参数
     * @param expectedScene 期望位置
     * @param collector     问题收集器
     */
    private void validateRuntimeParam(String paramName,
                                      JsonElement templateValue,
                                      InterfaceDocParamSnapshot docParam,
                                      String expectedScene,
                                      InterfacePublishIssueCollector collector) {
        if (docParam == null) {
            collector.addIssue(InterfacePublishIssueCategoryEnum.RUNTIME_TEMPLATE, "REQUEST_PARAM_DOC_MISSING",
                    "params[" + paramName + "]", "运行时模板参数缺少结构化文档");
            return;
        }
        if (!Objects.equals(expectedScene, docParam.getParamScene())) {
            collector.addIssue(InterfacePublishIssueCategoryEnum.RUNTIME_TEMPLATE, "REQUEST_PARAM_SCENE_MISMATCH",
                    "params[" + paramName + "].paramScene", "运行时模板参数位置与结构化文档不一致");
        }
        if (!Objects.equals(docParam.getRequired(), 1)) {
            collector.addIssue(InterfacePublishIssueCategoryEnum.RUNTIME_TEMPLATE, "REQUEST_PARAM_REQUIRED_MISMATCH",
                    "params[" + paramName + "].required", "运行时模板参数必须在结构化文档中声明为必填");
        }
        String expectedType = resolveRuntimeType(templateValue);
        if (!Objects.equals(expectedType, StringUtils.lowerCase(docParam.getType(), Locale.ROOT))) {
            collector.addIssue(InterfacePublishIssueCategoryEnum.RUNTIME_TEMPLATE, "REQUEST_PARAM_TYPE_MISMATCH",
                    "params[" + paramName + "].type", "运行时模板参数类型与结构化文档不一致");
        }
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
}
