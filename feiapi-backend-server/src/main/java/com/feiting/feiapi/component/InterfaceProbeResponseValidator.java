package com.feiting.feiapi.component;

import com.feiting.feiapi.exception.InterfacePublishProbeException;
import com.feiting.feiapi.interfaceplatform.documentation.model.entity.InterfaceDoc;
import com.feiting.feiapi.interfaceplatform.documentation.model.entity.InterfaceDocParam;
import com.feiting.feiapi.interfaceplatform.documentation.model.enums.InterfaceDocParamSceneEnum;
import com.feiting.feiapi.model.enums.PublishProbeFailureStageEnum;
import com.feiting.feiapi.model.publish.InterfacePublishContext;
import com.feiting.feiapiclientsdk.model.ProbeInvocationResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 接口发布探测响应契约校验器。
 */
@Component
public class InterfaceProbeResponseValidator {

    /**
     * 校验发布探测响应契约。
     *
     * @param publishContext 发布上下文
     * @param result         SDK 探测响应元数据
     */
    public void validate(InterfacePublishContext publishContext, ProbeInvocationResult result) {
        if (result == null) {
            throw probeException(PublishProbeFailureStageEnum.SDK_INVOCATION, "SDK 未返回探测响应元数据");
        }
        String gatewayFailureStage = StringUtils.trimToNull(result.getGatewayFailureStage());
        if (gatewayFailureStage != null) {
            throw probeException(resolveGatewayStage(gatewayFailureStage), "网关发布探测校验失败");
        }
        Integer statusCode = result.getStatusCode();
        if (statusCode == null || statusCode < 200 || statusCode >= 300) {
            throw probeException(PublishProbeFailureStageEnum.DOWNSTREAM_STATUS, "下游返回非 2xx 状态");
        }
        InterfaceDoc doc = publishContext.getInterfaceDoc();
        String expectedContentType = doc == null ? null : doc.getResponseContentType();
        validateContentType(expectedContentType, result.getContentType());
        if (!isJsonContentType(expectedContentType)) {
            validateNonJsonBody(doc, result.getBody(), publishContext.getDocParams());
            return;
        }
        JsonElement responseJson = parseJson(result.getBody());
        JsonElement successExample = parseJson(doc.getSuccessExample());
        if (!jsonRootType(responseJson).equals(jsonRootType(successExample))) {
            throw probeException(PublishProbeFailureStageEnum.RESPONSE_FORMAT, "响应 JSON 根类型与成功示例不一致");
        }
        List<InterfaceDocParam> responseParams = publishContext.getDocParams().stream()
                .filter(param -> InterfaceDocParamSceneEnum.RESPONSE.getValue().equals(param.getParamScene()))
                .sorted(Comparator.comparing(param -> Objects.requireNonNullElse(param.getSortOrder(), Integer.MAX_VALUE)))
                .collect(Collectors.toList());
        if (!responseParams.isEmpty() && !responseJson.isJsonObject() && !responseJson.isJsonArray()) {
            throw probeException(PublishProbeFailureStageEnum.RESPONSE_STRUCTURE, "JSON 标量响应不能配置对象式响应字段");
        }
        validateRootFields(responseJson, responseParams);
    }

    /**
     * 校验响应媒体类型兼容性。
     *
     * @param expectedContentType 文档声明类型
     * @param actualContentType   实际类型
     */
    private void validateContentType(String expectedContentType, String actualContentType) {
        if (StringUtils.isBlank(actualContentType)) {
            throw probeException(PublishProbeFailureStageEnum.RESPONSE_FORMAT, "响应缺少 Content-Type");
        }
        try {
            MediaType expected = MediaType.parseMediaType(StringUtils.defaultIfBlank(expectedContentType, "application/json"));
            MediaType actual = MediaType.parseMediaType(actualContentType);
            if (!expected.isCompatibleWith(actual)) {
                throw probeException(PublishProbeFailureStageEnum.RESPONSE_FORMAT, "响应 Content-Type 与文档声明不兼容");
            }
        } catch (IllegalArgumentException exception) {
            throw probeException(PublishProbeFailureStageEnum.RESPONSE_FORMAT, "响应 Content-Type 格式非法");
        }
    }

    /**
     * 校验非 JSON 响应体规则。
     *
     * @param doc           文档主记录
     * @param body          响应体
     * @param docParams     文档参数
     */
    private void validateNonJsonBody(InterfaceDoc doc, String body, List<InterfaceDocParam> docParams) {
        boolean hasResponseParams = docParams.stream()
                .anyMatch(param -> InterfaceDocParamSceneEnum.RESPONSE.getValue().equals(param.getParamScene()));
        if (StringUtils.isNotBlank(doc.getSuccessExample()) && StringUtils.isBlank(body)) {
            throw probeException(PublishProbeFailureStageEnum.RESPONSE_FORMAT, "响应体不能为空");
        }
        if (hasResponseParams) {
            throw probeException(PublishProbeFailureStageEnum.RESPONSE_STRUCTURE, "非 JSON 响应不能配置结构化响应字段");
        }
    }

    /**
     * 解析 JSON。
     *
     * @param body JSON 文本
     * @return JSON 元素
     */
    private JsonElement parseJson(String body) {
        try {
            return JsonParser.parseString(body);
        } catch (JsonSyntaxException exception) {
            throw probeException(PublishProbeFailureStageEnum.RESPONSE_FORMAT, "响应体不是合法 JSON");
        }
    }

    /**
     * 校验根响应字段。
     *
     * @param responseJson   响应 JSON
     * @param responseParams 响应字段
     */
    private void validateRootFields(JsonElement responseJson, List<InterfaceDocParam> responseParams) {
        List<InterfaceDocParam> rootParams = responseParams.stream()
                .filter(param -> param.getParentId() == null || param.getParentId() <= 0)
                .collect(Collectors.toList());
        Map<Long, List<InterfaceDocParam>> childrenMap = responseParams.stream()
                .filter(param -> param.getParentId() != null && param.getParentId() > 0)
                .collect(Collectors.groupingBy(InterfaceDocParam::getParentId));
        if (responseJson.isJsonObject()) {
            validateObjectFields(responseJson.getAsJsonObject(), rootParams, childrenMap);
            return;
        }
        if (responseJson.isJsonArray()) {
            validateArrayElements(responseJson.getAsJsonArray(), rootParams, childrenMap, "响应根节点");
        }
    }

    /**
     * 校验对象字段。
     *
     * @param object      JSON 对象
     * @param params      字段定义
     * @param childrenMap 子字段映射
     */
    private void validateObjectFields(JsonObject object,
                                      List<InterfaceDocParam> params,
                                      Map<Long, List<InterfaceDocParam>> childrenMap) {
        params.forEach(param -> {
            JsonElement value = object.get(param.getName());
            boolean nullable = Objects.equals(param.getNullable(), 1);
            if ((value == null || value.isJsonNull()) && !nullable) {
                throw probeException(PublishProbeFailureStageEnum.RESPONSE_STRUCTURE,
                        "响应字段缺失或为空：" + param.getName());
            }
            if (value == null || value.isJsonNull()) {
                return;
            }
            validateFieldType(param, value, childrenMap.getOrDefault(param.getId(), List.of()), childrenMap);
        });
    }

    /**
     * 校验数组元素字段。
     *
     * @param array       JSON 数组
     * @param params      字段定义
     * @param childrenMap 子字段映射
     * @param fieldPath   字段路径
     */
    private void validateArrayElements(JsonArray array,
                                       List<InterfaceDocParam> params,
                                       Map<Long, List<InterfaceDocParam>> childrenMap,
                                       String fieldPath) {
        if (params.isEmpty()) {
            return;
        }
        array.forEach(element -> {
            if (!element.isJsonObject()) {
                throw probeException(PublishProbeFailureStageEnum.RESPONSE_STRUCTURE,
                        fieldPath + " 数组元素必须是对象");
            }
            validateObjectFields(element.getAsJsonObject(), params, childrenMap);
        });
    }

    /**
     * 校验字段类型和子字段。
     *
     * @param param      字段定义
     * @param value      实际 JSON 值
     * @param childParams 子字段定义
     */
    private void validateFieldType(InterfaceDocParam param,
                                   JsonElement value,
                                   List<InterfaceDocParam> childParams,
                                   Map<Long, List<InterfaceDocParam>> childrenMap) {
        String type = StringUtils.lowerCase(StringUtils.trimToEmpty(param.getType()), Locale.ROOT);
        boolean matched = switch (type) {
            case "string" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
            case "number" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
            case "boolean" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
            case "object" -> value.isJsonObject();
            case "array" -> value.isJsonArray();
            default -> false;
        };
        if (!matched) {
            throw probeException(PublishProbeFailureStageEnum.RESPONSE_STRUCTURE, "响应字段类型不匹配：" + param.getName());
        }
        if ("object".equals(type)) {
            validateObjectFields(value.getAsJsonObject(), childParams, childrenMap);
        }
        if ("array".equals(type) && !childParams.isEmpty()) {
            validateArrayElements(value.getAsJsonArray(), childParams, childrenMap, param.getName());
        }
    }

    /**
     * 判断是否 JSON 媒体类型。
     *
     * @param contentType 内容类型
     * @return 是否 JSON 类型
     */
    private boolean isJsonContentType(String contentType) {
        return StringUtils.lowerCase(StringUtils.defaultString(contentType), Locale.ROOT).contains("json");
    }

    /**
     * 获取 JSON 根类型。
     *
     * @param element JSON 元素
     * @return 根类型
     */
    private String jsonRootType(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "null";
        }
        if (element.isJsonObject()) {
            return "object";
        }
        if (element.isJsonArray()) {
            return "array";
        }
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isString()) {
                return "string";
            }
            if (element.getAsJsonPrimitive().isNumber()) {
                return "number";
            }
            if (element.getAsJsonPrimitive().isBoolean()) {
                return "boolean";
            }
        }
        return "unknown";
    }

    /**
     * 解析网关受控失败阶段。
     *
     * @param gatewayFailureStage 网关阶段文本
     * @return 发布探测失败阶段
     */
    private PublishProbeFailureStageEnum resolveGatewayStage(String gatewayFailureStage) {
        try {
            return PublishProbeFailureStageEnum.valueOf(gatewayFailureStage);
        } catch (IllegalArgumentException exception) {
            return PublishProbeFailureStageEnum.GATEWAY_ROUTE;
        }
    }

    /**
     * 创建探测异常。
     *
     * @param stage  阶段
     * @param reason 原因
     * @return 探测异常
     */
    private InterfacePublishProbeException probeException(PublishProbeFailureStageEnum stage, String reason) {
        return new InterfacePublishProbeException(stage, reason);
    }
}
