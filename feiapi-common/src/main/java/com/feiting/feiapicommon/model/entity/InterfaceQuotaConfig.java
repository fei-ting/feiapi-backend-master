package com.feiting.feiapicommon.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 接口配额类型配置实体。
 *
 * @TableName interface_quota_config
 */
@TableName(value = "interface_quota_config")
@Data
public class InterfaceQuotaConfig implements Serializable {

    /**
     * 主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 配额类型。
     */
    private String quotaType;

    /**
     * 初始发放额度。
     */
    private Integer initialQuota;

    /**
     * 配置说明。
     */
    private String description;

    /**
     * 创建时间。
     */
    private Date createTime;

    /**
     * 更新时间。
     */
    private Date updateTime;

    /**
     * 是否删除，0-未删除，1-已删除。
     */
    @TableLogic
    private Integer isDelete;

    /**
     * 序列化版本号。
     */
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
