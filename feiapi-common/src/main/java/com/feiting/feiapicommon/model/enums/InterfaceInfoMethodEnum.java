package com.feiting.feiapicommon.model.enums;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 接口请求方法枚举
 */
public enum InterfaceInfoMethodEnum {

    /**
     * GET 请求
     */
    GET("GET"),

    /**
     * POST 请求
     */
    POST("POST"),

    /**
     * PUT 请求
     */
    PUT("PUT"),

    /**
     * DELETE 请求
     */
    DELETE("DELETE"),

    /**
     * PATCH 请求
     */
    PATCH("PATCH");

    /**
     * 请求方法值
     */
    private final String value;

    InterfaceInfoMethodEnum(String value) {
        this.value = value;
    }

    /**
     * 获取请求方法值列表
     *
     * @return 请求方法值列表
     */
    public static List<String> getValues() {
        return Arrays.stream(values()).map(InterfaceInfoMethodEnum::getValue).collect(Collectors.toList());
    }

    /**
     * 判断请求方法是否合法
     *
     * @param method 请求方法
     * @return 请求方法是否合法
     */
    public static boolean isValid(String method) {
        if (method == null) {
            return false;
        }
        String normalizedMethod = method.trim().toUpperCase();
        return Arrays.stream(values()).anyMatch(item -> item.value.equals(normalizedMethod));
    }

    /**
     * 标准化请求方法
     *
     * @param method 请求方法
     * @return 标准化后的请求方法
     */
    public static String normalize(String method) {
        if (!isValid(method)) {
            return method;
        }
        return method.trim().toUpperCase();
    }

    public String getValue() {
        return value;
    }
}
