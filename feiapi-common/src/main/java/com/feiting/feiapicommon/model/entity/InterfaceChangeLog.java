package com.feiting.feiapicommon.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 接口变更审计日志实体。
 */
@Data
@TableName("interface_change_log")
public class InterfaceChangeLog implements Serializable {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接口信息 ID。 */
    private Long interfaceInfoId;

    /** 接口名称快照。 */
    private String interfaceName;

    /** 变更类型编码。 */
    private String changeType;

    /** 事件发生时间。 */
    private Date eventTime;

    /** 记录创建时间。 */
    private Date createTime;

    /** 序列化版本号。 */
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
