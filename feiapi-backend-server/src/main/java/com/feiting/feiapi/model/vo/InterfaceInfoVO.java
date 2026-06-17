package com.feiting.feiapi.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 接口信息封装视图
 */
@Data
public class InterfaceInfoVO implements Serializable {

    /**
     * 主键
     */
    private Long id;

    /**
     * 接口名称
     */
    private String name;

    /**
     * 接口描述
     */
    private String description;

    /**
     * 接口展示地址
     */
    private String url;

    /**
     * 接口路径
     */
    private String path;

    /**
     * 真实后端服务地址
     */
    private String targetHost;

    /**
     * 请求参数
     */
    private String requestParams;

    /**
     * 请求头文档，不参与网关鉴权和路由
     */
    private String requestHeader;

    /**
     * 响应头文档，不参与网关运行时逻辑
     */
    private String responseHeader;

    /**
     * 接口状态（0-下线，1-上线，2-发布验证中）
     */
    private Integer status;

    /**
     * 请求方法
     */
    private String method;

    /**
     * 创建人 id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 调用次数
     */
    private Integer totalNum;

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;
}
