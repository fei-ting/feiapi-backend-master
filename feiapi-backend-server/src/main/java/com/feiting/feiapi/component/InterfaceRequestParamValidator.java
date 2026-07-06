package com.feiting.feiapi.component;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 接口请求参数校验器。
 *
 * <p>根据接口自身的请求参数模板校验在线调试入参，避免把某个接口的字段规则误用到其他接口。</p>
 */
@Component
public class InterfaceRequestParamValidator {

     /**
     * 当前支持的字符串类型标记。
     */
    private static final Set<String> SUPPORTED_TYPE_MARKERS = new HashSet<>(
            Arrays.asList("string", "number", "boolean", "object", "array"));

    /**
     * 校验用户在线调试参数。
     *
     * @param requestParamTemplate 接口请求参数模板
     * @param userRequestParams    用户请求参数
     */
    public void validate(String requestParamTemplate, String userRequestParams) {
        JsonElement userParamElement = parseUserRequestParams(userRequestParams);
        JsonObject templateObject = parseTemplateObject(requestParamTemplate);
        if (templateObject == null) {
            return;
        }
        JsonObject userParamObject = toUserParamObject(userParamElement);
        templateObject.entrySet().stream()
                .forEach(entry -> validateRequiredField(userParamObject, entry.getKey(), entry.getValue()));
    }

    /**
     * 解析用户请求参数，确保非空内容必须是合法 JSON。
     *
     * @param userRequestParams 用户请求参数
     * @return 用户参数 JSON 元素
     */
    private JsonElement parseUserRequestParams(String userRequestParams) {
        if (StringUtils.isBlank(userRequestParams)) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(userRequestParams);
        } catch (JsonSyntaxException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数必须是合法 JSON");
        }
    }

    /**
     * 解析接口请求参数模板。
     *
     * @param requestParamTemplate 接口请求参数模板
     * @return 模板 JSON 对象，非标准 JSON 对象时返回 null 以兼容旧数据
     */
    private JsonObject parseTemplateObject(String requestParamTemplate) {
        if (StringUtils.isBlank(requestParamTemplate)) {
            return null;
        }
        try {
            JsonElement templateElement = JsonParser.parseString(requestParamTemplate);
            if (!templateElement.isJsonObject()) {
                return null;
            }
            return templateElement.getAsJsonObject();
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    /**
     * 将用户请求参数转换为 JSON 对象。
     *
     * @param userParamElement 用户参数 JSON 元素
     * @return 用户参数 JSON 对象
     */
    private JsonObject toUserParamObject(JsonElement userParamElement) {
        if (userParamElement == null || !userParamElement.isJsonObject()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数必须是 JSON 对象");
        }
        return userParamElement.getAsJsonObject();
    }

    /**
     * 校验模板声明的必填字段。
     *
     * @param userParamObject 用户参数对象
     * @param fieldName       字段名
     * @param templateValue   模板字段值
     */
    private void validateRequiredField(JsonObject userParamObject, String fieldName, JsonElement templateValue) {
        JsonElement actualValue = userParamObject.get(fieldName);
        if (actualValue == null || actualValue.isJsonNull()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数缺少必填字段：" + fieldName);
        }
        validateFieldType(fieldName, templateValue, actualValue);
    }

    /**
     * 校验字段类型。
     *
     * @param fieldName     字段名
     * @param templateValue 模板字段值
     * @param actualValue   实际字段值
     */
    private void validateFieldType(String fieldName, JsonElement templateValue, JsonElement actualValue) {
        String expectedType = resolveExpectedType(templateValue);
        if (expectedType == null) {
            return;
        }
        if (!matchesType(expectedType, actualValue)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "请求参数字段类型错误：" + fieldName + " 应为 " + expectedType);
        }
        if ("string".equals(expectedType) && StringUtils.isBlank(actualValue.getAsString())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数字段不能为空：" + fieldName);
        }
    }

    /**
     * 根据模板字段值解析期望类型。
     *
     * @param templateValue 模板字段值
     * @return 期望类型
     */
    private String resolveExpectedType(JsonElement templateValue) {
        if (templateValue == null || templateValue.isJsonNull()) {
            return null;
        }
        if (templateValue.isJsonObject()) {
            return "object";
        }
        if (templateValue.isJsonArray()) {
            return "array";
        }
        if (!templateValue.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = templateValue.getAsJsonPrimitive();
        if (primitive.isString()) {
            String marker = primitive.getAsString().trim().toLowerCase(Locale.ROOT);
            return SUPPORTED_TYPE_MARKERS.contains(marker) ? marker : "string";
        }
        if (primitive.isNumber()) {
            return "number";
        }
        if (primitive.isBoolean()) {
            return "boolean";
        }
        return null;
    }

    /**
     * 判断实际字段值是否匹配期望类型。
     *
     * @param expectedType 期望类型
     * @param actualValue  实际字段值
     * @return 是否匹配
     */
    private boolean matchesType(String expectedType, JsonElement actualValue) {
        if (actualValue == null || actualValue.isJsonNull()) {
            return false;
        }
        switch (expectedType) {
            case "string":
                return actualValue.isJsonPrimitive() && actualValue.getAsJsonPrimitive().isString();
            case "number":
                return actualValue.isJsonPrimitive() && actualValue.getAsJsonPrimitive().isNumber();
            case "boolean":
                return actualValue.isJsonPrimitive() && actualValue.getAsJsonPrimitive().isBoolean();
            case "object":
                return actualValue.isJsonObject();
            case "array":
                return actualValue.isJsonArray();
            default:
                return true;
        }
    }
}
