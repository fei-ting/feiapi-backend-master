package com.feiting.feiapi.model.enums;

/**
 * 接口变更类型枚举。
 */
public enum InterfaceChangeTypeEnum {

    /** 新增接口。 */
    CREATED("created"),

    /** 修改接口。 */
    UPDATED("updated"),

    /** 接口上线。 */
    ONLINE("online"),

    /** 接口下线。 */
    OFFLINE("offline");

    /** 前端展示编码。 */
    private final String code;

    InterfaceChangeTypeEnum(String code) {
        this.code = code;
    }

    /**
     * 获取前端展示编码。
     *
     * @return 展示编码
     */
    public String getCode() {
        return code;
    }
}
