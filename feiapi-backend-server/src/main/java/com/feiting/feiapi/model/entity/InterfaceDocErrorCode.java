package com.feiting.feiapi.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 接口文档错误码实体。
 *
 * <p>用于保存单个接口对外公开的错误码、错误说明和解决建议。</p>
 */
@TableName(value = "interface_doc_error_code")
@Data
public class InterfaceDocErrorCode implements Serializable {

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
     * 错误码。
     */
    private String errorCode;

    /**
     * 错误信息。
     */
    private String errorMessage;

    /**
     * 错误说明。
     */
    private String description;

    /**
     * 解决建议。
     */
    private String solution;

    /**
     * 排序值，数值越小越靠前。
     */
    private Integer sortOrder;

    /**
     * 创建时间。
     */
    private Date createTime;

    /**
     * 更新时间。
     */
    private Date updateTime;

    /**
     * 是否删除，0 表示未删除，1 表示已删除。
     */
    @TableLogic
    private Integer isDelete;

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;
}
