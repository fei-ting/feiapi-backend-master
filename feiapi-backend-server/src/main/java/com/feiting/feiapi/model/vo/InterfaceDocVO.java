package com.feiting.feiapi.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 接口文档主信息视图。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InterfaceDocVO implements Serializable {

    /**
     * 主键。
     */
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;
}
