package com.feiting.feiapicommon.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 接口信息实体
 *
 * @TableName interface_info
 */
@TableName(value ="interface_info")
@Data
public class InterfaceInfo implements Serializable {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
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
     * 接口展示地址，主要用于前端展示和兼容旧数据
     */
    private String url;

    /**
     * 接口路径，用于网关路由和接口唯一身份匹配
     */
    private String path;

    /**
     * 真实后端服务地址，用于描述接口实际转发目标
     */
    private String targetHost;

    /**
     * 请求参数
     * [
     *   {"name": "username", "type": "string"}
     * ]
     */
    private String requestParams;

    /**
     * 请求头文档，描述调用方需要传递的 Header，不参与网关鉴权和路由
     */
    private String requestHeader;

    /**
     * 响应头文档，描述接口响应 Header，不参与网关运行时逻辑
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
     * 创建人
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
     * 是否删除(0-未删, 1-已删)
     */
    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
