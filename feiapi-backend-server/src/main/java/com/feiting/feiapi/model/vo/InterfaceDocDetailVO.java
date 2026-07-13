package com.feiting.feiapi.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 接口文档聚合详情视图。
 */
@Data
public class InterfaceDocDetailVO implements Serializable {

    /**
     * 接口基础信息。
     */
    private InterfaceInfoVO interfaceInfo;

    /**
     * 文档主信息。
     */
    private InterfaceDocVO doc;

    /**
     * 网关调用地址。
     */
    private String gatewayUrl;

    /**
     * 是否缺少结构化文档。
     */
    private Boolean legacyFallback;

    /**
     * 请求 Header 参数列表。
     */
    private List<InterfaceDocParamVO> requestHeaders;

    /**
     * 请求参数列表。
     */
    private List<InterfaceDocParamVO> requestParams;

    /**
     * 响应参数列表。
     */
    private List<InterfaceDocParamVO> responseParams;

    /**
     * 错误码列表。
     */
    private List<InterfaceDocErrorCodeVO> errorCodes;

    /**
     * curl 调用示例。
     */
    private String curlExample;

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;
}
