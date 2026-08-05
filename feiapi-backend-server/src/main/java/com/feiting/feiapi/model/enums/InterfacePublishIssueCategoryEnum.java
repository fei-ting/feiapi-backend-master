package com.feiting.feiapi.model.enums;

/**
 * 接口发布前检查问题分类枚举。
 */
public enum InterfacePublishIssueCategoryEnum {

    /**
     * 接口运行时配置问题。
     */
    INTERFACE_CONFIG,

    /**
     * SDK 方法和探测契约问题。
     */
    SDK,

    /**
     * 运行时请求参数模板问题。
     */
    RUNTIME_TEMPLATE,

    /**
     * 结构化接口文档问题。
     */
    DOCUMENT,

    /**
     * Java SDK 和 curl 调用示例问题。
     */
    CALL_EXAMPLE
}
