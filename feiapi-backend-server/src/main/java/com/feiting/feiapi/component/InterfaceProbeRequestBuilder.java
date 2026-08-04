package com.feiting.feiapi.component;

import com.feiting.feiapi.exception.InterfacePublishProbeException;
import com.feiting.feiapi.model.entity.InterfaceDocParam;
import com.feiting.feiapi.model.enums.InterfaceDocParamSceneEnum;
import com.feiting.feiapi.model.enums.PublishProbeFailureStageEnum;
import com.feiting.feiapi.model.publish.InterfacePublishContext;
import com.feiting.feiapi.utils.TextSizeUtils;
import com.feiting.feiapiclientsdk.annotation.SdkInvoke;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 接口发布探测请求参数构造器。
 */
@Component
public class InterfaceProbeRequestBuilder {

    /**
     * 发布探测请求体最大 UTF-8 字节数。
     */
    private static final int MAX_PROBE_REQUEST_BODY_BYTES = 65_535;

    /**
     * 运行时模板中的字符串类型标记。
     */
    private static final String STRING_TYPE_MARKER = "string";

    /**
     * 构造发布探测请求参数。
     *
     * @param publishContext 发布上下文
     * @return 探测请求参数 JSON，无参方法返回空字符串
     */
    public String build(InterfacePublishContext publishContext) {
        SdkInvoke sdkInvoke = publishContext.getSdkMethod().getAnnotation(SdkInvoke.class);
        if (!sdkInvoke.needParams()) {
            return "";
        }
        JsonObject runtimeTemplate = parseRuntimeTemplate(publishContext);
        Map<String, InterfaceDocParam> requestParamMap = requestParamMap(publishContext.getDocParams());
        JsonObject probeRequest = new JsonObject();
        runtimeTemplate.entrySet().forEach(entry -> {
            String paramName = entry.getKey();
            InterfaceDocParam docParam = requestParamMap.get(paramName);
            if (docParam == null) {
                throw probePrepareException("运行时参数缺少结构化文档：" + paramName);
            }
            probeRequest.add(paramName, normalizeValue(paramName, docParam, entry.getValue()));
        });
        String requestJson = probeRequest.toString();
        if (TextSizeUtils.utf8ByteLength(requestJson) > MAX_PROBE_REQUEST_BODY_BYTES) {
            throw probePrepareException("发布探测请求体不能超过 65535 字节");
        }
        return requestJson;
    }

    /**
     * 解析运行时模板。
     *
     * @param publishContext 发布上下文
     * @return 运行时模板对象
     */
    private JsonObject parseRuntimeTemplate(InterfacePublishContext publishContext) {
        String requestParams = publishContext.getInterfaceInfo().getRequestParams();
        try {
            JsonElement element = JsonParser.parseString(requestParams);
            if (!element.isJsonObject()) {
                throw probePrepareException("运行时参数模板必须是 JSON 对象");
            }
            return element.getAsJsonObject();
        } catch (JsonSyntaxException exception) {
            throw probePrepareException("运行时参数模板必须是合法 JSON");
        }
    }

    /**
     * 构建请求参数名称映射。
     *
     * @param docParams 文档参数
     * @return 请求参数映射
     */
    private Map<String, InterfaceDocParam> requestParamMap(List<InterfaceDocParam> docParams) {
        return docParams.stream()
                .filter(Objects::nonNull)
                .filter(param -> InterfaceDocParamSceneEnum.QUERY.getValue().equals(param.getParamScene())
                        || InterfaceDocParamSceneEnum.BODY.getValue().equals(param.getParamScene()))
                .sorted(Comparator.comparing(param -> Objects.requireNonNullElse(param.getSortOrder(), Integer.MAX_VALUE)))
                .collect(Collectors.toMap(InterfaceDocParam::getName, param -> param, (first, second) -> first,
                        LinkedHashMap::new));
    }

    /**
     * 根据结构化文档声明类型标准化探测参数值。
     *
     * @param paramName     参数名称
     * @param docParam      文档参数
     * @param templateValue 模板值
     * @return 标准化 JSON 值
     */
    private JsonElement normalizeValue(String paramName, InterfaceDocParam docParam, JsonElement templateValue) {
        String type = StringUtils.lowerCase(StringUtils.trimToEmpty(docParam.getType()), Locale.ROOT);
        String rawValue = StringUtils.trimToNull(docParam.getExampleValue());
        boolean usingTemplateValue = rawValue == null;
        if (usingTemplateValue) {
            rawValue = templateToText(templateValue);
        }
        try {
            return switch (type) {
                case "string" -> new JsonPrimitive(requireProbeStringValue(paramName, rawValue,
                        templateValue, usingTemplateValue));
                case "number" -> new JsonPrimitive(new BigDecimal(requireText(paramName, rawValue)));
                case "boolean" -> normalizeBoolean(paramName, rawValue);
                case "object" -> normalizeStructured(paramName, rawValue, true);
                case "array" -> normalizeStructured(paramName, rawValue, false);
                default -> throw probePrepareException("请求参数类型不受支持：" + paramName);
            };
        } catch (NumberFormatException | JsonSyntaxException exception) {
            throw probePrepareException("请求参数示例值格式错误：" + paramName);
        }
    }

    /**
     * 将模板值转换为文本。
     *
     * @param templateValue 模板值
     * @return 文本值
     */
    private String templateToText(JsonElement templateValue) {
        if (templateValue == null || templateValue.isJsonNull()) {
            return null;
        }
        if (templateValue.isJsonPrimitive()) {
            JsonPrimitive primitive = templateValue.getAsJsonPrimitive();
            return primitive.isString() ? primitive.getAsString() : primitive.toString();
        }
        return templateValue.toString();
    }

    /**
     * 要求字符串参数具备真实探测值。
     *
     * @param paramName     参数名称
     * @param rawValue      原始值
     * @param templateValue 模板值
     * @param usingTemplateValue 是否正在使用模板值
     * @return 字符串探测值
     */
    private String requireProbeStringValue(String paramName,
                                           String rawValue,
                                           JsonElement templateValue,
                                           boolean usingTemplateValue) {
        String text = requireText(paramName, rawValue);
        if (usingTemplateValue && isStringTypeMarker(templateValue)) {
            throw probePrepareException("缺少可安全探测的参数值：" + paramName);
        }
        return text;
    }

    /**
     * 判断模板值是否为字符串类型标记。
     *
     * @param templateValue 模板值
     * @return 是否为字符串类型标记
     */
    private boolean isStringTypeMarker(JsonElement templateValue) {
        if (templateValue == null || templateValue.isJsonNull() || !templateValue.isJsonPrimitive()) {
            return false;
        }
        JsonPrimitive primitive = templateValue.getAsJsonPrimitive();
        return primitive.isString()
                && STRING_TYPE_MARKER.equals(StringUtils.lowerCase(StringUtils.trimToEmpty(primitive.getAsString()),
                Locale.ROOT));
    }

    /**
     * 解析布尔值。
     *
     * @param paramName 参数名称
     * @param rawValue  原始值
     * @return JSON 布尔值
     */
    private JsonPrimitive normalizeBoolean(String paramName, String rawValue) {
        String text = requireText(paramName, rawValue);
        if (!"true".equalsIgnoreCase(text) && !"false".equalsIgnoreCase(text)) {
            throw probePrepareException("请求参数布尔值只允许 true 或 false：" + paramName);
        }
        return new JsonPrimitive(Boolean.parseBoolean(text));
    }

    /**
     * 解析对象或数组值。
     *
     * @param paramName 参数名称
     * @param rawValue  原始值
     * @param object    是否要求对象
     * @return JSON 对象或数组
     */
    private JsonElement normalizeStructured(String paramName, String rawValue, boolean object) {
        JsonElement element = JsonParser.parseString(requireText(paramName, rawValue));
        if ((object && !element.isJsonObject()) || (!object && !element.isJsonArray())) {
            throw probePrepareException("请求参数结构化值类型不匹配：" + paramName);
        }
        return element;
    }

    /**
     * 要求文本非空。
     *
     * @param paramName 参数名称
     * @param rawValue  原始值
     * @return 非空文本
     */
    private String requireText(String paramName, String rawValue) {
        String text = StringUtils.trimToNull(rawValue);
        if (text == null) {
            throw probePrepareException("缺少可安全探测的参数值：" + paramName);
        }
        return text;
    }

    /**
     * 创建探测准备失败异常。
     *
     * @param message 安全错误信息
     * @return 发布探测异常
     */
    private InterfacePublishProbeException probePrepareException(String message) {
        return new InterfacePublishProbeException(PublishProbeFailureStageEnum.SDK_INVOCATION, message);
    }
}
