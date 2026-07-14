package com.feiting.feiapi.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 接口文档主信息实体。
 *
 * <p>用于保存接口级文档说明、请求/响应格式和示例内容。</p>
 */
@TableName(value = "interface_doc")
@Data
public class InterfaceDoc implements Serializable {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的接口信息 ID。
     */
    private Long interfaceInfoId;

    /**
     * 文档版本号。
     */
    private String docVersion;

    /**
     * 请求内容类型。
     */
    private String requestContentType;

    /**
     * 响应内容类型。
     */
    private String responseContentType;

    /**
     * 鉴权说明。
     */
    private String authDescription;

    /**
     * 成功响应 JSON 示例。
     */
    private String successExample;

    /**
     * 失败响应 JSON 示例。
     */
    private String failExample;

    /**
     * 文档备注。
     */
    private String remark;

    /**
     * 创建时间。
     */
    private Date createTime;

    /**
     * 更新时间。
     */
    private Date updateTime;

    /**
     * 逻辑删除标识，0 表示未删除，其他值表示已删除记录 ID。
     */
    @TableLogic(value = "0", delval = "id")
    private Long isDelete;
}
