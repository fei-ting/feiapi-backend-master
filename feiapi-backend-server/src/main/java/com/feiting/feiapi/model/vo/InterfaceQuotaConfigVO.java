package com.feiting.feiapi.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 接口配额类型配置视图对象。
 */
@Data
public class InterfaceQuotaConfigVO implements Serializable {

    /**
     * 主键。
     */
    private Long id;

    /**
     * 配额类型。
     */
    private String quotaType;

    /**
     * 配额类型说明。
     */
    private String quotaTypeText;

    /**
     * 初始发放额度。
     */
    private Integer initialQuota;

    /**
     * 配置说明。
     */
    private String description;

    /**
     * 是否为有限额度类型。
     */
    private Boolean limited;

    /**
     * 创建时间。
     */
    private Date createTime;

    /**
     * 更新时间。
     */
    private Date updateTime;

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;
}
