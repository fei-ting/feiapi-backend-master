package com.feiting.feiapi.model.dto.interfaceInfo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 创建请求
 */
@Data
public class InterfaceInfoAddRequest implements Serializable {

    /**
     * 名称
     */
    @NotBlank(message = "接口名称不能为空")
    @Size(max = 50, message = "接口名称长度不能超过 50")
    private String name;

    /**
     * 描述
     */
    @Size(max = 512, message = "接口描述长度不能超过 512")
    private String description;

    /**
     * 接口展示地址
     */
    @Size(max = 512, message = "接口地址长度不能超过 512")
    private String url;

    /**
     * 接口路径
     */
    @NotBlank(message = "接口路径不能为空")
    @Size(max = 512, message = "接口路径长度不能超过 512")
    private String path;

    /**
     * 真实后端服务地址
     */
    @NotBlank(message = "真实后端服务地址不能为空")
    @Size(max = 512, message = "真实后端服务地址长度不能超过 512")
    private String targetHost;

    /**
     * 请求参数
     */
    @Size(max = 65535, message = "请求参数长度不能超过 65535")
    private String requestParams;

    /**
     * 请求头文档，不参与网关鉴权和路由
     */
    @Size(max = 65535, message = "请求头长度不能超过 65535")
    private String requestHeader;

    /**
     * 响应头文档，不参与网关运行时逻辑
     */
    @Size(max = 65535, message = "响应头长度不能超过 65535")
    private String responseHeader;


    /**
     * 请求方法
     */
    @NotBlank(message = "请求类型不能为空")
    @Size(max = 16, message = "请求类型长度不能超过 16")
    private String method;


    private static final long serialVersionUID = 1L;
}
