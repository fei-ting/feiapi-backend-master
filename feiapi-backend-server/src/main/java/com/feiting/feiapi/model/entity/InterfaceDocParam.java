package com.feiting.feiapi.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 接口文档参数实体。
 *
 * <p>统一保存请求 Header、Query 参数、Body 参数和响应字段说明。</p>
 */
@TableName(value = "interface_doc_param")
@Data
public class InterfaceDocParam implements Serializable {

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
     * 参数场景，取值包括 QUERY、BODY、RESPONSE。
     */
    private String paramScene;

    /**
     * 父级参数 ID，用于描述响应字段或请求体字段的嵌套结构。
     */
    private Long parentId;

    /**
     * 参数名称。
     */
    private String name;

    /**
     * 参数类型。
     */
    private String type;

    /**
     * 是否必填，0 表示否，1 表示是。
     */
    private Integer required;

    /**
     * 是否允许为空，0 表示否，1 表示是。
     */
    private Integer nullable;

    /**
     * 默认值。
     */
    private String defaultValue;

    /**
     * 示例值。
     */
    private String exampleValue;

    /**
     * 参数说明。
     */
    private String description;

    /**
     * 校验规则展示说明。
     */
    private String validationRule;

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
     * 逻辑删除标识，0 表示未删除，其他值表示已删除记录 ID。
     */
    @TableLogic(value = "0", delval = "id")
    private Long isDelete;
}
