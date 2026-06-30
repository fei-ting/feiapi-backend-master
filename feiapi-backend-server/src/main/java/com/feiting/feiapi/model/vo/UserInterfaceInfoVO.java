package com.feiting.feiapi.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户接口额度视图对象。
 */
@Data
public class UserInterfaceInfoVO implements Serializable {

    /**
     * 用户接口关系主键。
     */
    private Long id;

    /**
     * 调用用户 id。
     */
    private Long userId;

    /**
     * 接口 id。
     */
    private Long interfaceInfoId;

    /**
     * 接口名称。
     */
    private String interfaceName;

    /**
     * 接口路径。
     */
    private String interfacePath;

    /**
     * 请求方法。
     */
    private String method;

    /**
     * 接口状态。
     */
    private Integer interfaceStatus;

    /**
     * 配额类型。
     */
    private String quotaType;

    /**
     * 配额类型说明。
     */
    private String quotaTypeText;

    /**
     * 当前配额类型初始额度。
     */
    private Integer initialQuota;

    /**
     * 总调用次数。
     */
    private Integer totalNum;

    /**
     * 剩余调用次数。
     */
    private Integer leftNum;

    /**
     * 用户接口关系状态。
     */
    private Integer status;

    /**
     * 更新时间。
     */
    private Date updateTime;

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;
}
