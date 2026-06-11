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
     * 接口地址
     */
    private String url;

    /**
     * 请求参数
     */
    private String requestParams;

    /**
     * 请求头
     */
    private String requestHeader;

    /**
     * 响应头
     */
    private String responseHeader;

    /**
     * 接口状态（0-关闭，1-开启）
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
