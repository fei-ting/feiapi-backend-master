package com.feiting.feiapi.interfaceplatform.definition.model.snapshot;

import lombok.Builder;
import lombok.Value;

import java.util.Date;

/**
 * 接口定义只读快照。
 *
 * <p>该模型只承载跨域读取需要的接口运行配置，不携带实体回写或持久化能力。</p>
 */
@Value
@Builder
public class InterfaceDefinitionSnapshot {

    /**
     * 接口信息 ID。
     */
    Long interfaceInfoId;

    /**
     * 接口名称。
     */
    String name;

    /**
     * SDK 方法名。
     */
    String sdkMethodName;

    /**
     * 接口描述。
     */
    String description;

    /**
     * 接口展示地址。
     */
    String url;

    /**
     * 网关路由路径。
     */
    String path;

    /**
     * 真实后端服务地址。
     */
    String targetHost;

    /**
     * 运行时请求参数模板。
     */
    String requestParams;

    /**
     * 请求头文档。
     */
    String requestHeader;

    /**
     * 响应头文档。
     */
    String responseHeader;

    /**
     * 接口状态。
     */
    Integer status;

    /**
     * 请求方法。
     */
    String method;

    /**
     * 接口配额类型。
     */
    String quotaType;

    /**
     * 创建人 ID。
     */
    Long userId;

    /**
     * 创建时间。
     */
    Date createTime;

    /**
     * 更新时间。
     */
    Date updateTime;
}
