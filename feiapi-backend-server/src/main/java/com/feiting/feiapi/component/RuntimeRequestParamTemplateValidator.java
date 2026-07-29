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

/**
 * 运行时请求参数模板校验器。
 *
 * <p>运行时模板是请求参数名称的唯一权威来源。本组件只校验模板结构和参数名边界，
 * 不修改模板文本或参数名称。</p>
 */
@Component
public class RuntimeRequestParamTemplateValidator {

    /**
     * 校验运行时请求参数模板。
     *
     * @param requestParams 运行时请求参数模板 JSON 文本
     */
    public void validate(String requestParams) {
        if (StringUtils.isBlank(requestParams)) {
            return;
        }
        JsonObject requestParamObject = parseRequestParamObject(requestParams);
        requestParamObject.keySet().forEach(this::validateParamName);
    }

    /**
     * 解析请求参数模板为 JSON 对象。
     *
     * @param requestParams 运行时请求参数模板 JSON 文本
     * @return 请求参数 JSON 对象
     */
    private JsonObject parseRequestParamObject(String requestParams) {
        try {
            JsonElement jsonElement = JsonParser.parseString(requestParams);
            if (!jsonElement.isJsonObject()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数模板必须是 JSON 对象");
            }
            return jsonElement.getAsJsonObject();
        } catch (JsonSyntaxException exception) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数模板必须是合法 JSON");
        }
    }

    /**
     * 校验运行时请求参数名称。
     *
     * <p>直接检查原始名称首尾字符，绝不使用裁剪结果替换、保存或匹配名称。</p>
     *
     * @param name 模板中的原始参数名称
     */
    private void validateParamName(String name) {
        if (name == null || name.isEmpty() || name.codePoints().allMatch(this::isWhitespace)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数名称不能为空");
        }
        int firstCodePoint = name.codePointAt(0);
        int lastCodePoint = name.codePointBefore(name.length());
        if (isWhitespace(firstCodePoint) || isWhitespace(lastCodePoint)) {
            String escapedName = new JsonPrimitive(name).toString();
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数名称不能包含首尾空白：" + escapedName);
        }
    }

    /**
     * 判断 Unicode 码点是否为空白或空格字符。
     *
     * @param codePoint Unicode 码点
     * @return 是否为空白或空格字符
     */
    private boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }
}
