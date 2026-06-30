package com.feiting.feiapicommon.model.enums;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 接口配额类型枚举。
 */
public enum InterfaceQuotaTypeEnum {

    /**
     * 免费无限接口，不扣减剩余额度。
     */
    FREE_UNLIMITED("免费无限接口", "FREE_UNLIMITED", 0, false),

    /**
     * 基础额度接口，默认初始额度为 100 次。
     */
    BASIC_QUOTA("基础额度接口", "BASIC_QUOTA", 100, true),

    /**
     * 高级体验接口，默认初始额度为 3 次。
     */
    ADVANCED_TRIAL("高级体验接口", "ADVANCED_TRIAL", 3, true);

    /**
     * 类型说明。
     */
    private final String text;

    /**
     * 类型编码。
     */
    private final String value;

    /**
     * 默认初始额度。
     */
    private final int defaultInitialQuota;

    /**
     * 是否为有限额度类型。
     */
    private final boolean limited;

    InterfaceQuotaTypeEnum(String text, String value, int defaultInitialQuota, boolean limited) {
        this.text = text;
        this.value = value;
        this.defaultInitialQuota = defaultInitialQuota;
        this.limited = limited;
    }

    /**
     * 获取类型编码列表。
     *
     * @return 类型编码列表
     */
    public static List<String> getValues() {
        return Arrays.stream(values()).map(InterfaceQuotaTypeEnum::getValue).collect(Collectors.toList());
    }

    /**
     * 判断配额类型是否合法。
     *
     * @param value 类型编码
     * @return 配额类型是否合法
     */
    public static boolean isValid(String value) {
        return getEnumByValue(value) != null;
    }

    /**
     * 根据类型编码获取枚举。
     *
     * @param value 类型编码
     * @return 配额类型枚举
     */
    public static InterfaceQuotaTypeEnum getEnumByValue(String value) {
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
     * 获取类型说明。
     *
     * @return 类型说明
     */
    public String getText() {
        return text;
    }

    /**
     * 获取类型编码。
     *
     * @return 类型编码
     */
    public String getValue() {
        return value;
    }

    /**
     * 获取默认初始额度。
     *
     * @return 默认初始额度
     */
    public int getDefaultInitialQuota() {
        return defaultInitialQuota;
    }

    /**
     * 判断是否为有限额度类型。
     *
     * @return 是否为有限额度类型
     */
    public boolean isLimited() {
        return limited;
    }
}
