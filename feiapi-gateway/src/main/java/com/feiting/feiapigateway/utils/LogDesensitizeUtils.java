package com.feiting.feiapigateway.utils;

import org.springframework.util.MultiValueMap;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 网关日志脱敏工具类
 */
public final class LogDesensitizeUtils {

    private static final String MASK = "******";

    private static final Set<String> SENSITIVE_FIELD_NAMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "password",
            "userpassword",
            "checkpassword",
            "oldpassword",
            "newpassword",
            "secretkey",
            "accesskey",
            "sign",
            "token",
            "authorization",
            "authorizationtoken",
            "refreshtoken"
    )));

    private LogDesensitizeUtils() {
    }

    /**
     * 脱敏 query 参数。
     */
    public static Map<String, Object> toSafeQueryParams(MultiValueMap<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        queryParams.forEach((key, values) -> {
            if (isSensitiveField(key)) {
                result.put(key, MASK);
            } else {
                result.put(key, safeValues(values));
            }
        });
        return result;
    }

    private static Object safeValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values;
    }

    private static boolean isSensitiveField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String normalizedFieldName = fieldName.toLowerCase(Locale.ROOT);
        if (SENSITIVE_FIELD_NAMES.contains(normalizedFieldName)) {
            return true;
        }
        return normalizedFieldName.contains("password")
                || normalizedFieldName.contains("secret")
                || normalizedFieldName.contains("token")
                || normalizedFieldName.contains("sign")
                || normalizedFieldName.contains("authorization")
                || normalizedFieldName.contains("accesskey");
    }
}
