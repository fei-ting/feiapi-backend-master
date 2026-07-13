package com.feiting.feiapi.model.enums;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 接口文档参数场景枚举。
 */
public enum InterfaceDocParamSceneEnum {

    /**
     * 请求 Header 参数。
     */
    HEADER("请求 Header", "HEADER"),

    /**
     * URL Query 参数。
     */
    QUERY("请求 Query 参数", "QUERY"),

    /**
     * 请求 Body 参数。
     */
    BODY("请求 Body 参数", "BODY"),

    /**
     * 响应字段。
     */
    RESPONSE("响应字段", "RESPONSE");

    /**
     * 场景说明。
     */
    private final String text;

    /**
     * 场景编码。
     */
    private final String value;

    InterfaceDocParamSceneEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 获取场景编码列表。
     *
     * @return 场景编码列表
     */
    public static List<String> getValues() {
        return Arrays.stream(values()).map(InterfaceDocParamSceneEnum::getValue).collect(Collectors.toList());
    }

    /**
     * 判断场景编码是否合法。
     *
     * @param value 场景编码
     * @return 场景编码是否合法
     */
    public static boolean isValid(String value) {
        return getEnumByValue(value) != null;
    }

    /**
     * 根据场景编码获取枚举。
     *
     * @param value 场景编码
     * @return 参数场景枚举
     */
    public static InterfaceDocParamSceneEnum getEnumByValue(String value) {
        if (value == null) {
            return null;
        }
        String normalizedValue = value.trim();
        return Arrays.stream(values())
                .filter(item -> item.value.equals(normalizedValue))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取场景说明。
     *
     * @return 场景说明
     */
    public String getText() {
        return text;
    }

    /**
     * 获取场景编码。
     *
     * @return 场景编码
     */
    public String getValue() {
        return value;
    }
}
