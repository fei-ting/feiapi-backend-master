package com.feiting.feiapi.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Optional;

/**
 * 用户角色枚举
 */
public enum UserRoleEnum {

    /**
     * 无角色要求，用于注解默认值
     */
    NONE("", "无角色要求"),

    /**
     * 普通用户角色
     */
    USER("user", "普通用户"),

    /**
     * 管理员角色
     */
    ADMIN("admin", "管理员");

    /**
     * 数据库存储的角色编码
     */
    private final String code;

    /**
     * 角色中文描述
     */
    private final String description;

    /**
     * 构造用户角色枚举
     *
     * @param code        数据库存储的角色编码
     * @param description 角色中文描述
     */
    UserRoleEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据角色编码获取用户角色枚举
     *
     * @param code 数据库存储的角色编码
     * @return 用户角色枚举
     */
    public static Optional<UserRoleEnum> fromCode(String code) {
        if (StringUtils.isBlank(code)) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(role -> role != NONE)
                .filter(role -> role.code.equals(code))
                .findFirst();
    }

    /**
     * 根据角色编码反序列化用户角色枚举
     *
     * @param code 数据库存储的角色编码
     * @return 用户角色枚举，非法角色返回 null 并交由参数校验处理
     */
    @JsonCreator
    public static UserRoleEnum fromJson(String code) {
        return fromCode(code).orElse(null);
    }

    /**
     * 判断角色编码是否合法
     *
     * @param code 数据库存储的角色编码
     * @return 是否合法
     */
    public static boolean isValidCode(String code) {
        return fromCode(code).isPresent();
    }

    /**
     * 获取数据库存储的角色编码
     *
     * @return 数据库存储的角色编码
     */
    @JsonValue
    public String getCode() {
        return code;
    }

    /**
     * 获取角色中文描述
     *
     * @return 角色中文描述
     */
    public String getDescription() {
        return description;
    }
}
