package com.feiting.feiapicommon.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户角色变更审计日志
 *
 * @TableName user_role_change_log
 */
@TableName(value = "user_role_change_log")
@Data
public class UserRoleChangeLog implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 操作者 id
     */
    private Long operatorId;

    /**
     * 目标用户 id
     */
    private Long targetUserId;

    /**
     * 旧角色
     */
    private String oldRole;

    /**
     * 新角色
     */
    private String newRole;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private Date createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
