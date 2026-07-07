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
 * 接口调用日志实体。
 *
 * @TableName interface_invoke_log
 */
@TableName(value = "interface_invoke_log")
@Data
public class InterfaceInvokeLog implements Serializable {

    /**
     * 主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 调用用户 ID。
     */
    private Long userId;

    /**
     * 被调用接口 ID。
     */
    private Long interfaceInfoId;

    /**
     * 接口请求路径。
     */
    private String path;

    /**
     * HTTP 请求方法。
     */
    private String method;

    /**
     * 下游响应状态码。
     */
    private Integer statusCode;

    /**
     * 是否调用成功。
     */
    private Integer success;

    /**
     * 响应耗时，单位毫秒。
     */
    private Long responseTimeMs;

    /**
     * 调用发生时间。
     */
    private Date invokeTime;

    /**
     * 创建时间。
     */
    private Date createTime;

    /**
     * 是否删除（0-未删除，1-已删除）。
     */
    @TableLogic
    private Integer isDelete;

    /**
     * 序列化版本号。
     */
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
