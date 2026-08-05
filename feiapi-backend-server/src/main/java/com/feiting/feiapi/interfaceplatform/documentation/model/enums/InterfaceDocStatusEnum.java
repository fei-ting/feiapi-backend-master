package com.feiting.feiapi.interfaceplatform.documentation.model.enums;

import java.util.Arrays;

/**
 * 接口文档维护状态枚举。
 */
public enum InterfaceDocStatusEnum {

    /**
     * 草稿，表示文档仍待管理员确认完善。
     */
    DRAFT("DRAFT"),

    /**
     * 已完成，表示文档已经通过完整性校验并由管理员确认。
     */
    READY("READY");

    /**
     * 持久化状态值。
     */
    private final String value;

    /**
     * 创建文档状态枚举。
     *
     * @param value 持久化状态值
     */
    InterfaceDocStatusEnum(String value) {
        this.value = value;
    }

    /**
     * 获取持久化状态值。
     *
     * @return 持久化状态值
     */
    public String getValue() {
        return value;
    }

    /**
     * 根据持久化值解析文档状态。
     *
     * @param value 持久化状态值
     * @return 匹配的状态，不匹配时返回空
     */
    public static InterfaceDocStatusEnum getEnumByValue(String value) {
        return Arrays.stream(values())
                .filter(status -> status.value.equals(value))
                .findFirst()
                .orElse(null);
    }
}
