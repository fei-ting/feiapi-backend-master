package com.feiting.feiapi.model.dto.interfaceInfo;


import com.feiting.feiapi.common.PageRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 查询请求
 *
 * @author yupi
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class InterfaceInfoQueryRequest extends PageRequest implements Serializable {

    /**
     * 主键
     */
    @Positive(message = "接口 id 必须大于 0")
    private Long id;

    /**
     * 名称
     */
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
    @Size(max = 512, message = "接口路径长度不能超过 512")
    private String path;

    /**
     * 真实后端服务地址
     */
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
     * 接口状态 0-下线 1-上线 2-发布验证中
     */
    @Min(value = 0, message = "接口状态不能小于 0")
    @Max(value = 2, message = "接口状态不能大于 2")
    private Integer status;

    /**
     * 请求方法
     */
    @Size(max = 16, message = "请求类型长度不能超过 16")
    private String method;

    /**
     * 创建人
     */
    @Positive(message = "创建人 id 必须大于 0")
    private Long userId;


    private static final long serialVersionUID = 1L;
}
