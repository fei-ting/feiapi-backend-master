package com.feiting.feiapi.utils;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 排序字段工具类。
 * 负责校验前端传入的驼峰字段名，并转换为数据库下划线字段名。
 */
public final class SortFieldUtils {

    private SortFieldUtils() {
    }

    /**
     * 构建允许排序字段白名单。
     */
    public static Set<String> allowedFields(String... fields) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(fields)));
    }

    /**
     * 将前端传入的驼峰字段转换为数据库下划线字段。
     * 若字段为空或不在白名单中，则返回 null，避免非法字段参与排序。
     */
    public static String resolveSortField(String sortField, Set<String> allowedFields) {
        if (StringUtils.isBlank(sortField) || allowedFields == null || !allowedFields.contains(sortField)) {
            return null;
        }
        return camelToSnake(sortField);
    }

    /**
     * 驼峰命名转下划线命名。
     */
    public static String camelToSnake(String fieldName) {
        if (StringUtils.isBlank(fieldName)) {
            return fieldName;
        }
        StringBuilder builder = new StringBuilder(fieldName.length() + 8);
        for (int i = 0; i < fieldName.length(); i++) {
            char currentChar = fieldName.charAt(i);
            if (Character.isUpperCase(currentChar)) {
                builder.append('_').append(Character.toLowerCase(currentChar));
            } else {
                builder.append(currentChar);
            }
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }
}
